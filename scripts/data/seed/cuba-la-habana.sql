-- Installation setup: Ottos in La Habana, Cuba (the current business). Run ONCE per environment, right after the first
-- start of the backend has created the schemas (Flyway V1), and before loading any data. Idempotent: running it again
-- changes nothing. Another country or currency is another setup script; the schema and the code stay the same.
--
-- It sets: the business currency and the phone rule, the zones where the business delivers, the currency corridors
-- with their initial rates and fee rules, and the SALES and DELIVERY roles with their initial permissions. The ids are
-- fixed so that other scripts (e.g. the staging test users) can refer to them.

-- ---------------------------------------------------------------------------------------------------------- settings
-- The business charges in USD and keeps its accounts in USD. Cuban phone numbers: 8 digits after the country code.
INSERT INTO configuration.settings (setting_key, setting_value) VALUES
    ('business_currency', 'USD'),
    ('phone_match_digits', '8')
ON CONFLICT (setting_key) DO NOTHING;

-- ------------------------------------------------------------------------------------------------------------- zones
-- Official codes of the political-administrative division of Cuba. Only La Habana for now.
INSERT INTO branches.provinces (code, name) VALUES ('23', 'La Habana')
ON CONFLICT (code) DO NOTHING;

INSERT INTO branches.municipalities (code, province_code, name) VALUES
    ('2301', '23', 'Playa'),
    ('2302', '23', 'Plaza de la Revolución'),
    ('2303', '23', 'Centro Habana'),
    ('2304', '23', 'La Habana Vieja'),
    ('2305', '23', 'Regla'),
    ('2306', '23', 'La Habana del Este'),
    ('2307', '23', 'Guanabacoa'),
    ('2308', '23', 'San Miguel del Padrón'),
    ('2309', '23', 'Diez de Octubre'),
    ('2310', '23', 'Cerro'),
    ('2311', '23', 'Marianao'),
    ('2312', '23', 'La Lisa'),
    ('2313', '23', 'Boyeros'),
    ('2314', '23', 'Arroyo Naranjo'),
    ('2315', '23', 'Cotorro')
ON CONFLICT (code) DO NOTHING;

-- --------------------------------------------------------------------------------------------- corridors, rates, fees
-- Amounts in CUP are rounded down to multiples of 50; in USD, to the cent.
INSERT INTO configuration.corridors (code, source_currency, target_currency, delivery_rounding) VALUES
    ('USD-CUP', 'USD', 'CUP', 50),
    ('USD-USD', 'USD', 'USD', 0.01)
ON CONFLICT (code) DO NOTHING;

-- Same currency: the rate is always 1. Pickups start at 1 (1x1). The USD-CUP delivery rate has no value until an
-- administrator sets one.
INSERT INTO configuration.exchange_rates (id, corridor_code, remittance_type, rate, valid_from) VALUES
    ('00000000-0000-0000-0001-000000000001', 'USD-USD', 'DELIVERY', 1, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0001-000000000002', 'USD-CUP', 'PICKUP',   1, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0001-000000000003', 'USD-USD', 'PICKUP',   1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 10 % of the amount, at least 10 USD, for deliveries and pickups alike until an administrator changes them.
INSERT INTO configuration.fee_rules (id, corridor_code, remittance_type, type, fee_value, min_fee, valid_from) VALUES
    ('00000000-0000-0000-0002-000000000001', 'USD-CUP', 'DELIVERY', 'PERCENTAGE', 10, 10, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0002-000000000002', 'USD-USD', 'DELIVERY', 'PERCENTAGE', 10, 10, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0002-000000000003', 'USD-CUP', 'PICKUP',   'PERCENTAGE', 10, 10, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0002-000000000004', 'USD-USD', 'PICKUP',   'PERCENTAGE', 10, 10, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- -------------------------------------------------------------------------------------------------------------- roles
-- Editable through the API afterwards. Names are in English: the frontend translates by code.
INSERT INTO identity.roles (id, code, name, description, built_in, created_at) VALUES
    ('00000000-0000-0000-0000-000000000002', 'SALES',    'Sales',    'Registers and charges remittances',      FALSE, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'DELIVERY', 'Delivery', 'Delivers remittances to beneficiaries', FALSE, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

INSERT INTO identity.role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM identity.roles r CROSS JOIN identity.permissions p
WHERE r.code = 'SALES' AND p.code IN (
    'customers:read', 'customers:write',
    'configuration:read', 'rates:read',
    'remittances:read', 'remittances:create', 'remittances:assign', 'remittances:postpone',
    'remittances:report-incident', 'remittances:review',
    'cash:read', 'cash:write', 'cash:review',
    'reports:read')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM identity.roles r CROSS JOIN identity.permissions p
WHERE r.code = 'DELIVERY' AND p.code IN (
    'remittances:read-assigned', 'remittances:deliver', 'remittances:postpone', 'remittances:report-incident',
    'cash:read-own', 'cash:exchange-own')
ON CONFLICT DO NOTHING;
