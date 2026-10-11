-- Ottos · Baseline schema for version 1.0.
--
-- Built from every migration of the betas, merged before the first release because no environment had real data
-- yet. From now on the schema only changes with new migrations (V2, V3…), never by editing this one.
-- Sections follow the modules: identity, branches, customers, beneficiaries, configuration, remittances, payments,
-- reporting.

-- =====================================================================================================================
-- identity · staff users, dynamic roles and permissions (RBAC), refresh tokens
--
-- Permissions are the catalog the code checks (@PreAuthorize("hasAuthority('users:read')")); new ones arrive through
-- migrations together with the code that uses them, and are granted to ADMIN in the same migration. Roles and their
-- permissions are data: administrators manage them through the API.
-- =====================================================================================================================

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255)             NOT NULL,
    password_hash VARCHAR(100)             NOT NULL,
    name          VARCHAR(150)             NOT NULL,
    active        BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE permissions (
    code        VARCHAR(100) PRIMARY KEY,
    module      VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    code        VARCHAR(50)              NOT NULL,
    name        VARCHAR(100)             NOT NULL,
    description VARCHAR(255),
    built_in    BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_roles_code UNIQUE (code)
);

CREATE TABLE role_permissions (
    role_id         UUID         NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_code) REFERENCES permissions (code)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX ix_user_roles_role ON user_roles (role_id);

