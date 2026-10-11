-- Phase 1 · Audit log: who did what and when.
--
-- Modules publish an AuditEvent for every change that must leave a trace (today: staff users and roles, own password
-- changes and the initial administrator); the reporting module stores it here in the same transaction. Entries are
-- never edited nor deleted. details is a JSON object with what changed; it never contains secrets.

CREATE TABLE audit_log (
    id           UUID PRIMARY KEY,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_id     UUID,
    action       VARCHAR(50)              NOT NULL,
    target_type  VARCHAR(30)              NOT NULL,
    target_id    VARCHAR(100)             NOT NULL,
    target_label VARCHAR(255),
    details      TEXT                     NOT NULL
);

CREATE INDEX ix_audit_log_occurred ON audit_log (occurred_at);
CREATE INDEX ix_audit_log_target ON audit_log (target_type, target_id, occurred_at);
CREATE INDEX ix_audit_log_actor ON audit_log (actor_id, occurred_at);

INSERT INTO permissions (code, module, description) VALUES
    ('audit:read', 'reporting', 'View the audit log: who changed users, roles and other audited records, and when');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'audit:read' FROM roles r WHERE r.code = 'ADMIN';
