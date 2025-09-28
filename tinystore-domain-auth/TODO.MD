[MODE: PLAN]

## 范围与约束
- 保持对外 OAuth2/OIDC 端点、授权码/令牌/同意/登出等契约完全不变（Spring Authorization Server 仍作为协议端点承载者）。
- 仅在 tinystore-domain-auth 内部完成 DDD 分层与端口/适配器改造；暂不拆子模块。
- 基于“包内迁移/渐进抽象”，可分三阶段落地（见下文“迁移分期”）。

## 一、包结构重构（package-by-component）
在 tinystore-domain-auth 下新建四层包，逐步迁移现有类至对应位置，并新增 ACL/端口：

- `com.tinystore.auth.domain`
  - `model.user`：MallUser 聚合与实体/值对象
  - `model.otp`：OTP 实体（用户聚合内子实体）
  - `model.consent`：Consent 领域对象（聚合根为 UserConsent）
  - `event`：领域事件（UserFrozenEvent、RefreshTokenRevokedEvent、ConsentChangedEvent、UserRegisteredEvent）
  - `service`：领域服务（OtpGenerationService、RefreshTokenVersionService、ScopePolicyService）
  - `exception`：领域异常（UserFrozenException、OtpRateLimitExceeded、OtpInvalid/Expired 等）
  - `primitives`：值对象（UserId、PhoneNumber、OtpCode、RtVersion、ClientId、Scopes 等）
- `com.tinystore.auth.application`
  - `port.in`（用例输入端口）：接口由接口层驱动
    - `OtpUseCase`、`AuthUseCase`、`ConsentUseCase`、`TokenRevocationUseCase`、`UserAdminUseCase`、`AuditQueryUseCase`
  - `port.out`（输出端口/依赖）：由基础设施实现
    - `UserRepositoryPort`、`OtpRepositoryPort`、`ConsentRepositoryPort`
    - `AuthorizationStorePort`（SAS 授权/令牌/同意适配）
    - `SmsSenderPort`、`LockAndRateLimitPort`（Redis/分布式锁+计数）
    - `OutboxPort`（保存领域事件）、
      `EventPublisherPort`（出站发布器）
    - `AuditLogPort`（审计写入/查询）
  - `service`（应用服务/用例编排）：实现 port.in，组合领域服务/port.out
  - `dto`：输入输出 DTO（对接口层友好）
- `com.tinystore.auth.infrastructure`
  - `persistence.jpa` 或 `persistence.jdbc`：MallUser/OTP/Consent 仓储实现与映射
  - `sso.sas`：SAS 适配器（AuthorizationServer ACL、TokenCustomizer、ConsentService 适配）
  - `cache.redis`：Redisson/Redis 限流与分布式锁适配
  - `outbox`：Outbox 仓储与发布器（定时/事务外触发）
  - `audit`：审计存储实现
  - `config`：Spring Bean 装配、属性绑定、事务、调度器
- `com.tinystore.auth.interfaces`
  - `rest`：OTP、撤销、用户管理/查询、审计查询等 REST 控制器（保留现有路径）
  - `web`：登录/同意模板控制器（保持 UI/表单契约）
  - `security`：HTTP 安全配置、过滤器；OTP Authentication Filter/Provider 的接入点
  - `mapper`：DTO ↔ 领域对象的 MapStruct（可选）

说明：
- 现有 `AuthorizationServerConfig`、`SecurityConfig`、`TokenCustomizer` 等移动/拆分到 `infrastructure.sso.sas` + `interfaces.security`，通过 ACL 隔离领域。
- 现有 `OtpController`、`AdminTokenController`、`ConsentController`、`LoginController` 迁移到 `interfaces.rest|web` 并改为调用 application 服务。

## 二、领域模型与不变量

