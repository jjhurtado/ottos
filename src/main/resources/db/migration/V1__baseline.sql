-- Ottos · Baseline schema for version 1.0.
--
-- Built from every structural migration up to 1.0.0-rc.3, merged on 2026-10-13 while no environment had real
-- customers (staging and production were recreated from scratch). From now on this file is never edited: structural
-- changes go in new migrations (V2, V3…) and data operations in scripts/data, never in Flyway.
--
-- One schema per module, named like the module's package; each module only reads its own schema from code. Foreign
-- keys between schemas are kept on purpose: they protect integrity and match the dependencies between modules. The
-- public schema only holds Flyway's history.

CREATE SCHEMA identity;
CREATE SCHEMA branches;
CREATE SCHEMA customers;
CREATE SCHEMA beneficiaries;
CREATE SCHEMA configuration;
CREATE SCHEMA remittances;
CREATE SCHEMA payments;
CREATE SCHEMA reporting;
CREATE SCHEMA apps;

-- =====================================================================================================================
-- identity · staff users, dynamic roles and permissions (RBAC), refresh tokens
--
-- Permissions are the catalog the code checks (@PreAuthorize("hasAuthority('users:read')")); new ones arrive through
-- migrations together with the code that uses them, and are granted to ADMIN in the same migration. Roles and their
-- permissions are data: administrators manage them through the API.
-- =====================================================================================================================

CREATE TABLE identity.users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255)             NOT NULL,
    password_hash VARCHAR(100)             NOT NULL,
    name          VARCHAR(150)             NOT NULL,
    active        BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE identity.permissions (
    code        VARCHAR(100) PRIMARY KEY,
    module      VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE identity.roles (
    id          UUID PRIMARY KEY,
    code        VARCHAR(50)              NOT NULL,
    name        VARCHAR(100)             NOT NULL,
    description VARCHAR(255),
    built_in    BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_roles_code UNIQUE (code)
);

CREATE TABLE identity.role_permissions (
    role_id         UUID         NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES identity.roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_code) REFERENCES identity.permissions (code)
);

CREATE TABLE identity.user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES identity.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES identity.roles (id)
);

CREATE INDEX ix_user_roles_role ON identity.user_roles (role_id);

-- Opaque single-use refresh tokens, stored only as a SHA-256 hash. client: where the session was opened (WEB, or
-- MOBILE for the offline-first app, whose access tokens last a working day). replaced_by: the token issued when this
-- one was used, to tell a retry within ottos.security.refresh-reuse-grace from a stolen token.
CREATE TABLE identity.refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID                     NOT NULL,
    token_hash  VARCHAR(64)              NOT NULL,
    client      VARCHAR(10)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    replaced_by UUID,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_client CHECK (client IN ('WEB', 'MOBILE')),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES identity.users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_user ON identity.refresh_tokens (user_id);

INSERT INTO identity.permissions (code, module, description) VALUES
    ('users:read',  'identity', 'View staff users and their roles'),
    ('users:write', 'identity', 'Create, update, activate and deactivate staff users, assign roles, reset passwords'),
    ('roles:read',  'identity', 'View roles and the permission catalog'),
    ('roles:write', 'identity', 'Create, update and delete roles and their permissions');

