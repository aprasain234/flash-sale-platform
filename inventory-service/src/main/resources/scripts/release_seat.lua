-- KEYS[1] = seats:available:{eventId}
-- KEYS[2] = reservation:{eventId}:{seatId}
--
-- Returns 1 if a held reservation was released, 0 if there was nothing to release.
-- Safe to call twice (idempotent) — a second call is a no-op.

local existing = redis.call('GET', KEYS[2])
if not existing then
    return 0
end

redis.call('DEL', KEYS[2])
redis.call('INCR', KEYS[1])
return 1
