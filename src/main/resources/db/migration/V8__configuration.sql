-- Phase 1 · Configuration module.
--
-- Every configurable value of the business lives in the configuration module and is managed from one place:
-- corridors, exchange rates and fee rules (tables unchanged, now owned by configuration) and the settings below.
-- The remittances module no longer keeps its own settings table.

CREATE TABLE settings (
    setting_key   VARCHAR(50)              PRIMARY KEY,
    setting_value VARCHAR(255)             NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    UUID
);

-- minimum_amount: smallest amount a remittance can send, in USD (0 = no minimum until an administrator sets one)
INSERT INTO settings (setting_key, setting_value) VALUES ('minimum_amount', '0');

INSERT INTO settings (setting_key, setting_value)
SELECT setting_key, setting_value FROM remittance_settings WHERE setting_key = 'default_delivery_days';

DROP TABLE remittance_settings;

-- configuration:read / configuration:write replace rates:write and the viewing part of rates:read.
-- Every role keeps what it could do: whoever could read rates can read the configuration, whoever could set them
-- can change it. rates:read stays for calculating quotes.
INSERT INTO permissions (code, module, description) VALUES
    ('configuration:read',  'configuration', 'View the configuration: minimum amount, delivery days, corridors, exchange rates and fee rules'),
    ('configuration:write', 'configuration', 'Change the configuration: minimum amount, delivery days, exchange rates and fee rules');

INSERT INTO role_permissions (role_id, permission_code)
SELECT role_id, 'configuration:read' FROM role_permissions WHERE permission_code = 'rates:read';

INSERT INTO role_permissions (role_id, permission_code)
SELECT role_id, 'configuration:write' FROM role_permissions WHERE permission_code = 'rates:write';

DELETE FROM role_permissions WHERE permission_code = 'rates:write';
DELETE FROM permissions WHERE code = 'rates:write';

UPDATE permissions SET description = 'Calculate quotes' WHERE code = 'rates:read';