-- ADMIN is built in: it cannot be edited or deleted and holds every permission (granted at the end of this file). The
-- other roles (e.g. SALES, DELIVERY) belong to each installation and come with its setup script (scripts/data/seed).
INSERT INTO identity.roles (id, code, name, description, built_in, created_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN', 'Administrator', 'Full access to the system', TRUE, CURRENT_TIMESTAMP);

-- =====================================================================================================================
-- branches · zones: provinces and municipalities where the business delivers, with their official codes
--
-- The zones are data of each installation (scripts/data/seed), not of the schema. A single branch in 1.0, so no
-- branch tables yet.
-- =====================================================================================================================

CREATE TABLE branches.provinces (
    code   VARCHAR(20)  PRIMARY KEY,
    name   VARCHAR(100) NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE branches.municipalities (
    code          VARCHAR(20)  PRIMARY KEY,
    province_code VARCHAR(20)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_municipalities_province FOREIGN KEY (province_code) REFERENCES branches.provinces (code)
);


-- =====================================================================================================================
-- customers · remittance senders, kept apart from staff users
--
-- They have no login yet; customer sign-up (Phase 2) will add its own credentials table instead of touching users.
-- Phones are stored normalized (digits only, with an optional leading +). Neither the phone nor the document is unique:
-- a person with two addresses is two customers with the same name and phone (there is no multi-address model). Only
-- an identical customer (same name, phone and address) is rejected, by the application.
-- =====================================================================================================================

CREATE TABLE customers.customers (
    id              UUID PRIMARY KEY,
    full_name       VARCHAR(150)             NOT NULL,
    phone           VARCHAR(30)              NOT NULL,
    email           VARCHAR(255),
    document_type   VARCHAR(20),
    document_number VARCHAR(50),
    address         VARCHAR(255),
    notes           VARCHAR(1000),
    active          BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      UUID
);

CREATE INDEX ix_customers_phone ON customers.customers (phone);

INSERT INTO identity.permissions (code, module, description) VALUES
    ('customers:read',  'customers', 'View customers and beneficiaries'),
    ('customers:write', 'customers', 'Create and update customers and beneficiaries, link beneficiaries to customers'),
    ('customers:export', 'customers', 'Export every customer with their beneficiaries (personal data)'),
    ('customers:import', 'customers', 'Import customers and beneficiaries');

-- =====================================================================================================================
-- beneficiaries · the people who receive (or hand over, in pickups) the money
--
-- A beneficiary is one person, shared by every customer that sends to them (customer_beneficiaries). A beneficiary can
-- have no customer yet: they are linked when a customer sends them a remittance. Deleting one only deactivates it,
-- because remittances point at it.
-- =====================================================================================================================

CREATE TABLE beneficiaries.beneficiaries (
    id                UUID PRIMARY KEY,
    full_name         VARCHAR(150)             NOT NULL,
    phone             VARCHAR(30)              NOT NULL,
    alternate_phone   VARCHAR(30),
    street            VARCHAR(150)             NOT NULL,
    house_number      VARCHAR(30),
    between_streets   VARCHAR(150),
    neighborhood      VARCHAR(100),
    municipality_code VARCHAR(20)              NOT NULL,
    reference         VARCHAR(255),
    document_number   VARCHAR(50),
    notes             VARCHAR(1000),
    active            BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by        UUID,
    CONSTRAINT fk_beneficiaries_municipality FOREIGN KEY (municipality_code) REFERENCES branches.municipalities (code)
);

CREATE INDEX ix_beneficiaries_phone ON beneficiaries.beneficiaries (phone);
CREATE INDEX ix_beneficiaries_municipality ON beneficiaries.beneficiaries (municipality_code);

CREATE TABLE beneficiaries.customer_beneficiaries (
    customer_id    UUID                     NOT NULL,
    beneficiary_id UUID                     NOT NULL,
    linked_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_customer_beneficiaries PRIMARY KEY (customer_id, beneficiary_id),
    CONSTRAINT fk_customer_beneficiaries_customer FOREIGN KEY (customer_id) REFERENCES customers.customers (id),
    CONSTRAINT fk_customer_beneficiaries_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries.beneficiaries (id)
);

CREATE INDEX ix_customer_beneficiaries_beneficiary ON beneficiaries.customer_beneficiaries (beneficiary_id);

-- =====================================================================================================================
-- configuration · every value the business can change without a deploy, managed from one place
--
-- Settings, and per corridor (currency pair such as USD-CUP) its exchange rates and fee rules, one history for
-- deliveries and another for pickups. Rates and fee rules are never edited: each change inserts a new row with its
-- valid_from, and every remittance keeps a copy of the values it used. Concrete values (e.g. the minimum amount) are
-- set through the API, never in migrations.
-- =====================================================================================================================

-- business_currency: the currency the business charges in and keeps its accounts in (e.g. USD); set by the
--   installation's setup script and never changed afterwards
-- phone_match_digits: trailing digits that identify a phone when matching old records (8 in Cuba); set by the setup
--   script
-- minimum_amount: smallest amount a remittance can send, in the business currency (0 = no minimum)
-- default_delivery_days: days from registration to the expected delivery date
-- exchange_rate_tolerance_percent: a courier exchange whose rate strays more than this from the configured delivery
--   rate is recorded but flagged for review
CREATE TABLE configuration.settings (
    setting_key   VARCHAR(50)              PRIMARY KEY,
    setting_value VARCHAR(255)             NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    UUID
);

CREATE TABLE configuration.corridors (
    code              VARCHAR(7)     PRIMARY KEY,
    source_currency   VARCHAR(3)     NOT NULL,
    target_currency   VARCHAR(3)     NOT NULL,
    -- amount to deliver is rounded down to a multiple of this (50 CUP; 0.01 means no rounding)
    delivery_rounding NUMERIC(19, 2) NOT NULL,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_corridors_pair UNIQUE (source_currency, target_currency),
    CONSTRAINT ck_corridors_rounding CHECK (delivery_rounding > 0)
);

CREATE TABLE configuration.exchange_rates (
    id              UUID PRIMARY KEY,
    corridor_code   VARCHAR(7)               NOT NULL,
    remittance_type VARCHAR(20)              NOT NULL,
    rate            NUMERIC(19, 6)           NOT NULL,
    valid_from      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      UUID,
    CONSTRAINT fk_exchange_rates_corridor FOREIGN KEY (corridor_code) REFERENCES configuration.corridors (code),
    CONSTRAINT ck_exchange_rates_rate CHECK (rate > 0),
    CONSTRAINT ck_exchange_rates_remittance_type CHECK (remittance_type IN ('DELIVERY', 'PICKUP'))
);

CREATE INDEX ix_exchange_rates_corridor ON configuration.exchange_rates (corridor_code, remittance_type, valid_from);

CREATE TABLE configuration.fee_rules (
    id              UUID PRIMARY KEY,
    corridor_code   VARCHAR(7)               NOT NULL,
    remittance_type VARCHAR(20)              NOT NULL,
    -- PERCENTAGE: value is a percent of the amount; FIXED: value is the fee itself
    type            VARCHAR(20)              NOT NULL,
    fee_value       NUMERIC(19, 4)           NOT NULL,
    min_fee         NUMERIC(19, 2),
    max_fee         NUMERIC(19, 2),
    valid_from      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      UUID,
    CONSTRAINT fk_fee_rules_corridor FOREIGN KEY (corridor_code) REFERENCES configuration.corridors (code),
    CONSTRAINT ck_fee_rules_remittance_type CHECK (remittance_type IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT ck_fee_rules_type CHECK (type IN ('PERCENTAGE', 'FIXED')),
    CONSTRAINT ck_fee_rules_value CHECK (fee_value >= 0)
);

CREATE INDEX ix_fee_rules_corridor ON configuration.fee_rules (corridor_code, remittance_type, valid_from);

INSERT INTO configuration.settings (setting_key, setting_value) VALUES
    ('minimum_amount', '0'),
    ('default_delivery_days', '2'),
    ('exchange_rate_tolerance_percent', '20');


INSERT INTO identity.permissions (code, module, description) VALUES
    ('configuration:read',  'configuration', 'View the configuration: minimum amount, delivery days, corridors, exchange rates and fee rules'),
    ('configuration:write', 'configuration', 'Change the configuration: minimum amount, delivery days, exchange rates and fee rules'),
    ('rates:read',          'rates',         'Calculate quotes');

-- =====================================================================================================================
-- remittances · deliveries and pickups, statuses and transitions as data, due dates, incidents and reviews
--
-- Statuses are data. The code only relies on their flags:
--   is_initial       a new remittance starts here (exactly one status)
--   requires_courier moving here assigns a courier
--   is_final         the remittance is completed: the courier's cash moves and it can no longer be late
-- Transitions say which moves are allowed and which permission each needs. Status names are only a fallback: the
-- frontend translates statuses by code and remittance type.
-- =====================================================================================================================

CREATE TABLE remittances.remittance_statuses (
    code             VARCHAR(30)  PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    position         INT          NOT NULL,
    is_initial       BOOLEAN      NOT NULL DEFAULT FALSE,
    requires_courier BOOLEAN      NOT NULL DEFAULT FALSE,
    is_final         BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE remittances.remittance_transitions (
    from_status     VARCHAR(30)  NOT NULL,
    to_status       VARCHAR(30)  NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    CONSTRAINT pk_remittance_transitions PRIMARY KEY (from_status, to_status),
    CONSTRAINT fk_remittance_transitions_from FOREIGN KEY (from_status) REFERENCES remittances.remittance_statuses (code),
    CONSTRAINT fk_remittance_transitions_to FOREIGN KEY (to_status) REFERENCES remittances.remittance_statuses (code),
    CONSTRAINT fk_remittance_transitions_permission FOREIGN KEY (permission_code) REFERENCES identity.permissions (code)
);

CREATE TABLE remittances.remittances (
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
    beneficiary_municipality_code VARCHAR(20)              NOT NULL,
    beneficiary_reference         VARCHAR(255),
    -- copy of the quote at creation
    corridor_code                 VARCHAR(7)               NOT NULL,
    source_currency               VARCHAR(3)               NOT NULL,
    target_currency               VARCHAR(3)               NOT NULL,
    amount                        NUMERIC(19, 2)           NOT NULL,
    fee                           NUMERIC(19, 2)           NOT NULL,
    -- the fee was entered by hand when registering (pickups only) instead of the configured rule
    fee_overridden                BOOLEAN                  NOT NULL DEFAULT FALSE,
    total                         NUMERIC(19, 2)           NOT NULL,
    rate                          NUMERIC(19, 6)           NOT NULL,
    -- the rate was entered by hand when registering (pickups only) instead of the configured one
    rate_overridden               BOOLEAN                  NOT NULL DEFAULT FALSE,
    amount_to_deliver             NUMERIC(19, 2)           NOT NULL,
    exchange_rate_id              UUID                     NOT NULL,
    fee_rule_id                   UUID                     NOT NULL,
    pin                           VARCHAR(6)               NOT NULL,
    courier_id                    UUID,
    expected_date                 DATE                     NOT NULL,
    postponements                 INT                      NOT NULL DEFAULT 0,
    -- the latest incident reported and not yet acted on (reassign, postpone, complete or any status change)
    open_incident_reason          VARCHAR(30),
    open_incident_note            VARCHAR(1000),
    open_incident_at              TIMESTAMP WITH TIME ZONE,
    open_incident_by              UUID,
    -- pending review after a sync from the mobile app, until someone resolves it with a note
    review_reason                 VARCHAR(40),
    review_flagged_at             TIMESTAMP WITH TIME ZONE,
    review_action_id              UUID,
    notes                         VARCHAR(1000),
    idempotency_key               VARCHAR(100),
    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by                    UUID                     NOT NULL,
    completed_at                  TIMESTAMP WITH TIME ZONE,
    completed_by                  UUID,
    -- server time when the completion was recorded: later than completed_at for a delivery synced late from the
    -- mobile app; Sales' completed feed polls on it so it never misses one
    completion_recorded_at        TIMESTAMP WITH TIME ZONE,
    -- voided: kept for the record (who, when, why) but counted nowhere; the cash it moved is reversed
    voided_at                     TIMESTAMP WITH TIME ZONE,
    voided_by                     UUID,
    void_reason                   VARCHAR(1000),
    -- last change of any kind: the mobile app downloads what changed since its last sync
    updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL,
    version                       BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT uk_remittances_code UNIQUE (code),
    CONSTRAINT uk_remittances_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_remittances_type CHECK (type IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT ck_remittances_incident_reason
        CHECK (open_incident_reason IN ('NOT_HOME', 'WRONG_ADDRESS', 'UNREACHABLE', 'REFUSED', 'OTHER')),
    CONSTRAINT ck_remittances_review_reason
        CHECK (review_reason IN ('DELIVERED_AFTER_REASSIGNMENT', 'PIN_MISMATCH', 'DUPLICATE_DELIVERY', 'LATE_SYNC')),
    CONSTRAINT fk_remittances_status FOREIGN KEY (status) REFERENCES remittances.remittance_statuses (code),
    CONSTRAINT fk_remittances_customer FOREIGN KEY (customer_id) REFERENCES customers.customers (id),
    CONSTRAINT fk_remittances_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries.beneficiaries (id),
    CONSTRAINT fk_remittances_municipality FOREIGN KEY (beneficiary_municipality_code) REFERENCES branches.municipalities (code),
    CONSTRAINT fk_remittances_corridor FOREIGN KEY (corridor_code) REFERENCES configuration.corridors (code),
    CONSTRAINT fk_remittances_courier FOREIGN KEY (courier_id) REFERENCES identity.users (id)
);

CREATE INDEX ix_remittances_status ON remittances.remittances (status, expected_date);
CREATE INDEX ix_remittances_customer ON remittances.remittances (customer_id, created_at);
CREATE INDEX ix_remittances_beneficiary ON remittances.remittances (beneficiary_id, created_at);
CREATE INDEX ix_remittances_courier ON remittances.remittances (courier_id, status);
CREATE INDEX ix_remittances_courier_updated ON remittances.remittances (courier_id, updated_at);
CREATE INDEX ix_remittances_open_incident ON remittances.remittances (open_incident_at);
CREATE INDEX ix_remittances_review ON remittances.remittances (review_flagged_at);
CREATE INDEX ix_remittances_completion_recorded ON remittances.remittances (completion_recorded_at)
    WHERE completion_recorded_at IS NOT NULL;

-- History of a remittance: who did what and when. Never edited.
CREATE TABLE remittances.remittance_events (
    id              UUID PRIMARY KEY,
    remittance_id   UUID                     NOT NULL,
    type            VARCHAR(30)              NOT NULL,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30),
    courier_id      UUID,
    previous_date   DATE,
    new_date        DATE,
    incident_reason VARCHAR(30),
    review_reason   VARCHAR(40),
    note            VARCHAR(1000),
    actor_id        UUID                     NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_remittance_events_remittance FOREIGN KEY (remittance_id) REFERENCES remittances.remittances (id)
);

CREATE INDEX ix_remittance_events_remittance ON remittances.remittance_events (remittance_id, occurred_at);

-- Actions applied from the mobile app's offline queue, recorded with the id the phone generated in the same
-- transaction as their effect: sending one again returns ALREADY_APPLIED. Rejected actions are not recorded.
CREATE TABLE remittances.synced_actions (
    id            UUID PRIMARY KEY,
    user_id       UUID                     NOT NULL,
    type          VARCHAR(30)              NOT NULL,
    remittance_id UUID                     NOT NULL,
    occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    device_id     VARCHAR(100),
    CONSTRAINT fk_synced_actions_remittance FOREIGN KEY (remittance_id) REFERENCES remittances.remittances (id),
    CONSTRAINT ck_synced_actions_type CHECK (type IN ('DELIVER', 'DELIVER_WITH_PIN', 'POSTPONE', 'REPORT_INCIDENT'))
);

CREATE INDEX ix_synced_actions_remittance ON remittances.synced_actions (remittance_id);

INSERT INTO identity.permissions (code, module, description) VALUES
    ('remittances:read',            'remittances', 'View every remittance, its history and customer and beneficiary statistics'),
    ('remittances:read-assigned',   'remittances', 'View the remittances assigned to oneself'),
    ('remittances:read-financials', 'remittances', 'View fee, rate and totals of pickups'),
    ('remittances:create',          'remittances', 'Register paid remittances and pickups'),
    ('remittances:assign',          'remittances', 'Assign and reassign remittances to couriers'),
    ('remittances:deliver',         'remittances', 'Mark remittances as delivered or collected; required to be a courier'),
    ('remittances:postpone',        'remittances', 'Postpone the expected date of a remittance'),
    ('remittances:report-incident', 'remittances', 'Report why a delivery or pickup could not be done; couriers only on their own remittances'),
    ('remittances:review',          'remittances', 'Resolve remittances flagged for review after a sync from the mobile app'),
    ('remittances:void',            'remittances', 'Void a remittance: it stops counting and the cash it moved is reversed'),
    ('remittances:historical',      'remittances', 'Register old remittances and pickups, delivered on their date');

-- Initial workflow: Paid → Assigned (→ reassigned) → Delivered
INSERT INTO remittances.remittance_statuses (code, name, position, is_initial, requires_courier, is_final) VALUES
    ('PAID',      'Pagada',    10, TRUE,  FALSE, FALSE),
    ('ASSIGNED',  'Asignada',  20, FALSE, TRUE,  FALSE),
    ('DELIVERED', 'Entregada', 30, FALSE, FALSE, TRUE);

INSERT INTO remittances.remittance_transitions (from_status, to_status, permission_code) VALUES
    ('PAID',     'ASSIGNED',  'remittances:assign'),
    ('ASSIGNED', 'ASSIGNED',  'remittances:assign'),
    ('ASSIGNED', 'DELIVERED', 'remittances:deliver');

-- =====================================================================================================================
-- payments · cash of the business and of each courier
--
-- Double-entry ledger: every movement takes money out of one account and puts it into another, so balances (the sum
-- of movements) always reconcile. Balances are never stored.
--   BUSINESS  the business cash box, one per currency it handles (a single branch in 1.0); created when first used
--   COURIER   the cash a courier carries, one per courier and currency; may go negative
--   EXTERNAL  the outside world (senders, beneficiaries), one per currency
--
-- Courier currency exchanges: the business works in USD; when a delivery is in CUP, the courier changes USD on the way
-- at the rate they get and records it, online or from the offline queue. An exchange moves cash only inside the
-- courier's cash (two legs: one currency out, another in) and the business box does not change. rate is always units
-- of the other currency per 1 USD, in both directions.
-- =====================================================================================================================

CREATE TABLE payments.cash_accounts (
    id          UUID PRIMARY KEY,
    account_key VARCHAR(80)              NOT NULL,
    type        VARCHAR(20)              NOT NULL,
    owner_id    UUID,
    currency    VARCHAR(3)               NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_cash_accounts_key UNIQUE (account_key),
    CONSTRAINT ck_cash_accounts_type CHECK (type IN ('BUSINESS', 'COURIER', 'EXTERNAL'))
);

CREATE TABLE payments.cash_exchanges (
    id                UUID PRIMARY KEY,
    courier_id        UUID                     NOT NULL,
    from_currency     VARCHAR(3)               NOT NULL,
    from_amount       NUMERIC(19, 2)           NOT NULL,
    to_currency       VARCHAR(3)               NOT NULL,
    to_amount         NUMERIC(19, 2)           NOT NULL,
    rate              NUMERIC(19, 6)           NOT NULL,
    -- the configured delivery rate it was compared with, and how far it strayed (null: no rate configured)
    configured_rate   NUMERIC(19, 6),
    deviation_percent NUMERIC(9, 2),
    note              VARCHAR(1000),
    occurred_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by       UUID                     NOT NULL,
    recorded_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    -- id generated by the phone for an exchange synced from the offline queue: sending it again is harmless
    client_action_id  UUID,
    review_reason     VARCHAR(40),
    review_flagged_at TIMESTAMP WITH TIME ZONE,
    resolved_at       TIMESTAMP WITH TIME ZONE,
    resolved_by       UUID,
    resolution_note   VARCHAR(1000),
    CONSTRAINT uk_cash_exchanges_client_action UNIQUE (client_action_id),
    CONSTRAINT fk_cash_exchanges_courier FOREIGN KEY (courier_id) REFERENCES identity.users (id),
    CONSTRAINT ck_cash_exchanges_amounts CHECK (from_amount > 0 AND to_amount > 0 AND rate > 0),
    CONSTRAINT ck_cash_exchanges_currencies CHECK (from_currency <> to_currency),
    CONSTRAINT ck_cash_exchanges_review_reason CHECK (review_reason IN ('UNUSUAL_EXCHANGE_RATE', 'LATE_SYNC'))
);

CREATE INDEX ix_cash_exchanges_courier ON payments.cash_exchanges (courier_id, occurred_at);
CREATE INDEX ix_cash_exchanges_review ON payments.cash_exchanges (review_flagged_at);

CREATE TABLE payments.cash_movements (
    id              UUID PRIMARY KEY,
    type            VARCHAR(30)              NOT NULL,
    from_account_id UUID                     NOT NULL,
    to_account_id   UUID                     NOT NULL,
    amount          NUMERIC(19, 2)           NOT NULL,
    currency        VARCHAR(3)               NOT NULL,
    remittance_id   UUID,
    -- both legs of a courier exchange point at it and carry its rate
    exchange_id     UUID,
    rate            NUMERIC(19, 6),
    note            VARCHAR(1000),
    actor_id        UUID                     NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_cash_movements_from FOREIGN KEY (from_account_id) REFERENCES payments.cash_accounts (id),
    CONSTRAINT fk_cash_movements_to FOREIGN KEY (to_account_id) REFERENCES payments.cash_accounts (id),
    CONSTRAINT fk_cash_movements_remittance FOREIGN KEY (remittance_id) REFERENCES remittances.remittances (id),
    CONSTRAINT fk_cash_movements_exchange FOREIGN KEY (exchange_id) REFERENCES payments.cash_exchanges (id),
    CONSTRAINT ck_cash_movements_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_movements_accounts CHECK (from_account_id <> to_account_id)
);

CREATE INDEX ix_cash_movements_from ON payments.cash_movements (from_account_id, occurred_at);
CREATE INDEX ix_cash_movements_to ON payments.cash_movements (to_account_id, occurred_at);
CREATE INDEX ix_cash_movements_remittance ON payments.cash_movements (remittance_id);
CREATE INDEX ix_cash_movements_exchange ON payments.cash_movements (exchange_id);


INSERT INTO identity.permissions (code, module, description) VALUES
    ('cash:read',     'payments', 'View the business cash, every courier''s cash and all movements'),
    ('cash:read-own', 'payments', 'View one''s own cash as a courier'),
    ('cash:write',    'payments', 'Hand cash to couriers and register the cash they return'),
    ('cash:adjust',   'payments', 'Register deposits into and withdrawals from the business cash box (capital, currency exchange, expenses)'),
    ('cash:exchange-own', 'payments', 'Record currency exchanges of one''s own cash as a courier'),
    ('cash:review',       'payments', 'Resolve courier currency exchanges flagged for review');

-- =====================================================================================================================
-- reporting · audit log (who did what and when) and business reports
--
-- Modules publish an AuditEvent for every change that must leave a trace (staff users and roles, own password
-- changes, the initial administrator); reporting stores it here in the same transaction. Entries are never edited nor
-- deleted. details is a JSON object with what changed; it never contains secrets.
-- =====================================================================================================================

CREATE TABLE reporting.audit_log (
    id           UUID PRIMARY KEY,
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_id     UUID,
    action       VARCHAR(50)              NOT NULL,
    target_type  VARCHAR(30)              NOT NULL,
    target_id    VARCHAR(100)             NOT NULL,
    target_label VARCHAR(255),
    details      TEXT                     NOT NULL
);

CREATE INDEX ix_audit_log_occurred ON reporting.audit_log (occurred_at);
CREATE INDEX ix_audit_log_target ON reporting.audit_log (target_type, target_id, occurred_at);
CREATE INDEX ix_audit_log_actor ON reporting.audit_log (actor_id, occurred_at);

INSERT INTO identity.permissions (code, module, description) VALUES
    ('audit:read',   'reporting', 'View the audit log: who changed users, roles and other audited records, and when'),
    ('reports:read', 'reporting', 'View the business reports, such as the monthly sales report');

-- =====================================================================================================================
-- apps · mobile app releases (contract: docs/contracts/app-version.md)
--
-- One row per published build; never edited nor deleted. Rows are inserted by data scripts (scripts/data/app-releases),
-- never by an endpoint, so no credential can announce a fake mandatory update. The download link is not stored: the
-- server builds it from a fixed base per platform. Latest build = highest of its platform; minimum = highest mandatory.
-- =====================================================================================================================

CREATE TABLE apps.app_releases (
    id           UUID PRIMARY KEY,
    platform     VARCHAR(10)              NOT NULL,
    -- versionCode (Android) / CFBundleVersion (iOS): grows with every build
    build        INT                      NOT NULL,
    -- what people see, e.g. 1.0.0-rc.2
    version_name VARCHAR(30)              NOT NULL,
    -- clients with a lower build must update
    mandatory    BOOLEAN                  NOT NULL DEFAULT FALSE,
    notes        VARCHAR(1000),
    released_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_releases_platform_build UNIQUE (platform, build),
    CONSTRAINT ck_app_releases_platform CHECK (platform IN ('ANDROID', 'IOS')),
    CONSTRAINT ck_app_releases_build CHECK (build > 0)
);

-- =====================================================================================================================
-- Initial grants. ADMIN holds every permission. The roles of each installation get theirs from its setup script.
-- =====================================================================================================================

INSERT INTO identity.role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM identity.roles r CROSS JOIN identity.permissions p WHERE r.code = 'ADMIN';
