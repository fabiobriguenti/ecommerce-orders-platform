-- Phase 4: full transactional schema.

-- Order total is frozen at confirmation time.
ALTER TABLE orders
    ADD COLUMN total_amount   NUMERIC(19, 2),
    ADD COLUMN total_currency VARCHAR(3);

-- At most one active (non-terminal) order per customer (strong guarantee under concurrency).
CREATE UNIQUE INDEX uq_active_order_per_customer
    ON orders (customer_id)
    WHERE status NOT IN ('PAID', 'CANCELLED');

CREATE TABLE order_items (
    id                  UUID         PRIMARY KEY,
    order_id            UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id          VARCHAR(100) NOT NULL,
    quantity            INTEGER      NOT NULL,
    unit_price_amount   NUMERIC(19, 2),
    unit_price_currency VARCHAR(3)
);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE UNIQUE INDEX uq_order_items_order_product ON order_items (order_id, product_id);

CREATE TABLE payments (
    id         UUID         PRIMARY KEY,
    order_id   UUID         NOT NULL UNIQUE REFERENCES orders (id),
    amount     NUMERIC(19, 2) NOT NULL,
    currency   VARCHAR(3)   NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    attempts   INTEGER      NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Transactional outbox (ADR-04): domain events written with the aggregate change.
CREATE TABLE outbox (
    id           UUID        PRIMARY KEY,
    aggregate_id UUID        NOT NULL,
    type         VARCHAR(60) NOT NULL,
    payload      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

-- Idempotency-Key store for mutating endpoints (consumed by the web filter in Phase 6).
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    request_hash    VARCHAR(128) NOT NULL,
    response_status INTEGER      NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