### 1) MallUser 聚合（`domain.model.user`）
- 实体
  - `MallUser`
    - 字段：UserId id（UUID/雪花）、PhoneNumber phone、nickname、avatar、status（ACTIVE/FROZEN）、RtVersion rtVersion（long≥1）、createdAt、updatedAt
    - 行为：
      - `freeze()` / `unfreeze()`：状态切换（产生 UserFrozenEvent/UnfrozenEvent）
      - `bumpRtVersion()`：rtVersion 自增（产生 RefreshTokenRevokedEvent）
      - `changePhone(PhoneNumber, OtpCode)`：带 OTP 校验的手机号变更（本阶段可占位）
      - `updateProfile(...)`：资料变更（限账户上下文触发时校验来源）
- 不变量
  - phone 全局唯一，可变更需 OTP 校验
  - status=FROZEN 时禁止登录/发 OTP
  - rtVersion 单调递增，初始1，撤销 RT 时必须递增
- 值对象
  - `UserId`、`PhoneNumber`（格式/号段校验）、`RtVersion`

### 2) OTP 实体（`domain.model.otp`）
- 作为 MallUser 聚合内子实体，或独立实体并通过 UserId 关联（实现时表独立存储）
- 字段：id、userId、code（脱敏/加密存储可选）、expireAt、usedAt、retryCount、sendCount、lastSendAt
- 规则（由领域服务执行）：
  - 有效期 10 分钟；验证成功后一次性失效
  - 同手机号 1 分钟内最多生成 2 次（计数+窗口）
  - 错误 3 次后 5 分钟封禁
  - 冻结用户不可生成 OTP

### 3) Consent 领域对象（`domain.model.consent`）
- `UserConsent`：userId、clientId、grantedScopes(Set<ScopeName>)、updatedAt
- 行为：
  - `grant(scopes)`：并集；产生 ConsentChangedEvent
  - `revoke(scopes)`：差集；产生 ConsentChangedEvent
  - 审计：每次变更写审计

### 4) 领域服务（`domain.service`）
- `OtpGenerationService`
  - `generateFor(PhoneNumber)`：计算 code、有效期；返回 Otp 实例
  - `verify(userId, OtpCode)`：幂等校验，返回通过/失败类型
- `RefreshTokenVersionService`
  - `revokeAllOf(UserId)`：驱动 rtVersion 自增，并要求外部清理授权（port.out）
- `ScopePolicyService`
  - `isUserVisible(scope)`、`isDefaultGranted(scope)`、`requiresConsent(scope)`

### 5) 领域事件（`domain.event`）
- `RefreshTokenRevokedEvent(userId, rtVersion, reason, at)`
- `UserFrozenEvent(userId, at)` / `UserUnfrozenEvent`
- `ConsentChangedEvent(userId, clientId, addedScopes, removedScopes, at)`
- `UserRegisteredEvent(userId, phone, at)`

## 三、用例（应用层）与端口

### 输入端口（`application.port.in`）
- `OtpUseCase`
  - `SendOtpCommand(phone, requestId, ip, ua)` → `SendOtpResult(deliveryChannel, masked)`（幂等，使用 requestId 去重）
  - `VerifyOtpCommand(phone, code)` → `AuthResult(userId)`（通过则返回 userId）
- `AuthUseCase`
  - `LoginByOtp(phone, code)` → `AuthResult(userId)`（组合 VerifyOtp + 用户自动注册）
- `ConsentUseCase`
  - `LoadConsent(userId, clientId)` → `ConsentDto`
  - `ApproveScopes(userId, clientId, scopes)` → `ConsentDto`
  - `RevokeScopes(userId, clientId, scopes)` → `ConsentDto`
- `TokenRevocationUseCase`
  - `RevokeUserTokens(userId, reason)` → void（触发 rtVersion++ 并清理 SAS 授权）
- `UserAdminUseCase`
  - `FreezeUser(userId)`、`UnfreezeUser(userId)`、`FindByPhone(phone)`、`GetStatus(userId)`
- `AuditQueryUseCase`
  - `QueryAudits(filter, page)` → `Page<AuditRecordDto>`

### 输出端口（`application.port.out`）
- `UserRepositoryPort`
  - `findByPhone(PhoneNumber)`、`findById(UserId)`、`save(MallUser)`、`update(MallUser)`、`existsPhone(PhoneNumber)`
- `OtpRepositoryPort`
  - `create(otp)`、`markUsed(id, at)`、`increaseRetry(userId)`、`countSentInWindow(phone, window)`、`blockUntil(phone, until)`、`isBlocked(phone, now)`
