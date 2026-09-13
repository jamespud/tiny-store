-- OTP 原子校验+消费（C4）。
--
-- KEYS[1] = auth:otp:<phone>   (STRING: "<code>|<expireAtEpochMs>")
-- ARGV[1] = 用户提交的验证码
-- ARGV[2] = 当前毫秒时间戳
--
-- 返回：NOT_FOUND | EXPIRED | MISMATCH | SUCCESS
--
-- 关键点：比对与"消费"必须在同一次原子执行内完成，否则并发（含跨副本）校验
-- 会让同一个验证码被成功使用多次。成功即删除 key —— 重复使用得到 NOT_FOUND。

local key = KEYS[1]
local input = ARGV[1]
local nowMs = tonumber(ARGV[2])

local raw = redis.call('GET', key)
if not raw then
  return 'NOT_FOUND'
end

local sep = string.find(raw, '|', 1, true)
local code = raw
local expireAt = 0
if sep then
  code = string.sub(raw, 1, sep - 1)
  expireAt = tonumber(string.sub(raw, sep + 1)) or 0
end

if expireAt > 0 and expireAt < nowMs then
  redis.call('DEL', key)
  return 'EXPIRED'
end

if code ~= input then
  return 'MISMATCH'
end

redis.call('DEL', key)
return 'SUCCESS'
