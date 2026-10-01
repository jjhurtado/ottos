# Ottos Backend

Backend for the **Ottos** remittance platform: Java 21 + Spring Boot 3.5, PostgreSQL, modular monolith.
Base package: `org.jobits.ottos`.

## Requirements

- JDK 21
- No Gradle install needed: use the included wrapper (`./gradlew`, or `gradlew.bat` on Windows)
- Docker (for local PostgreSQL)

## Run locally

```bash
# 1. Local credentials (once): copy the template and fill in the empty values
cp .env.example .env
#    DB_PASSWORD and OTTOS_ADMIN_PASSWORD: any value; OTTOS_JWT_SECRET: openssl rand -base64 32

# 2. Database
docker compose up -d

# 3. Application with the development profile
./gradlew bootRun --args="--spring.profiles.active=dev"
```

`.env` is ignored by git. Docker Compose reads it automatically and the application imports it
(`spring.config.import`), so run both from the project root (in IntelliJ, the run configuration's working
directory). Real environment variables override it.

On first start the application creates the administrator from `OTTOS_ADMIN_EMAIL` / `OTTOS_ADMIN_PASSWORD`.
Changing those values later does not change an existing administrator.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI contract: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

Try the login:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@ottos.local","password":"<OTTOS_ADMIN_PASSWORD from .env>"}'
```

## Tests

```bash
./gradlew build
```

Tests use H2 in PostgreSQL mode with the same Flyway migrations, so they don't need Docker.
`ModularityTest` fails if a module uses another module's internal classes.

## Structure

Every direct package under `org.jobits.ottos` is a module (Spring Modulith). Types in the module's root
package are its public API; sub-packages are internal.

| Module | Package | Phase |
| --- | --- | --- |
| Identity and access (staff users, roles, permissions) | `identity` | 0 (implemented) |
| Branches and zones | `branches` | 0–1 |
| Customers (remittance senders, no login yet) | `customers` | 1 (table and entity) |
| Rates and fees | `rates` | 1 |
| Beneficiaries | `beneficiaries` | 1 |
| Remittances | `remittances` | 1 |
| Dispatch and deliveries | `dispatch` | 1 |
| Payments and cash | `payments` | 1 and 3 |
| Notifications | `notifications` | 2 |
| Reporting and audit | `reporting` | 4 |

Inside each module: `domain` (entities, repositories), application services (e.g. `security`, `management`) and `web` (controllers).

## Environment variables

| Variable | Purpose | Default |
| --- | --- | --- |
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/ottos` |
| `DB_USER` / `DB_PASSWORD` | Database credentials (also used by docker compose) | Required |
| `OTTOS_JWT_SECRET` | Base64 HMAC key, at least 32 bytes (`openssl rand -base64 32`) | Required |
| `OTTOS_ADMIN_EMAIL` / `OTTOS_ADMIN_PASSWORD` | Creates the first ADMIN if none exists | empty |
| `PORT` | HTTP port | `8080` |

Token lifetimes: `ottos.security.access-token-ttl` (15m) and `ottos.security.refresh-token-ttl` (30d).

## Roles and permissions

Access control is RBAC with dynamic roles:

- **Permissions** (`users:read`, `roles:write`…) are the catalog the code checks. They are added only through
  Flyway migrations, together with the endpoint that uses them.
- **Roles** are data. Administrators create them, choose their permissions and assign them to users through
  the API. A user can hold several roles; their effective permissions are the union.
- `ADMIN` is built in: it cannot be edited or deleted and must hold every permission. At least one active
  administrator always remains.
- Nobody can grant or revoke permissions they don't hold, either by editing a role or by assigning one.

The access token carries `roles` and `permissions`; each permission is a Spring Security authority. Protect
endpoints with permissions, never with role names:

```java
@PreAuthorize("hasAuthority('remittances:create')")
```

To add a permission: write a migration that inserts it into `permissions` and grants it to ADMIN, then use it in
`@PreAuthorize`. `PermissionCatalogTest` fails if an endpoint checks a permission missing from the catalog or if
ADMIN lacks one.

Role or permission changes reach a user when they log in again or refresh their token, so within one access-token
lifetime (15 minutes). Deactivating a user or changing their password revokes their refresh tokens.

## Endpoints

| Method | Path | Access |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | Public |
| POST | `/api/v1/auth/refresh` | Public (refresh token, single use) |
| POST | `/api/v1/auth/logout` | Public (refresh token) |
| GET | `/api/v1/auth/me` | Authenticated |
| POST | `/api/v1/auth/change-password` | Authenticated |
| GET | `/api/v1/users`, `/api/v1/users/{id}` | `users:read` |
| POST | `/api/v1/users` | `users:write` |
| PUT | `/api/v1/users/{id}`, `/api/v1/users/{id}/roles`, `/api/v1/users/{id}/password` | `users:write` |
| POST | `/api/v1/users/{id}/activate`, `/api/v1/users/{id}/deactivate` | `users:write` |
| GET | `/api/v1/permissions`, `/api/v1/roles`, `/api/v1/roles/{id}` | `roles:read` |
| POST | `/api/v1/roles` | `roles:write` |
| PUT | `/api/v1/roles/{id}`, `/api/v1/roles/{id}/permissions` | `roles:write` |
| DELETE | `/api/v1/roles/{id}` | `roles:write` |

## Still to do in Phase 0

- Published Docker image and staging environment
- Scheduled cleanup of expired refresh tokens
