-- Hardcoded UUIDs so we can reliably link foreign keys

INSERT INTO venues (id, name, city, capacity)
VALUES ('11111111-1111-1111-1111-111111111111', 'Tech Convention Center', 'Austin', 5000);

INSERT INTO events (id, venue_id, name, start_time, status)
VALUES ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Distributed Systems Summit 2026', '2026-11-01 09:00:00+00', 'SCHEDULED');