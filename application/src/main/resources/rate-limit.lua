local ttl = tonumber(ARGV[1])
if not ttl or ttl <= 0 or ttl ~= math.floor(ttl) then return redis.error_reply('positive integer TTL required') end
local count = redis.call('INCR', KEYS[1])
if count == 1 then redis.call('PEXPIRE', KEYS[1], ttl) end
return count
