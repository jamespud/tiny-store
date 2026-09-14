-- 在业务事务提交之后，把幂等记录推进到 SUCCEEDED 并缓存响应。
--
-- KEYS[1] = idempotency:trade:create:<key>   (Redis HASH)
-- ARGV[1] = response json
-- ARGV[2] = ttl seconds
--
-- 返回值：1 表示记录仍属于本次请求并被推进；0 表示记录已不存在（例如已被回滚清理）或指纹不符。

local key = KEYS[1]
local fingerprint = ARGV[3]

local existing = redis.call('HGET', key, 'fingerprint')
if not existing then
  return 0
end
if fingerprint and existing ~= fingerprint then
  return 0
end

redis.call('HSET', key, 'state', 'SUCCEEDED', 'response', ARGV[1])
redis.call('EXPIRE', key, tonumber(ARGV[2]))
return 1
