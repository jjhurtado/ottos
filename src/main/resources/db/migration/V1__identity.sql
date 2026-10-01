-- Phase 0 · Identity and access: staff users, dynamic roles and permissions (RBAC), refresh tokens.
--
-- Permissions are the catalog the code checks (@PreAuthorize("hasAuthority('users:read')")); new ones
-- arrive through migrations together with the code that uses them, and must also be granted to ADMIN.
-- Roles and their permissions are data: administrators manage them through the API.

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

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID                     NOT NULL,
    token_hash VARCHAR(64)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

-- Permission catalog
INSERT INTO permissions (code, module, description) VALUES
    ('users:read',  'identity', 'View staff users and their roles'),
    ('users:write', 'identity', 'Create, update, activate and deactivate staff users, assign roles, reset passwords'),
    ('roles:read',  'identity', 'View roles and the permission catalog'),
    ('roles:write', 'identity', 'Create, update and delete roles and their permissions');

-- Initial roles. ADMIN is built in: it cannot be edited or deleted and holds every permission.
-- SALES and DELIVERY receive their permissions as their modules are built.
INSERT INTO roles (id, code, name, description, built_in, created_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN',    'Administrator', 'Full access to the system', TRUE,  CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', 'SALES',    'Sales',         'Registers and charges remittances', FALSE, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'DELIVERY', 'Delivery',      'Delivers remittances to beneficiaries', FALSE, CURRENT_TIMESTAMP);

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';
