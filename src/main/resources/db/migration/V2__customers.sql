-- Phase 1 · Customers (remittance senders), kept apart from staff users.
-- They have no login yet; self sign-up will add a customer_credentials table instead of touching users.

CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    full_name       VARCHAR(150)             NOT NULL,
    phone           VARCHAR(30),
    email           VARCHAR(255),
    document_type   VARCHAR(20),
    document_number VARCHAR(50),
    active          BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_customers_document UNIQUE (document_type, document_number)
);

CREATE INDEX ix_customers_phone ON customers (phone);
