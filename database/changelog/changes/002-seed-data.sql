--liquibase formatted sql

--changeset warehouse:002-seed-products
INSERT INTO products (sku, name, description) VALUES
    ('A100', 'Product A100', 'Sample product A100'),
    ('B200', 'Product B200', 'Sample product B200');

--changeset warehouse:002-seed-inventory
INSERT INTO inventory (sku, total_stock, available_stock, reserved_stock, version) VALUES
    ('A100', 100, 100, 0, 0),
    ('B200', 50, 50, 0, 0);
