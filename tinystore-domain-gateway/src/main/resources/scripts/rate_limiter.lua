-- Token bucket rate limiter script
-- KEYS[1] - tokens key
-- KEYS[2] - timestamp key
-- ARGV[1] - capacity
-- ARGV[2] - refill rate per second
-- ARGV[3] - now (epoch seconds)
-- ARGV[4] - requested tokens

local tokens_key = KEYS[1]
local timestamp_key = KEYS[2]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local tokens = tonumber(redis.call("GET", tokens_key))
if tokens == nil then
  tokens = capacity
end

local last_refreshed = tonumber(redis.call("GET", timestamp_key))
if last_refreshed == nil then
  last_refreshed = now
end

local delta = now - last_refreshed
if delta > 0 and refill_rate > 0 then
  local refill = math.floor(delta * refill_rate)
  if refill > 0 then
    tokens = math.min(capacity, tokens + refill)
    last_refreshed = now
  end
end

local allowed = tokens >= requested
local retry_after = 0

if allowed then
  tokens = tokens - requested
else
  local deficit = requested - tokens
  if refill_rate > 0 then
    retry_after = math.ceil(deficit / refill_rate)
  else
    retry_after = 1
  end
end

local ttl_seconds = math.max(2 * capacity / math.max(refill_rate, 1), 1)
redis.call("SET", tokens_key, tokens, "EX", math.ceil(ttl_seconds))
redis.call("SET", timestamp_key, last_refreshed, "EX", math.ceil(ttl_seconds))

local allowed_numeric = allowed and 1 or 0
return {allowed_numeric, tokens, retry_after}
