-- Phase 1 · Delivery incidents.
--
-- A courier (or Sales) reports why a delivery or pickup could not be done, with a reason code and a note. The
-- remittance keeps the latest open incident until someone acts on it (reassign, postpone, complete or any status
-- change), and every incident stays in the history.

ALTER TABLE remittances ADD COLUMN open_incident_reason VARCHAR(30);
ALTER TABLE remittances ADD COLUMN open_incident_note VARCHAR(1000);
ALTER TABLE remittances ADD COLUMN open_incident_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE remittances ADD COLUMN open_incident_by UUID;
ALTER TABLE remittances ADD CONSTRAINT ck_remittances_incident_reason
    CHECK (open_incident_reason IN ('NOT_HOME', 'WRONG_ADDRESS', 'UNREACHABLE', 'REFUSED', 'OTHER'));

CREATE INDEX ix_remittances_open_incident ON remittances (open_incident_at);

ALTER TABLE remittance_events ADD COLUMN incident_reason VARCHAR(30);

INSERT INTO permissions (code, module, description) VALUES
    ('remittances:report-incident', 'remittances',
     'Report why a delivery or pickup could not be done; couriers only on their own remittances');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'remittances:report-incident' FROM roles r WHERE r.code IN ('ADMIN', 'SALES', 'DELIVERY');
