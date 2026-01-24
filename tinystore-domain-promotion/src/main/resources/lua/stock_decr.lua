local key = KEYS[1]
local amount = tonumber(ARGV[2])

local current = tonumber(redis.call('GET', key) or '0')
if current < amount then
	return -1
end

current = current - amount
redis.call('SET', key, current)
return current

