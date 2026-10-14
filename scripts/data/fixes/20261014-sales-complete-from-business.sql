-- Grants Sales the new permission remittances:complete-from-business (backend migration V2): Sales marks a
-- remittance delivered or collected without a courier and the business cash moves. Requested by the user on
-- 2026-10-14. New installations get it from seed/cuba-la-habana.sql; this is for the ones already set up (staging,
-- production). Run it after the backend with V2 is deployed: the permission must exist.

INSERT INTO identity.role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM identity.roles r CROSS JOIN identity.permissions p
WHERE r.code = 'SALES' AND p.code = 'remittances:complete-from-business'
ON CONFLICT DO NOTHING;
