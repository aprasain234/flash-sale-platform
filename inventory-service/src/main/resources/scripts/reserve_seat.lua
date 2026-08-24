-- KEYS[1] = seats:available:{eventId}        (integer counter)
-- KEYS[2] = reservation:{eventId}:{seatId}    (per-seat hold key)
-- ARGV[1] = userId
-- ARGV[2] = ttlSeconds
--
-- Returns:
--   1  = reservation succeeded
--   0  = seat already held by someone else
--  -1  = no inventory remaining

local available = tonumber(redis.call('GET', KEYS[1]) or '0')

if available <= 0 then
    return -1
end

-- NX semantics done manually so we can combine with the counter decrement
-- atomically inside a single script execution.
local existing = redis.call('GET', KEYS[2])
if existing then
    return 0
end

redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
redis.call('DECR', KEYS[1])
return 1
