CREATE TABLE orders (
    id               VARCHAR(36) PRIMARY KEY,
    reservation_id   VARCHAR(64) NOT NULL,
    event_id         VARCHAR(64) NOT NULL,
    seat_id          VARCHAR(64) NOT NULL,
    user_id          VARCHAR(64) NOT NULL,
    amount           NUMERIC(10, 2) NOT NULL,
    idempotency_key  VARCHAR(64) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP,
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_orders_reservation_id ON orders (reservation_id);
CREATE INDEX idx_orders_user_id ON orders (user_id);
