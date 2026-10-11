-- Phase 1 · Actions synced from the mobile app.
--
-- Every action applied from the app's offline queue is recorded with the id the phone generated, in the same
-- transaction as its effect: sending it again returns ALREADY_APPLIED instead of applying it twice. Rejected actions
-- are not recorded, so the app can retry them.

CREATE TABLE synced_actions (
    id            UUID PRIMARY KEY,
    user_id       UUID                     NOT NULL,
    type          VARCHAR(30)              NOT NULL,
    remittance_id UUID                     NOT NULL,
    occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    device_id     VARCHAR(100),
    CONSTRAINT fk_synced_actions_remittance FOREIGN KEY (remittance_id) REFERENCES remittances (id),
    CONSTRAINT ck_synced_actions_type CHECK (type IN ('DELIVER', 'DELIVER_WITH_PIN', 'POSTPONE', 'REPORT_INCIDENT'))
);

CREATE INDEX ix_synced_actions_remittance ON synced_actions (remittance_id);