- `ConsentRepositoryPort`
  - `find(userId, clientId)`、`save(UserConsent)`
- `AuthorizationStorePort`（SAS ACL）
  - `clearAuthorizationsOf(userId)`（删除 oauth2_authorization 等）
  - `currentRtVersionOf(userId)`（可选核验）
  - `persistConsent(userId, clientId, scopes)`、`loadConsent(...)`（与 SAS 表映射）
- `SmsSenderPort`
  - `sendOtp(phone, code)` → `SmsDeliveryResult`
- `LockAndRateLimitPort`
  - `withLock(key, ttl, callback)`；`incr(key, window)`；`get(key)`；`setex(key, ttl, value)`
- `OutboxPort`
  - `save(domainEvent)`；`fetchUnpublished(batchSize)`；`markPublished(ids)`
- `EventPublisherPort`
  - `publish(List<DomainEvent>)`
- `AuditLogPort`
  - `append(AuditRecord)`、`query(filter, page)`

## 四、适配器与 ACL

### SAS 适配（`infrastructure.sso.sas`）
- `SasAuthorizationStoreAdapter implements AuthorizationStorePort`
  - 基于 `JdbcOAuth2AuthorizationService/JdbcOAuth2AuthorizationConsentService` 的受限访问，封装为领域接口
- `SasTokenCustomizerAdapter`
  - 现有 Token 自定义迁移至此；读取 MallUser/Consent 信息，仅通过端口访问仓储
- `SasConsentServiceAdapter`
  - 在处理同意页时，使用 `ConsentRepositoryPort` + `AuthorizationStorePort` 双写/一致化

### 持久化适配
- `JpaUserRepositoryAdapter implements UserRepositoryPort`（或 Spring JDBC）
- `JdbcOtpRepositoryAdapter implements OtpRepositoryPort`
- `JdbcConsentRepositoryAdapter implements ConsentRepositoryPort`
- `JdbcOutboxAdapter implements OutboxPort`
- `JdbcAuditLogAdapter implements AuditLogPort`

### Redis/锁/限流
- `RedissonLockAndRateLimitAdapter implements LockAndRateLimitPort`
  - key 设计：
    - OTP 发送计数：`otp:send:{phone}:{yyyyMMddHHmm}` 窗口
    - OTP 封禁：`otp:block:{phone}`
    - 幂等：`otp:req:{requestId}`

## 五、接口层调整（保持外部契约不变）
- `interfaces.web.LoginController`：改为调用 `AuthUseCase.LoginByOtp`
- `interfaces.rest.OtpController`：
  - POST `/api/auth/otp/send`：`SendOtpCommand`（携带 requestId）
  - POST `/api/auth/otp/verify`：`VerifyOtpCommand`
- `interfaces.web.ConsentController`：通过 `ConsentUseCase` 查询/提交授权
- `interfaces.rest.AdminTokenController`：调用 `TokenRevocationUseCase`；冻结/解冻接口调用 `UserAdminUseCase`
- 现有 `SecurityConfig` 保留对外路径/拦截；内部 AuthenticationProvider 改为依赖 `OtpUseCase`

## 六、数据库与迁移（Flyway）
新增迁移文件（在 `src/main/resources/db/migration/`）：

- `V3__auth_outbox_and_audit.sql`
  - `auth_outbox`：`id bigserial pk`, `type varchar(120)`, `aggregate_id varchar(64)`, `payload jsonb`, `occurred_at timestamptz`, `published boolean default false`, 索引(type, published)
  - `auth_audit`：`id bigserial pk`, `user_id uuid`, `client_id varchar(64)`, `action varchar(64)`, `scopes text[]`, `ip inet`, `ua text`, `created_at timestamptz`, 索引(user_id, created_at)
- `V4__otp_rate_limit_columns.sql`
  - 如需在 `login_otp` 增补：`retry_count int default 0`, `send_count int default 0`, `last_send_at timestamptz`, 索引(user_id, last_send_at)
