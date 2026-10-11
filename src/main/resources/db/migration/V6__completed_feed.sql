-- Feed of completed remittances for Sales' local notifications (contract: docs/contracts/completed-feed.md).
-- completed_at is when the delivery happened, which for a delivery synced from the mobile app can be hours before the
-- server learns of it. completion_recorded_at is the server's clock when the completion was recorded, so a feed
-- polled "since" a server time never misses a late sync.

ALTER TABLE remittances ADD COLUMN completion_recorded_at TIMESTAMP WITH TIME ZONE;
UPDATE remittances SET completion_recorded_at = completed_at WHERE completed_at IS NOT NULL;
CREATE INDEX ix_remittances_completion_recorded ON remittances (completion_recorded_at)
    WHERE completion_recorded_at IS NOT NULL;
