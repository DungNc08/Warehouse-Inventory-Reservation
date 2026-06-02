--liquibase formatted sql

--changeset warehouse:001-create-products
CREATE TABLE products (
    sku         VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT
);

--changeset warehouse:001-create-inventory
CREATE TABLE inventory (
    sku              VARCHAR(64) PRIMARY KEY REFERENCES products (sku),
    total_stock      INTEGER     NOT NULL CHECK (total_stock >= 0),
    available_stock  INTEGER     NOT NULL CHECK (available_stock >= 0),
    reserved_stock   INTEGER     NOT NULL CHECK (reserved_stock >= 0),
    version          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT inventory_stock_balance CHECK (available_stock + reserved_stock <= total_stock)
);

--changeset warehouse:001-create-reservations
CREATE TABLE reservations (
    id         UUID         PRIMARY KEY,
    order_id   VARCHAR(64)  NOT NULL UNIQUE,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reservations_status ON reservations (status);

--changeset warehouse:001-create-reservation-items
CREATE TABLE reservation_items (
    id             BIGSERIAL    PRIMARY KEY,
    reservation_id UUID         NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    sku            VARCHAR(64)  NOT NULL REFERENCES products (sku),
    quantity       INTEGER      NOT NULL CHECK (quantity > 0),
    UNIQUE (reservation_id, sku)
);

CREATE INDEX idx_reservation_items_sku ON reservation_items (sku);