- `V5__consent_history.sql`
  - `auth_consent_history`：`id bigserial pk`, `user_id uuid`, `client_id varchar(64)`, `added_scopes text[]`, `removed_scopes text[]`, `created_at timestamptz`

注意：不改动 SAS 自带表结构；Consent 历史为审计用途，实际授权仍走 SAS 表。

## 七、配置与属性
- `application.yml` 新增：
  - `auth.otp.valid-seconds=600`
  - `auth.otp.block-seconds=300`
  - `auth.otp.max-send-per-minute=2`
  - `auth.outbox.publish-interval=PT5S`（定时发布）
  - Redis/Redisson 连接设置
- Bean 装配：
  - `@EnableScheduling` 定时发布 outbox
  - 事务边界：应用服务作为事务脚手架（写操作开启事务）

## 八、并发、事务与幂等
- 发送 OTP：
  - 幂等键：`otp:req:{requestId}`，存在即直接返回上次结果
  - 分布式锁：`otp:lock:{phone}`，生成/验证操作包裹在独占锁内
  - 计数窗口：分钟粒度 `send_count` + Redis 计数器
- 验证 OTP：
  - 读写同表并 `usedAt` 标记一次性；失败自增 `retry_count`，到阈值设置 `block:{phone}`
- 撤销 RT：
  - 单事务：`rtVersion++` + 写 Outbox（`RefreshTokenRevokedEvent`）+ 调用 `AuthorizationStorePort.clearAuthorizationsOf(userId)`；失败重试策略

## 九、审计与事件发布
- 写审计：
  - 登录成功/失败、发 OTP、验证 OTP（成功/失败原因）、授权/撤销、冻结/解冻、撤销 RT
- 出站事件：
  - 定时任务扫描 `auth_outbox`（未发布行），批量交给 `EventPublisherPort`（可先实现为日志/本地发布），发布成功后 `markPublished`
  - 后续可替换为 Kafka/RabbitMQ 适配

## 十、测试策略
- 单元测试（domain/application）：
  - OtpGenerationService：有效期/次数/封禁边界
  - RefreshTokenVersionService：并发自增与事件写入
  - Consent 行为：增/删 scopes
- 合同/集成测试（Testcontainers）：
  - Postgres：OTP 生成-验证一次性；撤销 RT 清理 SAS 授权
  - Redis：发送限流、封禁与幂等
  - SAS 自定义器：Claims 与 Scopes 映射稳定
- 端到端（可选）：授权码 + 同意 + 刷新 + 撤销

## 十一、迁移分期与回滚
- Phase 1（安全收敛）
  - 引入分层包结构与端口接口；保持现有类，新增 Adapter 实现 port.out
  - Controller 改为调用 Application 服务；功能不变
- Phase 2（领域内聚）
  - 迁移 MallUser/OTP 规则至领域服务；补齐审计/Outbox；落库迁移
- Phase 3（事件/限流强化）
  - 引入 Redis 限流/锁；启用 Outbox 发布；新增审计查询接口
- 回滚：按阶段提交，若失败回滚到上一 tag；Flyway 向前迁移为主，必要时提供数据补丁脚本

## 十二、性能与容量
- OTP 发送限流 Redis QPS 预估：按手机号分钟粒度 key，内存占用可控
- Outbox 发布批次：默认 100 条/5 秒
- 审计表 180 天 TTL：可做分区表（后续优化）

---

