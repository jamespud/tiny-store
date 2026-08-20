package com.github.spud.tinystore.inventory.infrastructure.util;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 库存Redis操作管理器（封装预扣、回滚、CAS更新、超时清理、对账等原子操作）
 * 核心特性：
 * 1. 所有操作通过Lua脚本保证原子性
 * 2. 支持幂等、超时自动清理、数据对账
 * 3. 适配高并发秒杀场景
 */
@Slf4j
@Component
public class InventoryRedisManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ========================== 常量定义 ==========================
    /**
     * Redis Key前缀
     */
    private static final String KEY_PREFIX_TOTAL = "inventory:total:%s";
    private static final String KEY_PREFIX_DEDUCTED = "inventory:deducted:%s";
    private static final String KEY_PREFIX_UNCOMMIT = "inventory:uncommit:%s";
    private static final String KEY_PREFIX_VERSION = "inventory:total_version:%s";
    private static final String KEY_PREFIX_ROLLBACK_ERROR = "inventory:rollback:error";
    private static final String KEY_PREFIX_CHECK_LOG = "inventory:check:log";

    // V2 key前缀（加入 shopId 隔离，与 DB unique(shop_id, sku_id) 对齐）
    private static final String KEY_V2_TOTAL = "inventory:total:%s:%s";       // shopId:skuId
    private static final String KEY_V2_DEDUCTED = "inventory:deducted:%s:%s";
    private static final String KEY_V2_UNCOMMIT = "inventory:uncommit:%s:%s";
    private static final String KEY_V2_VERSION = "inventory:version:%s:%s";

    /**
     * 默认超时时间：30分钟（毫秒）
     */
    private static final long DEFAULT_TIMEOUT_MS = 1800000L;

    /**
     * 返回值枚举（避免魔法值）
     */
    public enum ResultCode {
        SUCCESS(1, "操作成功"),
        FAIL(-1, "操作失败"),
        PARAM_ERROR(-2, "参数错误"),
        INVENTORY_NOT_ENOUGH(-3, "库存不足"),
        RECORD_NOT_EXIST(0, "记录不存在/版本不匹配");

        private final int code;
        private final String desc;

        ResultCode(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static ResultCode getByCode(int code) {
            for (ResultCode resultCode : ResultCode.values()) {
                if (resultCode.code == code) {
                    return resultCode;
                }
            }
            return FAIL;
        }
    }

    // ========================== Lua脚本定义 ==========================
    /**
     * 脚本1：库存预扣（带唯一bizId）
     */
    private static final String PRE_DEDUCT_SCRIPT = """
            local total_key = KEYS[1]
            local deducted_key = KEYS[2]
            local uncommit_zset_key = KEYS[3]
            local version_key = KEYS[4]

            local amount = tonumber(ARGV[1]) or 0
            local biz_id = ARGV[2] or ""
            local total = tonumber(redis.call('get', total_key)) or 0
            local deducted = tonumber(redis.call('get', deducted_key)) or 0

            if biz_id == "" then
                return -2
            end

            if (deducted + amount) > total then
                return -1
            end

            redis.call('set', deducted_key, deducted + amount)
            -- 毫秒取整（浮点长尾会让 reservation_id 超 varchar(64)）
            local timestamp = redis.call('time')[1] * 1000 + math.floor(redis.call('time')[2] / 1000)
            local member = biz_id .. '_' .. timestamp .. '_' .. amount
            redis.call('zadd', uncommit_zset_key, timestamp, member)
            redis.call('incr', version_key)

            return member
            """;

    /**
     * 脚本2：库存预扣回滚（根据预扣member）
     */
    private static final String ROLLBACK_PRE_DEDUCT_SCRIPT = """
            local deducted_key = KEYS[1]
            local uncommit_zset_key = KEYS[2]
            local version_key = KEYS[3]
            local conflict_hash_key = KEYS[4]

            local member = ARGV[1] or ""
            if member == "" then
                return -1
            end

            local exists = redis.call('zscore', uncommit_zset_key, member)
            if not exists then
                return 0
            end

            local parts = {}
            for part in string.gmatch(member, '[^_]+') do
                table.insert(parts, part)
            end
            if #parts ~= 3 then
                return 0
            end
            local amount = tonumber(parts[3]) or 0
            if amount <= 0 then
                return 0
            end

            local current_deducted = tonumber(redis.call('get', deducted_key)) or 0
            if current_deducted < amount then
                redis.call('hincrby', conflict_hash_key, deducted_key .. '_' .. member, 1)
                return 0
            end
            redis.call('set', deducted_key, current_deducted - amount)
            redis.call('zrem', uncommit_zset_key, member)
            redis.call('incr', version_key)

            return 1
            """;

    /**
     * 脚本3：CAS更新总库存（版本号校验）
     */
    private static final String CAS_UPDATE_TOTAL_SCRIPT = """
            local total_key = KEYS[1]
            local version_key = KEYS[2]
            
            local expect_version = tonumber(ARGV[1]) or 0
            local added = tonumber(ARGV[2]) or 0
            local current_version = tonumber(redis.call('get', version_key)) or 0
            local current_total = tonumber(redis.call('get', total_key)) or 0
            
            if current_version ~= expect_version then
                return 0
            end
            
            redis.call('set', total_key, current_total + added)
            redis.call('set', version_key, current_version + 1)
            
            return 1
            """;

    /**
     * 脚本4：清理超时未提交的预扣记录
     */
    private static final String CLEAN_TIMEOUT_UNCOMMIT_SCRIPT = """
            local deducted_key = KEYS[1]
            local uncommit_zset_key = KEYS[2]
            local version_key = KEYS[3]
            local timeout = tonumber(ARGV[1]) or 1800000

            local current_ts = redis.call('time')[1] * 1000 + redis.call('time')[2] / 1000
            local expire_ts = current_ts - timeout

            local timeout_members = redis.call('zrangebyscore', uncommit_zset_key, 0, expire_ts)
            local clean_count = #timeout_members

            if clean_count > 0 then
                local total_rollback = 0
                for _, member in ipairs(timeout_members) do
                    local parts = {}
                    for part in string.gmatch(member, '[^_]+') do
                        table.insert(parts, part)
                    end
                    if #parts == 3 then
                        local amount = tonumber(parts[3]) or 0
                        total_rollback = total_rollback + amount
                    end
                end
                local current_deducted = tonumber(redis.call('get', deducted_key)) or 0
                if current_deducted >= total_rollback then
                    redis.call('set', deducted_key, current_deducted - total_rollback)
                end
                redis.call('zremrangebyscore', uncommit_zset_key, 0, expire_ts)
                redis.call('incr', version_key)
            end

            return clean_count
            """;

    /**
     * 脚本5：库存数据对账（修正deducted与uncommit不一致）
     */
    private static final String CHECK_CONSISTENCY_SCRIPT = """
            local deducted_key = KEYS[1]
            local uncommit_zset_key = KEYS[2]
            
            local deducted = tonumber(redis.call('get', deducted_key)) or 0
            local uncommit_total = 0
            local members = redis.call('zrange', uncommit_zset_key, 0, -1)
            for _, member in ipairs(members) do
                local parts = {}
                for part in string.gmatch(member, '[^_]+') do
                    table.insert(parts, part)
                end
                if #parts == 3 then
                    local amount = tonumber(parts[3]) or 0
                    uncommit_total = uncommit_total + amount
                end
            end
            
            local diff = deducted - uncommit_total
            if diff ~= 0 then
                redis.call('set', deducted_key, uncommit_total)
                redis.call('hset', KEYS[3], KEYS[1] .. '_' .. redis.call('time')[1], 
                    'deducted=' .. deducted .. ', uncommit_total=' .. uncommit_total .. ', diff=' .. diff)
            end
            
            return {deducted, uncommit_total, diff}
            """;

    /**
     * 脚本6：V2 addTotal（INCRBY total + INCR version 原子，保持 version 不变量）
     */
    private static final String ADD_TOTAL_V2_SCRIPT = """
            local total_key = KEYS[1]
            local version_key = KEYS[2]
            local delta = tonumber(ARGV[1]) or 0
            redis.call('incrby', total_key, delta)
            redis.call('incr', version_key)
            return 1
            """;

    /**
     * 脚本7：V2 decreaseTotal CAS（version 匹配才 DECRBY + INCR version；不匹配 no-op）
     */
    private static final String DECREASE_TOTAL_V2_CAS_SCRIPT = """
            local total_key = KEYS[1]
            local version_key = KEYS[2]
            local expect_version = tonumber(ARGV[1])
            local amount = tonumber(ARGV[2]) or 0
            local current_version = tonumber(redis.call('get', version_key)) or 0
            if current_version ~= expect_version then
                return 0
            end
            redis.call('incrby', total_key, -amount)
            redis.call('incr', version_key)
            return 1
            """;

    /**
     * 脚本8：V2 increaseDeducted CAS（version 匹配才 INCRBY + INCR version；不匹配 no-op）
     */
    private static final String INCREASE_DEDUCTED_V2_CAS_SCRIPT = """
            local deducted_key = KEYS[1]
            local version_key = KEYS[2]
            local expect_version = tonumber(ARGV[1])
            local amount = tonumber(ARGV[2]) or 0
            local current_version = tonumber(redis.call('get', version_key)) or 0
            if current_version ~= expect_version then
                return 0
            end
            redis.call('incrby', deducted_key, amount)
            redis.call('incr', version_key)
            return 1
            """;

    /**
     * 脚本9：V2 权威状态初始化（reconcile 补建缺失键，SETNX 语义）。
     * 仅初始化缺失的 total / deducted / version；已存在的键绝不覆盖（幂等、多实例安全）。
     * version 缺失时初始化为 0（与"尚无写入"语义一致）。
     * @return 1 = 至少补建了一个键，0 = 所有键已存在
     */
    private static final String INIT_V2_STATE_SCRIPT = """
            local total_key = KEYS[1]
            local deducted_key = KEYS[2]
            local version_key = KEYS[3]
            local total = tonumber(ARGV[1]) or 0
            local deducted = tonumber(ARGV[2]) or 0
            local applied = 0
            if redis.call('exists', total_key) == 0 then
                redis.call('set', total_key, total)
                applied = 1
            end
            if redis.call('exists', deducted_key) == 0 then
                redis.call('set', deducted_key, deducted)
                applied = 1
            end
            if redis.call('exists', version_key) == 0 then
                redis.call('set', version_key, 0)
                applied = 1
            end
            return applied
            """;

    // ========================== 初始化（序列化配置） ==========================
    /**
     * 初始化RedisTemplate序列化器（避免key/value乱码）
     */
    @PostConstruct
    public void initRedisTemplate() {
        // Key序列化
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        // Value序列化（Lua脚本参数/返回值与zset member统一按字符串处理，避免JSON字符串包裹）
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
    }

    // ========================== 核心操作方法 ==========================

    /**
     * 库存预扣（原子操作）
     * @param skuId 商品SKU ID
     * @param amount 预扣数量（正数）
     * @param bizId 唯一业务ID（订单ID，确保幂等）
     * @return 成功返回预扣member（用于后续回滚）；失败返回null
     * @deprecated 使用 {@link #preDeductInventoryV2(String, String, int, String)} 替代（含 shopId 隔离）
     */
    @Deprecated
    public String preDeductInventory(String skuId, int amount, String bizId) {
        // 参数校验
        Assert.hasText(skuId, "skuId不能为空");
        Assert.isTrue(amount > 0, "预扣数量必须大于0");
        Assert.hasText(bizId, "bizId不能为空");

        // 构造KEYS
        String totalKey = String.format(KEY_PREFIX_TOTAL, skuId);
        String deductedKey = String.format(KEY_PREFIX_DEDUCTED, skuId);
        String uncommitKey = String.format(KEY_PREFIX_UNCOMMIT, skuId);
        List<String> keys = Arrays.asList(totalKey, deductedKey, uncommitKey);

        // 构造ARGV
        List<String> args = Arrays.asList(String.valueOf(amount), bizId);

        // 执行脚本
        DefaultRedisScript<Object> script = new DefaultRedisScript<>(PRE_DEDUCT_SCRIPT, Object.class);
        Object result = redisTemplate.execute(script, keys, args.toArray());

        // 处理返回值
        if (result == null) {
            log.error("库存预扣失败：Redis执行返回空，skuId={}, amount={}, bizId={}", skuId, amount, bizId);
            return null;
        }
        if (result instanceof Long) {
            long code = (Long) result;
            ResultCode resultCode = ResultCode.getByCode((int) code);
            log.error("库存预扣失败：{}，skuId={}, amount={}, bizId={}", resultCode.getDesc(), skuId, amount, bizId);
            return null;
        }
        // 成功返回member
        String member = (String) result;
        log.info("库存预扣成功，skuId={}, amount={}, bizId={}, member={}", skuId, amount, bizId, member);
        return member;
    }

    /**
     * 回滚已预扣的库存（原子操作）
     * @param skuId 商品SKU ID
     * @param preDeductMember 预扣时返回的member
     * @return true=回滚成功，false=回滚失败
     * @deprecated 使用 {@link #rollbackPreDeductV2(String, String, String)} 替代（含 shopId 隔离）
     */
    @Deprecated
    public boolean rollbackPreDeduct(String skuId, String preDeductMember) {
        // 参数校验
        Assert.hasText(skuId, "skuId不能为空");
        Assert.hasText(preDeductMember, "preDeductMember不能为空");

        // 构造KEYS
        String deductedKey = String.format(KEY_PREFIX_DEDUCTED, skuId);
        String uncommitKey = String.format(KEY_PREFIX_UNCOMMIT, skuId);
        String rollbackErrorKey = KEY_PREFIX_ROLLBACK_ERROR;
        List<String> keys = Arrays.asList(deductedKey, uncommitKey, rollbackErrorKey);

        // 构造ARGV
        List<String> args = Arrays.asList(preDeductMember);

        // 执行脚本
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ROLLBACK_PRE_DEDUCT_SCRIPT, Long.class);
        Long resultCode = redisTemplate.execute(script, keys, args.toArray());

        // 处理返回值
        if (resultCode == null) {
            log.error("库存回滚失败：Redis执行返回空，skuId={}, member={}", skuId, preDeductMember);
            return false;
        }
        if (resultCode == ResultCode.SUCCESS.getCode()) {
            log.info("库存回滚成功，skuId={}, member={}", skuId, preDeductMember);
            return true;
        } else {
            ResultCode code = ResultCode.getByCode(resultCode.intValue());
            log.error("库存回滚失败：{}，skuId={}, member={}", code.getDesc(), skuId, preDeductMember);
            return false;
        }
    }

    /**
     * CAS更新总库存（版本号校验，原子操作）
     * @param skuId 商品SKU ID
     * @param expectVersion 期望的版本号
     * @param added 要增加的库存数量（可正可负，如+100=入库，-50=退货）
     * @return true=更新成功，false=版本号不匹配/更新失败
     */
    public boolean casUpdateTotal(String skuId, int expectVersion, int added) {
        // 参数校验
        Assert.hasText(skuId, "skuId不能为空");
        Assert.isTrue(expectVersion >= 0, "期望版本号必须≥0");

        // 构造KEYS
        String totalKey = String.format(KEY_PREFIX_TOTAL, skuId);
        String versionKey = String.format(KEY_PREFIX_VERSION, skuId);
        List<String> keys = Arrays.asList(totalKey, versionKey);

        // 构造ARGV
        List<String> args = Arrays.asList(String.valueOf(expectVersion), String.valueOf(added));

        // 执行脚本
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(CAS_UPDATE_TOTAL_SCRIPT, Long.class);
        Long resultCode = redisTemplate.execute(script, keys, args.toArray());

        // 处理返回值
        if (resultCode == null) {
            log.error("CAS更新总库存失败：Redis执行返回空，skuId={}, expectVersion={}, added={}", skuId, expectVersion, added);
            return false;
        }
        if (resultCode == ResultCode.SUCCESS.getCode()) {
            log.info("CAS更新总库存成功，skuId={}, expectVersion={}, added={}", skuId, expectVersion, added);
            return true;
        } else {
            log.error("CAS更新总库存失败：版本号不匹配，skuId={}, expectVersion={}, added={}", skuId, expectVersion, added);
            return false;
        }
    }

    /**
     * 清理超时未提交的预扣记录（原子操作）
     * @param skuId 商品SKU ID（传null则清理所有SKU，需自行扩展批量逻辑）
     * @param timeoutMs 超时时间（毫秒，默认30分钟）
     * @return 清理的记录数
     * @deprecated 使用 {@link #cleanTimeoutUncommitV2(String, String, Long)} 替代（含 shopId 隔离）
     */
    @Deprecated
    public long cleanTimeoutUncommit(String skuId, Long timeoutMs) {

        // 参数校验
        Assert.hasText(skuId, "skuId不能为空");
        long timeout = timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;

        // 构造KEYS
        String deductedKey = String.format(KEY_PREFIX_DEDUCTED, skuId);
        String uncommitKey = String.format(KEY_PREFIX_UNCOMMIT, skuId);
        List<String> keys = Arrays.asList(deductedKey, uncommitKey);

        // 构造ARGV
        List<String> args = Arrays.asList(String.valueOf(timeout));

        // 执行脚本
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(CLEAN_TIMEOUT_UNCOMMIT_SCRIPT, Long.class);
        Long cleanCount = redisTemplate.execute(script, keys, args.toArray());

        // 处理返回值
        cleanCount = cleanCount == null ? 0 : cleanCount;
        log.info("清理超时未提交预扣记录完成，skuId={}, 超时时间={}ms, 清理数量={}", skuId, timeout, cleanCount);
        return cleanCount;
    }

    /**
     * 库存数据对账（修正deducted与uncommit ZSet的金额差异）
     * @param skuId 商品SKU ID
     * @return 对账结果（deducted=对账前已扣减金额，uncommitTotal=uncommit总金额，diff=差异，fixedDeducted=修正后金额）
     */
    public Map<String, Long> checkInventoryConsistency(String skuId) {
        // 参数校验
        Assert.hasText(skuId, "skuId不能为空");

        // 构造KEYS
        String deductedKey = String.format(KEY_PREFIX_DEDUCTED, skuId);
        String uncommitKey = String.format(KEY_PREFIX_UNCOMMIT, skuId);
        String checkLogKey = KEY_PREFIX_CHECK_LOG;
        List<String> keys = Arrays.asList(deductedKey, uncommitKey, checkLogKey);

        // 执行脚本
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>(CHECK_CONSISTENCY_SCRIPT);
        List<Long> result = redisTemplate.execute(script, keys);

        // 处理返回值
        if (result == null || result.size() != 3) {
            log.error("库存对账失败：Redis执行返回异常，skuId={}", skuId);
            return Map.of(
                    "deducted", 0L,
                    "uncommitTotal", 0L,
                    "diff", 0L,
                    "fixedDeducted", 0L
            );
        }
        long deducted = result.get(0);
        long uncommitTotal = result.get(1);
        long diff = result.get(2);
        long fixedDeducted = deducted - diff;

        log.info("库存对账完成，skuId={}, 对账前deducted={}, uncommit总金额={}, 差异={}, 修正后deducted={}",
                skuId, deducted, uncommitTotal, diff, fixedDeducted);

        return Map.of(
                "deducted", deducted,
                "uncommitTotal", uncommitTotal,
                "diff", diff,
                "fixedDeducted", fixedDeducted
        );
    }

    // ========================== V2 方法（shopId + skuId 隔离） ==========================

    /**
     * V2：库存预扣（原子操作，含 shopId 隔离）
     *
     * @param shopId 店铺ID
     * @param skuId  商品SKU ID
     * @param amount 预扣数量（正数）
     * @param bizId  唯一业务ID（orderId）
     * @return 成功返回预扣 member（occupyId）；失败返回 null
     */
    public String preDeductInventoryV2(String shopId, String skuId, int amount, String bizId) {
        Assert.hasText(shopId, "shopId不能为空");
        Assert.hasText(skuId, "skuId不能为空");
        Assert.isTrue(amount > 0, "预扣数量必须大于0");
        Assert.hasText(bizId, "bizId不能为空");

        String totalKey = String.format(KEY_V2_TOTAL, shopId, skuId);
        String deductedKey = String.format(KEY_V2_DEDUCTED, shopId, skuId);
        String uncommitKey = String.format(KEY_V2_UNCOMMIT, shopId, skuId);
        String versionKey = String.format(KEY_V2_VERSION, shopId, skuId);
        List<String> keys = Arrays.asList(totalKey, deductedKey, uncommitKey, versionKey);
        List<String> args = Arrays.asList(String.valueOf(amount), bizId);

        DefaultRedisScript<Object> script = new DefaultRedisScript<>(PRE_DEDUCT_SCRIPT, Object.class);
        Object result = redisTemplate.execute(script, keys, args.toArray());

        if (result == null) {
            log.error("V2库存预扣失败：Redis执行返回空，shopId={}, skuId={}, amount={}, bizId={}", shopId, skuId, amount, bizId);
            return null;
        }
        if (result instanceof Long) {
            long code = (Long) result;
            ResultCode resultCode = ResultCode.getByCode((int) code);
            log.error("V2库存预扣失败：{}，shopId={}, skuId={}, amount={}, bizId={}", resultCode.getDesc(), shopId, skuId, amount, bizId);
            return null;
        }
        String member = (String) result;
        log.info("V2库存预扣成功，shopId={}, skuId={}, amount={}, bizId={}, member={}", shopId, skuId, amount, bizId, member);
        return member;
    }

    /**
     * V2：回滚已预扣的库存（原子操作，含 shopId 隔离）
     *
     * @param shopId          店铺ID
     * @param skuId           商品SKU ID
     * @param preDeductMember 预扣时返回的 member
     * @return true=回滚成功，false=回滚失败或记录不存在
     */
    public boolean rollbackPreDeductV2(String shopId, String skuId, String preDeductMember) {
        Assert.hasText(shopId, "shopId不能为空");
        Assert.hasText(skuId, "skuId不能为空");
        Assert.hasText(preDeductMember, "preDeductMember不能为空");

        String deductedKey = String.format(KEY_V2_DEDUCTED, shopId, skuId);
        String uncommitKey = String.format(KEY_V2_UNCOMMIT, shopId, skuId);
        String versionKey = String.format(KEY_V2_VERSION, shopId, skuId);
        String rollbackErrorKey = KEY_PREFIX_ROLLBACK_ERROR;
        List<String> keys = Arrays.asList(deductedKey, uncommitKey, versionKey, rollbackErrorKey);
        List<String> args = Arrays.asList(preDeductMember);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ROLLBACK_PRE_DEDUCT_SCRIPT, Long.class);
        Long resultCode = redisTemplate.execute(script, keys, args.toArray());

        if (resultCode == null) {
            log.error("V2库存回滚失败：Redis执行返回空，shopId={}, skuId={}, member={}", shopId, skuId, preDeductMember);
            return false;
        }
        if (resultCode == ResultCode.SUCCESS.getCode()) {
            log.info("V2库存回滚成功，shopId={}, skuId={}, member={}", shopId, skuId, preDeductMember);
            return true;
        } else {
            ResultCode code = ResultCode.getByCode(resultCode.intValue());
            log.warn("V2库存回滚：{}，shopId={}, skuId={}, member={}", code.getDesc(), shopId, skuId, preDeductMember);
            return false;
        }
    }

    /**
     * V2：清理超时未提交的预扣记录（原子操作，含 shopId 隔离）
     */
    public long cleanTimeoutUncommitV2(String shopId, String skuId, Long timeoutMs) {
        Assert.hasText(shopId, "shopId不能为空");
        Assert.hasText(skuId, "skuId不能为空");
        long timeout = timeoutMs == null ? DEFAULT_TIMEOUT_MS : timeoutMs;

        String deductedKey = String.format(KEY_V2_DEDUCTED, shopId, skuId);
        String uncommitKey = String.format(KEY_V2_UNCOMMIT, shopId, skuId);
        String versionKey = String.format(KEY_V2_VERSION, shopId, skuId);
        List<String> keys = Arrays.asList(deductedKey, uncommitKey, versionKey);
        List<String> args = Arrays.asList(String.valueOf(timeout));

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(CLEAN_TIMEOUT_UNCOMMIT_SCRIPT, Long.class);
        Long cleanCount = redisTemplate.execute(script, keys, args.toArray());
        cleanCount = cleanCount == null ? 0 : cleanCount;
        log.info("V2清理超时未提交预扣记录完成，shopId={}, skuId={}, 超时时间={}ms, 清理数量={}", shopId, skuId, timeout, cleanCount);
        return cleanCount;
    }

    /**
     * V2：获取 total key 名（供外部 SETNX 初始化使用）
     */
    public String getTotalKeyV2(String shopId, String skuId) {
        return String.format(KEY_V2_TOTAL, shopId, skuId);
    }

    /**
     * V2：bump the total cache by a signed delta (INCRBY). Caller must ensure the key
     * is initialized (see RedisInventoryDeductGateway.ensureTotalKeyInitialized).
     */
    public boolean addTotalV2(String shopId, String skuId, long delta) {
        Assert.hasText(shopId, "shopId不能为空");
        Assert.hasText(skuId, "skuId不能为空");
        String totalKey = String.format(KEY_V2_TOTAL, shopId, skuId);
        String versionKey = String.format(KEY_V2_VERSION, shopId, skuId);
        List<String> keys = Arrays.asList(totalKey, versionKey);
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(ADD_TOTAL_V2_SCRIPT, Long.class);
            redisTemplate.execute(script, keys, String.valueOf(delta));
            log.info("V2 addTotal: key={}, delta={}", totalKey, delta);
            return true;
        } catch (Exception e) {
            log.error("V2 addTotal failed: key={}, delta={}", totalKey, delta, e);
            return false;
        }
    }

    /**
     * V2：获取 deducted key 名（供对账读取使用）
     */
    public String getDeductedKeyV2(String shopId, String skuId) {
        return String.format(KEY_V2_DEDUCTED, shopId, skuId);
    }

    /**
     * V2：获取 version key 名（供对账读取 / CAS 修复使用）
     */
    public String getVersionKeyV2(String shopId, String skuId) {
        return String.format(KEY_V2_VERSION, shopId, skuId);
    }

    /**
     * V2：DECRBY total by amount with version CAS（reconcile repair）。
     * version 匹配才应用 + bump；不匹配 no-op 返回 false（幂等，多实例安全）。
     */
    public boolean decreaseTotalV2(String shopId, String skuId, long amount, long expectVersion) {
        Assert.hasText(shopId, "shopId不能为空");
        Assert.hasText(skuId, "skuId不能为空");
        Assert.isTrue(amount > 0, "amount必须大于0");
        String totalKey = String.format(KEY_V2_TOTAL, shopId, skuId);
        String versionKey = String.format(KEY_V2_VERSION, shopId, skuId);
        List<String> keys = Arrays.asList(totalKey, versionKey);
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(DECREASE_TOTAL_V2_CAS_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, keys, String.valueOf(expectVersion), String.valueOf(amount));
            boolean applied = result != null && result == 1L;
            log.info("V2 decreaseTotal CAS: key={}, amount={}, expectVersion={}, applied={}",
                    totalKey, amount, expectVersion, applied);
            return applied;
        } catch (Exception e) {
            log.error("V2 decreaseTotal CAS failed: key={}, amount={}", totalKey, amount, e);
            return false;
        }
    }

    /**
     * V2：INCRBY deducted by amount with version CAS（reconcile repair）。
     * version 匹配才应用 + bump；不匹配 no-op 返回 false（幂等，多实例安全）。
     */
    public boolean increaseDeductedV2(String shopId, String skuId, long amount, long expectVersion) {
        Assert.hasText(shopId, "shopId不能为空");
        Assert.hasText(skuId, "skuId不能为空");
        Assert.isTrue(amount > 0, "amount必须大于0");
        String deductedKey = String.format(KEY_V2_DEDUCTED, shopId, skuId);
        String versionKey = String.format(KEY_V2_VERSION, shopId, skuId);
        List<String> keys = Arrays.asList(deductedKey, versionKey);
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCREASE_DEDUCTED_V2_CAS_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, keys, String.valueOf(expectVersion), String.valueOf(amount));
            boolean applied = result != null && result == 1L;
            log.info("V2 increaseDeducted CAS: key={}, amount={}, expectVersion={}, applied={}",
                    deductedKey, amount, expectVersion, applied);
            return applied;
        } catch (Exception e) {
            log.error("V2 increaseDeducted CAS failed: key={}, amount={}", deductedKey, amount, e);
            return false;
        }
    }


    /**
     * V2：权威状态初始化（reconcile 补建缺失键）。
     * SETNX 语义：仅补建缺失的 total/deducted/version；已存在键不覆盖。
     * 多实例/并发安全：并发业务写先建键则本调用 no-op（返回 false），由下轮对账评估。
     *
     * @return true 当至少补建了一个键
     */
    public boolean initStateV2(String shopId, String skuId, long targetTotal, long targetDeducted) {
        Assert.hasText(shopId, "shopId不能为空");
        Assert.hasText(skuId, "skuId不能为空");
        String totalKey = String.format(KEY_V2_TOTAL, shopId, skuId);
        String deductedKey = String.format(KEY_V2_DEDUCTED, shopId, skuId);
        String versionKey = String.format(KEY_V2_VERSION, shopId, skuId);
        List<String> keys = Arrays.asList(totalKey, deductedKey, versionKey);
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(INIT_V2_STATE_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, keys, String.valueOf(targetTotal), String.valueOf(targetDeducted));
            boolean applied = result != null && result == 1L;
            log.info("V2 initState: shopId={}, skuId={}, targetTotal={}, targetDeducted={}, applied={}",
                    shopId, skuId, targetTotal, targetDeducted, applied);
            return applied;
        } catch (Exception e) {
            log.error("V2 initState failed: shopId={}, skuId={}", shopId, skuId, e);
            return false;
        }
    }
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DeductResult{
        private String orderId;

        private String skuId;

        private String quantity;

        private String reserveId;

    }

    public static class ReleaseResult {
        
        
    }
}