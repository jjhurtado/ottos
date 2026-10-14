-- Sales or Admin mark a remittance delivered (or collected) without a courier: the business cash box hands over or
-- receives the money (contract: docs/contracts/complete-from-business.md). Granted to ADMIN like every permission;
-- each installation grants it to its roles in its setup script.

INSERT INTO identity.permissions (code, module, description) VALUES
    ('remittances:complete-from-business', 'remittances',
     'Mark remittances as delivered or collected by the business, without a courier: the business cash moves');

INSERT INTO identity.role_permissions (role_id, permission_code)
SELECT r.id, 'remittances:complete-from-business' FROM identity.roles r WHERE r.code = 'ADMIN';