```sql
IMPLEMENTATION CHECKLIST:
1. 在 tinystore-domain-auth 新建包结构：
   - com.tinystore.auth.domain.{model.user,model.otp,model.consent,service,event,exception,primitives}
   - com.tinystore.auth.application.{port.in,port.out,service,dto}
   - com.tinystore.auth.infrastructure.{persistence.jdbc,sso.sas,cache.redis,outbox,audit,config}
   - com.tinystore.auth.interfaces.{rest,web,security,mapper}
2. 定义领域值对象与异常：
   - UserId、PhoneNumber、OtpCode、RtVersion、ClientId、ScopeName
   - UserFrozenException、OtpRateLimitExceeded、OtpInvalid、OtpExpired
3. 定义聚合与实体：
   - MallUser（字段/行为如设计），Otp（子实体），UserConsent
4. 实现领域服务：
   - OtpGenerationService（generateFor/verify），RefreshTokenVersionService（revokeAllOf），ScopePolicyService
5. 定义领域事件类：
   - RefreshTokenRevokedEvent、UserFrozenEvent/UserUnfrozenEvent、ConsentChangedEvent、UserRegisteredEvent
6. 定义应用层输入端口（接口）：
   - OtpUseCase、AuthUseCase、ConsentUseCase、TokenRevocationUseCase、UserAdminUseCase、AuditQueryUseCase
7. 定义应用层输出端口（接口）：
   - UserRepositoryPort、OtpRepositoryPort、ConsentRepositoryPort、AuthorizationStorePort、SmsSenderPort、LockAndRateLimitPort、OutboxPort、EventPublisherPort、AuditLogPort
8. 编写应用服务实现（service）：
   - OtpApplicationService、AuthApplicationService、ConsentApplicationService、TokenRevocationApplicationService、UserAdminApplicationService、AuditQueryApplicationService
   - 为每个用例定义 Command/Result DTO 并落地幂等/事务边界
9. SAS 适配器（infrastructure.sso.sas）：
   - SasAuthorizationStoreAdapter：实现 clearAuthorizationsOf/persistConsent/loadConsent
   - SasTokenCustomizerAdapter：迁移现有 TokenCustomizer 逻辑到此适配器，仅通过端口访问领域数据
   - 同意页适配：与 ConsentUseCase 协作，保证双写一致
10. 持久化适配器：
    - JpaUserRepositoryAdapter/JdbcUserRepositoryAdapter 实现 UserRepositoryPort
    - JdbcOtpRepositoryAdapter 实现 OtpRepositoryPort（含 markUsed/increaseRetry/countSentInWindow/isBlocked）
    - JdbcConsentRepositoryAdapter 实现 ConsentRepositoryPort
11. Redis/锁/限流适配器：
    - RedissonLockAndRateLimitAdapter 实现 LockAndRateLimitPort；按 key 规则完成 incr/lock/setex
12. Outbox 与审计：
    - JdbcOutboxAdapter 实现 OutboxPort；Scheduled 发布器实现 EventPublisherPort（先输出日志）
    - JdbcAuditLogAdapter 实现 AuditLogPort；定义审计记录模型与查询条件
13. Flyway 迁移脚本：
    - 添加 V3__auth_outbox_and_audit.sql、V4__otp_rate_limit_columns.sql、V5__consent_history.sql（按设计 DDL）
14. 接口层替换：
    - OtpController/LoginController/ConsentController/AdminTokenController 迁移至 interfaces 层并改用应用服务
    - 保持原有 URL/表单参数与响应结构不变
15. 安全配置调整：
    - SecurityConfig 中 OTP Provider 改为调用 OtpUseCase；过滤器顺序保持
    - CORS/CSRF 例外保留；不改公共端点路径
16. 配置与 Bean：
    - application.yml 新增 auth.* 属性（otp 时效/封禁、outbox 发布周期、redis 连接）
    - @EnableScheduling；为应用服务与适配器添加 @Transactional 边界
17. 审计打点：
    - 在应用服务内为：发/验 OTP、登录成功/失败、授权/撤销、冻结/解冻、撤销令牌 写审计
18. 单元测试：
    - 领域服务（OTP/RT/Consent）边界条件与不变量；应用服务幂等/事务测试（Mockito）
19. 集成测试（Testcontainers）：
    - Postgres + Redis：覆盖 OTP 限流/一次性、撤销 RT 清理授权、Consent 双写
20. 渐进迁移执行：
    - Phase 1：引入端口/适配器与应用服务，控制器切换到应用服务；功能对比回归
    - Phase 2：引入 Outbox/审计与 Redis 限流；开启定时发布
    - Phase 3：清理遗留直连仓储/服务依赖，确保领域层零 Spring 依赖
21. 运行与回滚：
    - 每阶段打 tag；问题出现时回滚至上一个 tag；保留数据迁移只增不减（必要时补偿脚本）
```