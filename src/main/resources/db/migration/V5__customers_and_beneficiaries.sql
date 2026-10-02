-- Phase 1 · Customers (senders) and beneficiaries (receivers).
-- A beneficiary is one person, shared by every customer that sends to them (customer_beneficiaries).
-- Phones are stored normalized: digits only, with an optional leading +.

ALTER TABLE customers ALTER COLUMN phone SET NOT NULL;
ALTER TABLE customers ADD COLUMN address VARCHAR(255);
ALTER TABLE customers ADD COLUMN notes VARCHAR(1000);
ALTER TABLE customers ADD COLUMN created_by UUID;
ALTER TABLE customers ADD CONSTRAINT uk_customers_phone UNIQUE (phone);
DROP INDEX ix_customers_phone;

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

INSERT INTO permissions (code, module, description) VALUES
    ('customers:read',  'customers', 'View customers and beneficiaries'),
    ('customers:write', 'customers', 'Create and update customers and beneficiaries, link beneficiaries to customers');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p
WHERE p.code IN ('customers:read', 'customers:write') AND r.code IN ('ADMIN', 'SALES');
