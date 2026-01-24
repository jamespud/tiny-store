local key = KEYS[1]
local amount = tonumber(ARGV[2])

local current = tonumber(redis.call('GET', key) or '0')
current = current + amount
redis.call('SET', key, current)
return current