-- Opaque single-use refresh tokens, stored only as a SHA-256 hash. client: where the session was opened (WEB, or
-- MOBILE for the offline-first app, whose access tokens last a working day). replaced_by: the token issued when this
-- one was used, to tell a retry within ottos.security.refresh-reuse-grace from a stolen token.
CREATE TABLE refresh_tokens (
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
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

INSERT INTO permissions (code, module, description) VALUES
    ('users:read',  'identity', 'View staff users and their roles'),
    ('users:write', 'identity', 'Create, update, activate and deactivate staff users, assign roles, reset passwords'),
    ('roles:read',  'identity', 'View roles and the permission catalog'),
    ('roles:write', 'identity', 'Create, update and delete roles and their permissions');

-- Initial roles. ADMIN is built in: it cannot be edited or deleted and holds every permission (granted at the end of
-- this file). SALES and DELIVERY can be edited through the API. Names are in English: the frontend translates by code.
INSERT INTO roles (id, code, name, description, built_in, created_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN',    'Administrator', 'Full access to the system', TRUE,  CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', 'SALES',    'Sales',         'Registers and charges remittances', FALSE, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'DELIVERY', 'Delivery',      'Delivers remittances to beneficiaries', FALSE, CURRENT_TIMESTAMP);

-- =====================================================================================================================
-- branches · zones: official provinces and municipalities of Cuba (codes from the political-administrative division)
--
-- Only La Habana for now; other provinces arrive with new migrations. A single branch in 1.0, so no branch tables yet.
-- =====================================================================================================================

CREATE TABLE provinces (
    code   VARCHAR(2)   PRIMARY KEY,
    name   VARCHAR(100) NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE municipalities (
    code          VARCHAR(4)   PRIMARY KEY,
    province_code VARCHAR(2)   NOT NULL,
    name          VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_municipalities_province FOREIGN KEY (province_code) REFERENCES provinces (code)
);

INSERT INTO provinces (code, name) VALUES ('23', 'La Habana');

INSERT INTO municipalities (code, province_code, name) VALUES
    ('2301', '23', 'Playa'),
    ('2302', '23', 'Plaza de la Revolución'),
    ('2303', '23', 'Centro Habana'),
    ('2304', '23', 'La Habana Vieja'),
    ('2305', '23', 'Regla'),
    ('2306', '23', 'La Habana del Este'),
    ('2307', '23', 'Guanabacoa'),
    ('2308', '23', 'San Miguel del Padrón'),
    ('2309', '23', 'Diez de Octubre'),
    ('2310', '23', 'Cerro'),
    ('2311', '23', 'Marianao'),
    ('2312', '23', 'La Lisa'),
    ('2313', '23', 'Boyeros'),
    ('2314', '23', 'Arroyo Naranjo'),
    ('2315', '23', 'Cotorro');

-- =====================================================================================================================
-- customers · remittance senders, kept apart from staff users
--
-- They have no login yet; customer sign-up (Phase 2) will add its own credentials table instead of touching users.
-- Phones are stored normalized (digits only, with an optional leading +). Neither the phone nor the document is unique:
-- a person with two addresses is two customers with the same name and phone (there is no multi-address model). Only
-- an identical customer (same name, phone and address) is rejected, by the application.
-- =====================================================================================================================

CREATE TABLE customers (
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

CREATE INDEX ix_customers_phone ON customers (phone);

INSERT INTO permissions (code, module, description) VALUES
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

CREATE TABLE beneficiaries (
    id                UUID PRIMARY KEY,
    full_name         VARCHAR(150)             NOT NULL,
    phone             VARCHAR(30)              NOT NULL,
    alternate_phone   VARCHAR(30),
    street            VARCHAR(150)             NOT NULL,
    house_number      VARCHAR(30),
    between_streets   VARCHAR(150),
    neighborhood      VARCHAR(100),
    municipality_code VARCHAR(4)               NOT NULL,
    reference         VARCHAR(255),
    document_number   VARCHAR(50),
    notes             VARCHAR(1000),
    active            BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by        UUID,
    CONSTRAINT fk_beneficiaries_municipality FOREIGN KEY (municipality_code) REFERENCES municipalities (code)
);

CREATE INDEX ix_beneficiaries_phone ON beneficiaries (phone);
CREATE INDEX ix_beneficiaries_municipality ON beneficiaries (municipality_code);

CREATE TABLE customer_beneficiaries (
    customer_id    UUID                     NOT NULL,
    beneficiary_id UUID                     NOT NULL,
    linked_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_customer_beneficiaries PRIMARY KEY (customer_id, beneficiary_id),
    CONSTRAINT fk_customer_beneficiaries_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_customer_beneficiaries_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries (id)
);

CREATE INDEX ix_customer_beneficiaries_beneficiary ON customer_beneficiaries (beneficiary_id);

-- =====================================================================================================================
-- configuration · every value the business can change without a deploy, managed from one place
--
-- Settings, and per corridor (currency pair such as USD-CUP) its exchange rates and fee rules, one history for
-- deliveries and another for pickups. Rates and fee rules are never edited: each change inserts a new row with its
-- valid_from, and every remittance keeps a copy of the values it used. Concrete values (e.g. the minimum amount) are
-- set through the API, never in migrations.
-- =====================================================================================================================

-- minimum_amount: smallest amount a remittance can send, in USD (0 = no minimum)
-- default_delivery_days: days from registration to the expected delivery date
-- exchange_rate_tolerance_percent: a courier exchange whose rate strays more than this from the configured delivery
--   rate is recorded but flagged for review
CREATE TABLE settings (
    setting_key   VARCHAR(50)              PRIMARY KEY,
    setting_value VARCHAR(255)             NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE,
    updated_by    UUID
);

CREATE TABLE corridors (
    code              VARCHAR(7)     PRIMARY KEY,
    source_currency   VARCHAR(3)     NOT NULL,
    target_currency   VARCHAR(3)     NOT NULL,
    -- amount to deliver is rounded down to a multiple of this (50 CUP; 0.01 means no rounding)
    delivery_rounding NUMERIC(19, 2) NOT NULL,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_corridors_pair UNIQUE (source_currency, target_currency),
    CONSTRAINT ck_corridors_rounding CHECK (delivery_rounding > 0)
);

CREATE TABLE exchange_rates (
    id              UUID PRIMARY KEY,
    corridor_code   VARCHAR(7)               NOT NULL,
    remittance_type VARCHAR(20)              NOT NULL,
    rate            NUMERIC(19, 6)           NOT NULL,
    valid_from      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by      UUID,
    CONSTRAINT fk_exchange_rates_corridor FOREIGN KEY (corridor_code) REFERENCES corridors (code),
    CONSTRAINT ck_exchange_rates_rate CHECK (rate > 0),
    CONSTRAINT ck_exchange_rates_remittance_type CHECK (remittance_type IN ('DELIVERY', 'PICKUP'))
);

CREATE INDEX ix_exchange_rates_corridor ON exchange_rates (corridor_code, remittance_type, valid_from);

CREATE TABLE fee_rules (
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
    CONSTRAINT fk_fee_rules_corridor FOREIGN KEY (corridor_code) REFERENCES corridors (code),
    CONSTRAINT ck_fee_rules_remittance_type CHECK (remittance_type IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT ck_fee_rules_type CHECK (type IN ('PERCENTAGE', 'FIXED')),
    CONSTRAINT ck_fee_rules_value CHECK (fee_value >= 0)
);

CREATE INDEX ix_fee_rules_corridor ON fee_rules (corridor_code, remittance_type, valid_from);

INSERT INTO settings (setting_key, setting_value) VALUES
    ('minimum_amount', '0'),
    ('default_delivery_days', '2'),
    ('exchange_rate_tolerance_percent', '20');

INSERT INTO corridors (code, source_currency, target_currency, delivery_rounding) VALUES
    ('USD-CUP', 'USD', 'CUP', 50),
    ('USD-USD', 'USD', 'USD', 0.01);

-- Same currency: the rate is always 1. Pickups start at 1 (1x1). The USD-CUP delivery rate has no value until an
-- administrator sets one.
INSERT INTO exchange_rates (id, corridor_code, remittance_type, rate, valid_from) VALUES
    ('00000000-0000-0000-0001-000000000001', 'USD-USD', 'DELIVERY', 1, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0001-000000000002', 'USD-CUP', 'PICKUP',   1, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0001-000000000003', 'USD-USD', 'PICKUP',   1, CURRENT_TIMESTAMP);

-- 10 % of the amount, at least 10 USD, for deliveries and pickups alike until an administrator changes them.
INSERT INTO fee_rules (id, corridor_code, remittance_type, type, fee_value, min_fee, valid_from) VALUES
    ('00000000-0000-0000-0002-000000000001', 'USD-CUP', 'DELIVERY', 'PERCENTAGE', 10, 10, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0002-000000000002', 'USD-USD', 'DELIVERY', 'PERCENTAGE', 10, 10, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0002-000000000003', 'USD-CUP', 'PICKUP',   'PERCENTAGE', 10, 10, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0002-000000000004', 'USD-USD', 'PICKUP',   'PERCENTAGE', 10, 10, CURRENT_TIMESTAMP);

INSERT INTO permissions (code, module, description) VALUES
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
CREATE INDEX ix_remittances_courier_updated ON remittances (courier_id, updated_at);
CREATE INDEX ix_remittances_open_incident ON remittances (open_incident_at);
CREATE INDEX ix_remittances_review ON remittances (review_flagged_at);
CREATE INDEX ix_remittances_completion_recorded ON remittances (completion_recorded_at)
    WHERE completion_recorded_at IS NOT NULL;

-- History of a remittance: who did what and when. Never edited.
CREATE TABLE remittance_events (
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
    CONSTRAINT fk_remittance_events_remittance FOREIGN KEY (remittance_id) REFERENCES remittances (id)
);

CREATE INDEX ix_remittance_events_remittance ON remittance_events (remittance_id, occurred_at);

-- Actions applied from the mobile app's offline queue, recorded with the id the phone generated in the same
-- transaction as their effect: sending one again returns ALREADY_APPLIED. Rejected actions are not recorded.
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

INSERT INTO permissions (code, module, description) VALUES
    ('remittances:read',            'remittances', 'View every remittance, its history and customer and beneficiary statistics'),
    ('remittances:read-assigned',   'remittances', 'View the remittances assigned to oneself'),
    ('remittances:read-financials', 'remittances', 'View fee, rate and totals of pickups'),
    ('remittances:create',          'remittances', 'Register paid remittances and pickups'),
    ('remittances:assign',          'remittances', 'Assign and reassign remittances to couriers'),
    ('remittances:deliver',         'remittances', 'Mark remittances as delivered or collected; required to be a courier'),
    ('remittances:postpone',        'remittances', 'Postpone the expected date of a remittance'),
    ('remittances:report-incident', 'remittances', 'Report why a delivery or pickup could not be done; couriers only on their own remittances'),
    ('remittances:review',          'remittances', 'Resolve remittances flagged for review after a sync from the mobile app');

-- Initial workflow: Paid → Assigned (→ reassigned) → Delivered
INSERT INTO remittance_statuses (code, name, position, is_initial, requires_courier, is_final) VALUES
    ('PAID',      'Pagada',    10, TRUE,  FALSE, FALSE),
    ('ASSIGNED',  'Asignada',  20, FALSE, TRUE,  FALSE),
    ('DELIVERED', 'Entregada', 30, FALSE, FALSE, TRUE);

INSERT INTO remittance_transitions (from_status, to_status, permission_code) VALUES
    ('PAID',     'ASSIGNED',  'remittances:assign'),
    ('ASSIGNED', 'ASSIGNED',  'remittances:assign'),
    ('ASSIGNED', 'DELIVERED', 'remittances:deliver');

-- =====================================================================================================================
-- payments · cash of the business and of each courier
--
-- Double-entry ledger: every movement takes money out of one account and puts it into another, so balances (the sum
-- of movements) always reconcile. Balances are never stored.
--   BUSINESS  the business cash box, one per currency (a single branch in 1.0)
--   COURIER   the cash a courier carries, one per courier and currency; may go negative
--   EXTERNAL  the outside world (senders, beneficiaries), one per currency
--
-- Courier currency exchanges: the business works in USD; when a delivery is in CUP, the courier changes USD on the way
-- at the rate they get and records it, online or from the offline queue. An exchange moves cash only inside the
-- courier's cash (two legs: one currency out, another in) and the business box does not change. rate is always units
-- of the other currency per 1 USD, in both directions.
-- =====================================================================================================================

CREATE TABLE cash_accounts (
    id          UUID PRIMARY KEY,
    account_key VARCHAR(80)              NOT NULL,
    type        VARCHAR(20)              NOT NULL,
    owner_id    UUID,
    currency    VARCHAR(3)               NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_cash_accounts_key UNIQUE (account_key),
    CONSTRAINT ck_cash_accounts_type CHECK (type IN ('BUSINESS', 'COURIER', 'EXTERNAL'))
);

CREATE TABLE cash_exchanges (
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
    CONSTRAINT fk_cash_exchanges_courier FOREIGN KEY (courier_id) REFERENCES users (id),
    CONSTRAINT ck_cash_exchanges_amounts CHECK (from_amount > 0 AND to_amount > 0 AND rate > 0),
    CONSTRAINT ck_cash_exchanges_currencies CHECK (from_currency <> to_currency),
    CONSTRAINT ck_cash_exchanges_review_reason CHECK (review_reason IN ('UNUSUAL_EXCHANGE_RATE', 'LATE_SYNC'))
);

CREATE INDEX ix_cash_exchanges_courier ON cash_exchanges (courier_id, occurred_at);
CREATE INDEX ix_cash_exchanges_review ON cash_exchanges (review_flagged_at);

CREATE TABLE cash_movements (
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
    CONSTRAINT fk_cash_movements_from FOREIGN KEY (from_account_id) REFERENCES cash_accounts (id),
    CONSTRAINT fk_cash_movements_to FOREIGN KEY (to_account_id) REFERENCES cash_accounts (id),
    CONSTRAINT fk_cash_movements_remittance FOREIGN KEY (remittance_id) REFERENCES remittances (id),
    CONSTRAINT fk_cash_movements_exchange FOREIGN KEY (exchange_id) REFERENCES cash_exchanges (id),
    CONSTRAINT ck_cash_movements_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_movements_accounts CHECK (from_account_id <> to_account_id)
);

CREATE INDEX ix_cash_movements_from ON cash_movements (from_account_id, occurred_at);
CREATE INDEX ix_cash_movements_to ON cash_movements (to_account_id, occurred_at);
CREATE INDEX ix_cash_movements_remittance ON cash_movements (remittance_id);
CREATE INDEX ix_cash_movements_exchange ON cash_movements (exchange_id);

INSERT INTO cash_accounts (id, account_key, type, currency, created_at) VALUES
    ('00000000-0000-0000-0003-000000000001', 'BUSINESS:USD', 'BUSINESS', 'USD', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0003-000000000002', 'BUSINESS:CUP', 'BUSINESS', 'CUP', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0003-000000000003', 'EXTERNAL:USD', 'EXTERNAL', 'USD', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0003-000000000004', 'EXTERNAL:CUP', 'EXTERNAL', 'CUP', CURRENT_TIMESTAMP);

INSERT INTO permissions (code, module, description) VALUES
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
    ('audit:read',   'reporting', 'View the audit log: who changed users, roles and other audited records, and when'),
    ('reports:read', 'reporting', 'View the business reports, such as the monthly sales report');

-- =====================================================================================================================
-- Initial grants. ADMIN holds every permission; SALES and DELIVERY start with these and can be changed through the API.
-- =====================================================================================================================

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE r.code = 'SALES' AND p.code IN (
    'customers:read', 'customers:write',
    'configuration:read', 'rates:read',
    'remittances:read', 'remittances:create', 'remittances:assign', 'remittances:postpone',
    'remittances:report-incident', 'remittances:review',
    'cash:read', 'cash:write', 'cash:review',
    'reports:read');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE r.code = 'DELIVERY' AND p.code IN (
    'remittances:read-assigned', 'remittances:deliver', 'remittances:postpone', 'remittances:report-incident',
    'cash:read-own', 'cash:exchange-own');
