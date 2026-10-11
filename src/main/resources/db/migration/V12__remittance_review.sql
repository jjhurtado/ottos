-- Phase 1 · Remittances that need review, and change tracking for the mobile app.
--
-- Some actions synced from the mobile app are applied but need Sales to check them (a delivery after a reassignment,
-- a wrong PIN offline, a duplicate delivery, a very late sync): the remittance keeps the open review until someone
-- resolves it with a note. updated_at lets the app download only what changed since its last sync.

ALTER TABLE remittances ADD COLUMN review_reason VARCHAR(40);
ALTER TABLE remittances ADD COLUMN review_flagged_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE remittances ADD COLUMN review_action_id UUID;
ALTER TABLE remittances ADD CONSTRAINT ck_remittances_review_reason
    CHECK (review_reason IN ('DELIVERED_AFTER_REASSIGNMENT', 'PIN_MISMATCH', 'DUPLICATE_DELIVERY', 'LATE_SYNC'));
CREATE INDEX ix_remittances_review ON remittances (review_flagged_at);

ALTER TABLE remittances ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
UPDATE remittances SET updated_at = COALESCE(completed_at, created_at);
ALTER TABLE remittances ALTER COLUMN updated_at SET NOT NULL;
CREATE INDEX ix_remittances_courier_updated ON remittances (courier_id, updated_at);

ALTER TABLE remittance_events ADD COLUMN review_reason VARCHAR(40);

INSERT INTO permissions (code, module, description) VALUES
    ('remittances:review', 'remittances', 'Resolve remittances flagged for review after a sync from the mobile app');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'remittances:review' FROM roles r WHERE r.code IN ('ADMIN', 'SALES');
