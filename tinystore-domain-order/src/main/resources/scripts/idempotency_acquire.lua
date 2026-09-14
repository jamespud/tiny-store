-- 原子获取下单幂等权。
--
-- KEYS[1] = idempotency:trade:create:<key>   (Redis HASH)
-- ARGV[1] = fingerprint (sha256 of the business request)
-- ARGV[2] = ttl seconds
--
-- 返回：ACQUIRED | IN_PROGRESS | REPLAY | FINGERPRINT_CONFLICT
--
-- 关键点：fingerprint 的比较与状态写入必须在同一次原子操作里完成，
-- 否则"同键不同请求体"会退化成静默返回上一单结果。

local key = KEYS[1]
local fingerprint = ARGV[1]
local ttl = tonumber(ARGV[2])

local existing = redis.call('HGET', key, 'fingerprint')

if not existing then
  redis.call('HSET', key, 'fingerprint', fingerprint, 'state', 'PROCESSING')
  redis.call('EXPIRE', key, ttl)
  return 'ACQUIRED'
end

if existing ~= fingerprint then
  return 'FINGERPRINT_CONFLICT'
end

local state = redis.call('HGET', key, 'state')
if state == 'SUCCEEDED' then
  return 'REPLAY'
end

return 'IN_PROGRESS'
