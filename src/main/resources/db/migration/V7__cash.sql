-- Phase 1 · Cash flow of the business and of each courier.
--
-- Double-entry ledger: every movement takes money out of one account and puts it into another, so balances
-- (the sum of movements) always reconcile. Balances are never stored.
--   BUSINESS  the business cash box, one per currency (one box for now; branch boxes can be added as a new type)
--   COURIER   the cash a courier carries, one per courier and currency; may go negative
--   EXTERNAL  the outside world (senders, beneficiaries), one per currency

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

CREATE TABLE cash_movements (
    id              UUID PRIMARY KEY,
    type            VARCHAR(30)              NOT NULL,
    from_account_id UUID                     NOT NULL,
    to_account_id   UUID                     NOT NULL,
    amount          NUMERIC(19, 2)           NOT NULL,
    currency        VARCHAR(3)               NOT NULL,
    remittance_id   UUID,
    note            VARCHAR(1000),
    actor_id        UUID                     NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_cash_movements_from FOREIGN KEY (from_account_id) REFERENCES cash_accounts (id),
    CONSTRAINT fk_cash_movements_to FOREIGN KEY (to_account_id) REFERENCES cash_accounts (id),
    CONSTRAINT fk_cash_movements_remittance FOREIGN KEY (remittance_id) REFERENCES remittances (id),
    CONSTRAINT ck_cash_movements_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_movements_accounts CHECK (from_account_id <> to_account_id)
);

CREATE INDEX ix_cash_movements_from ON cash_movements (from_account_id, occurred_at);
CREATE INDEX ix_cash_movements_to ON cash_movements (to_account_id, occurred_at);
CREATE INDEX ix_cash_movements_remittance ON cash_movements (remittance_id);

INSERT INTO cash_accounts (id, account_key, type, currency, created_at) VALUES
    ('00000000-0000-0000-0003-000000000001', 'BUSINESS:USD', 'BUSINESS', 'USD', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0003-000000000002', 'BUSINESS:CUP', 'BUSINESS', 'CUP', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0003-000000000003', 'EXTERNAL:USD', 'EXTERNAL', 'USD', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0003-000000000004', 'EXTERNAL:CUP', 'EXTERNAL', 'CUP', CURRENT_TIMESTAMP);

INSERT INTO permissions (code, module, description) VALUES
    ('cash:read',     'payments', 'View the business cash, every courier''s cash and all movements'),
    ('cash:read-own', 'payments', 'View one''s own cash as a courier'),
    ('cash:write',    'payments', 'Hand cash to couriers and register the cash they return'),
    ('cash:adjust',   'payments', 'Register deposits into and withdrawals from the business cash box (capital, currency exchange, expenses)');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE p.module = 'payments' AND r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE p.code IN ('cash:read', 'cash:write') AND r.code = 'SALES';

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'cash:read-own' FROM roles r WHERE r.code = 'DELIVERY';

