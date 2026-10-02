-- Phase 1 · Remittances: deliveries and pickups, statuses and transitions as data, due dates and delays.
--
-- Statuses are data. The code only relies on their flags:
--   is_initial       a new remittance starts here (exactly one status)
--   requires_courier moving here assigns a courier
--   is_final         the remittance is completed: the courier's cash moves and it can no longer be late
-- Transitions say which moves are allowed and which permission each needs.

CREATE TABLE remittance_statuses (
    code             VARCHAR(30)  PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    position         INT          NOT NULL,
    is_initial       BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_courier BOOLEAN      NOT NULL DEFAULT FALSE,
    is_final         BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE remittance_transitions (
    from_status     VARCHAR(30)  NOT NULL,
    to_status       VARCHAR(30)  NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    CONSTRAINT pk_remittance_transitions PRIMARY KEY (from_status, to_status),
    CONSTRAINT fk_remittance_transitions_from FOREIGN KEY (from_status) REFERENCES remittance_statuses (code),
    CONSTRAINT fk_remittance_transitions_to FOREIGN KEY (to_status) REFERENCES remittance_statuses (code),
    CONSTRAINT fk_remittance_transitions_permission FOREIGN KEY (permission_code) REFERENCES permissions (code)
);

CREATE TABLE remittance_settings (
    setting_key   VARCHAR(50)  PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL
);

CREATE TABLE remittances (
    id                            UUID PRIMARY KEY,
    code                          VARCHAR(12)              NOT NULL,
    type                          VARCHAR(20)              NOT NULL,
    status                        VARCHAR(30)              NOT NULL,
    customer_id                   UUID                     NOT NULL,
    beneficiary_id                UUID                     NOT NULL,
    -- copy of the beneficiary at creation: later edits don't change past remittances
    beneficiary_name              VARCHAR(150)             NOT NULL,
    beneficiary_phone             VARCHAR(30)              NOT NULL,
    beneficiary_alternate_phone   VARCHAR(30),
    beneficiary_address           VARCHAR(500)             NOT NULL,
    beneficiary_municipality_code VARCHAR(4)               NOT NULL,
    beneficiary_reference         VARCHAR(255),
    -- copy of the quote at creation
    corridor_code                 VARCHAR(7)               NOT NULL,
    source_currency               VARCHAR(3)               NOT NULL,
    target_currency               VARCHAR(3)               NOT NULL,
    amount                        NUMERIC(19, 2)           NOT NULL,
    fee                           NUMERIC(19, 2)           NOT NULL,
    total                         NUMERIC(19, 2)           NOT NULL,
    rate                          NUMERIC(19, 6)           NOT NULL,
    amount_to_deliver             NUMERIC(19, 2)           NOT NULL,
    exchange_rate_id              UUID                     NOT NULL,
    fee_rule_id                   UUID                     NOT NULL,
    pin                           VARCHAR(6)               NOT NULL,
    courier_id                    UUID,
    expected_date                 DATE                     NOT NULL,
    postponements                 INT                      NOT NULL DEFAULT 0,
    notes                         VARCHAR(1000),
    idempotency_key               VARCHAR(100),
    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by                    UUID                     NOT NULL,
    completed_at                  TIMESTAMP WITH TIME ZONE,
    completed_by                  UUID,
    version                       BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT uk_remittances_code UNIQUE (code),
    CONSTRAINT uk_remittances_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_remittances_type CHECK (type IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT fk_remittances_status FOREIGN KEY (status) REFERENCES remittance_statuses (code),
    CONSTRAINT fk_remittances_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_remittances_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries (id),
    CONSTRAINT fk_remittances_municipality FOREIGN KEY (beneficiary_municipality_code) REFERENCES municipalities (code),
    CONSTRAINT fk_remittances_corridor FOREIGN KEY (corridor_code) REFERENCES corridors (code),
    CONSTRAINT fk_remittances_courier FOREIGN KEY (courier_id) REFERENCES users (id)
);

CREATE INDEX ix_remittances_status ON remittances (status, expected_date);
CREATE INDEX ix_remittances_customer ON remittances (customer_id, created_at);
CREATE INDEX ix_remittances_beneficiary ON remittances (beneficiary_id, created_at);
CREATE INDEX ix_remittances_courier ON remittances (courier_id, status);

CREATE TABLE remittance_events (
    id                UUID PRIMARY KEY,
    remittance_id     UUID                     NOT NULL,
    type              VARCHAR(30)              NOT NULL,
    from_status       VARCHAR(30),
    to_status         VARCHAR(30),
    courier_id        UUID,
    previous_date     DATE,
    new_date          DATE,
    note              VARCHAR(1000),
    actor_id          UUID                     NOT NULL,
    occurred_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_remittance_events_remittance FOREIGN KEY (remittance_id) REFERENCES remittances (id)
);

CREATE INDEX ix_remittance_events_remittance ON remittance_events (remittance_id, occurred_at);

-- Permissions
INSERT INTO permissions (code, module, description) VALUES
    ('remittances:read',            'remittances', 'View every remittance, its history and customer and beneficiary statistics'),
    ('remittances:read-assigned',   'remittances', 'View the remittances assigned to oneself'),
    ('remittances:read-financials', 'remittances', 'View fee, rate and totals of pickups'),
    ('remittances:create',          'remittances', 'Register paid remittances and pickups'),
    ('remittances:assign',          'remittances', 'Assign and reassign remittances to couriers'),
    ('remittances:deliver',         'remittances', 'Mark remittances as delivered or collected; required to be a courier'),
    ('remittances:postpone',        'remittances', 'Postpone the expected date of a remittance');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE p.module = 'remittances' AND r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE p.code IN ('remittances:read', 'remittances:create', 'remittances:assign', 'remittances:postpone')
  AND r.code = 'SALES';

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE p.code IN ('remittances:read-assigned', 'remittances:deliver', 'remittances:postpone')
  AND r.code = 'DELIVERY';

-- Initial workflow: Paid → Assigned (→ reassigned) → Delivered
INSERT INTO remittance_statuses (code, name, position, is_initial, requires_courier, is_final) VALUES
    ('PAID',      'Pagada',    10, TRUE,  FALSE, FALSE),
    ('ASSIGNED',  'Asignada',  20, FALSE, TRUE,  FALSE),
    ('DELIVERED', 'Entregada', 30, FALSE, FALSE, TRUE);

INSERT INTO remittance_transitions (from_status, to_status, permission_code) VALUES
    ('PAID',     'ASSIGNED',  'remittances:assign'),
    ('ASSIGNED', 'ASSIGNED',  'remittances:assign'),
    ('ASSIGNED', 'DELIVERED', 'remittances:deliver');

INSERT INTO remittance_settings (setting_key, setting_value) VALUES
    ('default_delivery_days', '2');
