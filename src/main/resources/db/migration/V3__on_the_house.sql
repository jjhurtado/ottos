-- Remittances "on the house": deliveries the business sends free of fee (family, people the business chooses).
-- The fee is 0 and the total is the amount; waived_fee keeps the fee the rule would have charged and the reason is
-- mandatory. Only deliveries (a pickup can carry a fee of 0 entered by hand). Set when registering, never changed
-- afterwards. Contract: docs/contracts/on-the-house.md.
--
-- Existing remittances get on_the_house = FALSE and NULL in the other two columns, which satisfies every CHECK.
-- Adding a NOT NULL column with a constant default does not rewrite the table (PostgreSQL 11+); the CHECKs scan it
-- once while holding the lock. lock_timeout makes the migration fail fast instead of queueing behind a long
-- transaction and blocking every request behind it.

SET LOCAL lock_timeout = '10s';

ALTER TABLE remittances.remittances
    ADD COLUMN on_the_house        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN waived_fee          NUMERIC(19, 2),
    ADD COLUMN on_the_house_reason VARCHAR(500);

ALTER TABLE remittances.remittances
    ADD CONSTRAINT ck_remittances_on_the_house_delivery
        CHECK (NOT on_the_house OR (type = 'DELIVERY' AND fee = 0 AND total = amount)),
    ADD CONSTRAINT ck_remittances_on_the_house_waived_fee
        CHECK ((on_the_house AND waived_fee IS NOT NULL AND waived_fee >= 0)
            OR (NOT on_the_house AND waived_fee IS NULL)),
    ADD CONSTRAINT ck_remittances_on_the_house_reason
        CHECK ((on_the_house AND on_the_house_reason IS NOT NULL AND length(btrim(on_the_house_reason)) > 0)
            OR (NOT on_the_house AND on_the_house_reason IS NULL));

-- Granted to ADMIN like every permission; other roles get it from Roles (approved by the user: no data script).
INSERT INTO identity.permissions (code, module, description) VALUES
    ('remittances:on-the-house', 'remittances',
     'Register deliveries on the house: free of fee, with a mandatory reason');

INSERT INTO identity.role_permissions (role_id, permission_code)
SELECT r.id, 'remittances:on-the-house' FROM identity.roles r WHERE r.code = 'ADMIN';
