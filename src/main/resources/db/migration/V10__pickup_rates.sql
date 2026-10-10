-- Phase 1 · Pickups have their own exchange rate and fee rule.
--
-- Every exchange rate and fee rule now applies to one kind of remittance: DELIVERY or PICKUP. The existing rows are
-- the delivery ones. Each corridor starts with a pickup rate of 1 (1x1) and a pickup fee rule equal to its delivery
-- rule in force; administrators change them from the configuration.

ALTER TABLE exchange_rates ADD COLUMN remittance_type VARCHAR(20) NOT NULL DEFAULT 'DELIVERY';
ALTER TABLE exchange_rates ADD CONSTRAINT ck_exchange_rates_remittance_type CHECK (remittance_type IN ('DELIVERY', 'PICKUP'));
DROP INDEX ix_exchange_rates_corridor;
CREATE INDEX ix_exchange_rates_corridor ON exchange_rates (corridor_code, remittance_type, valid_from);

ALTER TABLE fee_rules ADD COLUMN remittance_type VARCHAR(20) NOT NULL DEFAULT 'DELIVERY';
ALTER TABLE fee_rules ADD CONSTRAINT ck_fee_rules_remittance_type CHECK (remittance_type IN ('DELIVERY', 'PICKUP'));
DROP INDEX ix_fee_rules_corridor;
CREATE INDEX ix_fee_rules_corridor ON fee_rules (corridor_code, remittance_type, valid_from);

INSERT INTO exchange_rates (id, corridor_code, remittance_type, rate, valid_from) VALUES
    ('00000000-0000-0000-0001-000000000002', 'USD-CUP', 'PICKUP', 1, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0001-000000000003', 'USD-USD', 'PICKUP', 1, CURRENT_TIMESTAMP);

INSERT INTO fee_rules (id, corridor_code, remittance_type, type, fee_value, min_fee, max_fee, valid_from)
SELECT '00000000-0000-0000-0002-000000000003', corridor_code, 'PICKUP', type, fee_value, min_fee, max_fee, CURRENT_TIMESTAMP
FROM fee_rules
WHERE corridor_code = 'USD-CUP' AND remittance_type = 'DELIVERY'
  AND valid_from = (SELECT MAX(valid_from) FROM fee_rules WHERE corridor_code = 'USD-CUP' AND remittance_type = 'DELIVERY');

INSERT INTO fee_rules (id, corridor_code, remittance_type, type, fee_value, min_fee, max_fee, valid_from)
SELECT '00000000-0000-0000-0002-000000000004', corridor_code, 'PICKUP', type, fee_value, min_fee, max_fee, CURRENT_TIMESTAMP
FROM fee_rules
WHERE corridor_code = 'USD-USD' AND remittance_type = 'DELIVERY'
  AND valid_from = (SELECT MAX(valid_from) FROM fee_rules WHERE corridor_code = 'USD-USD' AND remittance_type = 'DELIVERY');
