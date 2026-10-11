-- Customer import and export (contract: docs/contracts/customer-transfer.md).
--
-- A person with two addresses is two customers with the same name and phone (there is no multi-address model), so the
-- phone and the document stop being unique. Only an identical customer (same name, phone and address) is rejected,
-- by the application.

ALTER TABLE customers DROP CONSTRAINT uk_customers_phone;
ALTER TABLE customers DROP CONSTRAINT uk_customers_document;
CREATE INDEX ix_customers_phone ON customers (phone);

INSERT INTO permissions (code, module, description) VALUES
    ('customers:export', 'customers', 'Export every customer with their beneficiaries (personal data)'),
    ('customers:import', 'customers', 'Import customers and beneficiaries');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND p.code IN ('customers:export', 'customers:import');
