-- Voiding remittances and registering old ones by hand (contract: docs/contracts/void-and-historical.md).
--
-- A voided remittance is kept, with who voided it, when and why: it leaves the reports, statistics, work lists, the
-- completed feed and the mobile sync, and any cash it moved is reversed by REMITTANCE_VOID_REVERSAL movements.

ALTER TABLE remittances ADD COLUMN voided_at   TIMESTAMP WITH TIME ZONE;
ALTER TABLE remittances ADD COLUMN voided_by   UUID;
ALTER TABLE remittances ADD COLUMN void_reason VARCHAR(1000);

INSERT INTO permissions (code, module, description) VALUES
    ('remittances:void',       'remittances', 'Void a remittance: it stops counting and the cash it moved is reversed'),
    ('remittances:historical', 'remittances', 'Register old remittances and pickups, delivered on their date');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND p.code IN ('remittances:void', 'remittances:historical');
