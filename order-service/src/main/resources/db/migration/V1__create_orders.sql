-- Phase 1 walking skeleton: minimal orders table.
-- Full schema (order_items, payments, idempotency_keys, outbox) arrives in Phase 4.
CREATE TABLE orders (
    id          UUID         PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    status      VARCHAR(30)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
