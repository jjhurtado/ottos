-- Phase 1 · Rates and fees.
-- A corridor is a currency pair (USD-CUP). Exchange rates and fee rules are never edited: each change inserts a new
-- row with its valid_from, and every remittance keeps a copy of the values it used.

CREATE TABLE corridors (
    code              VARCHAR(7)     PRIMARY KEY,
    source_currency   VARCHAR(3)     NOT NULL,
    target_currency   VARCHAR(3)     NOT NULL,
    -- amount to deliver is rounded down to a multiple of this (50 CUP; 0.01 means no rounding)
    delivery_rounding NUMERIC(19, 2) NOT NULL,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_corridors_pair UNIQUE (source_currency, target_currency),
    CONSTRAINT ck_corridors_rounding CHECK (delivery_rounding > 0)
);

CREATE TABLE exchange_rates (
    id            UUID PRIMARY KEY,
    corridor_code VARCHAR(7)               NOT NULL,
    rate          NUMERIC(19, 6)           NOT NULL,
    valid_from    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    UUID,
    CONSTRAINT fk_exchange_rates_corridor FOREIGN KEY (corridor_code) REFERENCES corridors (code),
    CONSTRAINT ck_exchange_rates_rate CHECK (rate > 0)
);

CREATE INDEX ix_exchange_rates_corridor ON exchange_rates (corridor_code, valid_from);

CREATE TABLE fee_rules (
    id            UUID PRIMARY KEY,
    corridor_code VARCHAR(7)               NOT NULL,
    -- PERCENTAGE: value is a percent of the amount; FIXED: value is the fee itself
    type          VARCHAR(20)              NOT NULL,
    fee_value     NUMERIC(19, 4)           NOT NULL,
    min_fee       NUMERIC(19, 2),
    max_fee       NUMERIC(19, 2),
    valid_from    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    UUID,
    CONSTRAINT fk_fee_rules_corridor FOREIGN KEY (corridor_code) REFERENCES corridors (code),
    CONSTRAINT ck_fee_rules_type CHECK (type IN ('PERCENTAGE', 'FIXED')),
    CONSTRAINT ck_fee_rules_value CHECK (fee_value >= 0)
);

CREATE INDEX ix_fee_rules_corridor ON fee_rules (corridor_code, valid_from);

INSERT INTO corridors (code, source_currency, target_currency, delivery_rounding) VALUES
    ('USD-CUP', 'USD', 'CUP', 50),
    ('USD-USD', 'USD', 'USD', 0.01);

-- Same currency: the rate is always 1. USD-CUP has no rate until an administrator sets one.
INSERT INTO exchange_rates (id, corridor_code, rate, valid_from) VALUES
    ('00000000-0000-0000-0001-000000000001', 'USD-USD', 1, CURRENT_TIMESTAMP);

-- 10 % of the amount, at least 10 USD.
INSERT INTO fee_rules (id, corridor_code, type, fee_value, min_fee, valid_from) VALUES
    ('00000000-0000-0000-0002-000000000001', 'USD-CUP', 'PERCENTAGE', 10, 10, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0002-000000000002', 'USD-USD', 'PERCENTAGE', 10, 10, CURRENT_TIMESTAMP);

INSERT INTO permissions (code, module, description) VALUES
    ('rates:read',  'rates', 'View corridors, exchange rates and fee rules; calculate quotes'),
    ('rates:write', 'rates', 'Set exchange rates and fee rules');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE p.code IN ('rates:read', 'rates:write') AND r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'rates:read' FROM roles r WHERE r.code = 'SALES';
