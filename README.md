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
| Branches and zones (provinces, municipalities) | `branches` | 1 (zones implemented; branches later) |
| Customers (senders, no login yet) | `customers` | 1 (implemented) |
| Rates and fees | `rates` | 1 (implemented) |
| Beneficiaries | `beneficiaries` | 1 (implemented) |
| Remittances (deliveries and pickups) | `remittances` | 1 (implemented) |
| Dispatch and deliveries (routes, proof photos) | `dispatch` | later; assignment lives in `remittances` for now |
| Payments and cash (cash ledger) | `payments` | 1 (ledger implemented); closing and reconciliation in 3 |
| Notifications | `notifications` | 2 |
| Reporting and audit | `reporting` | 4 |

Inside each module: `domain` (entities, repositories), services as an interface plus its `Impl` (in the module root for the
public API, or in `application`, `management`, `security`) and `web` (controllers). Conventions for Git, layers and
services: [STANDARDS.md](STANDARDS.md). Versions: [ROADMAP.md](ROADMAP.md#versionado).

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

Full contract in Swagger UI (`/swagger-ui.html`). Summary by area, with the permission each needs:

| Area | Main endpoints | Permissions |
| --- | --- | --- |
| Auth | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/change-password`; `GET /auth/me` | public / authenticated |
| Staff users | `GET/POST /users`, `PUT /users/{id}`, `/users/{id}/roles`, `/users/{id}/password`, `POST /users/{id}/activate`, `/deactivate` | `users:read`, `users:write` |
| Roles | `GET /permissions`, `GET/POST /roles`, `PUT /roles/{id}`, `/roles/{id}/permissions`, `DELETE /roles/{id}` | `roles:read`, `roles:write` |
| Zones | `GET /provinces`, `GET /municipalities?province=` | authenticated |
| Rates and fees | `GET /corridors`, `GET/POST /corridors/{code}/rates`, `GET/POST /corridors/{code}/fee-rules`, `POST /quotes` | `rates:read`, `rates:write` |
| Customers | `GET/POST /customers?q=`, `GET/PUT /customers/{id}` | `customers:read`, `customers:write` |
| Beneficiaries | `GET /beneficiaries?q=`, `GET/PUT/DELETE /beneficiaries/{id}` (delete deactivates it for every customer), `POST /beneficiaries/{id}/restore`, `GET/POST /customers/{id}/beneficiaries`, `PUT/DELETE /customers/{id}/beneficiaries/{beneficiaryId}` | `customers:read`, `customers:write` |
| Remittances | `POST /remittances` (header `Idempotency-Key`), `GET /remittances?status=&type=&late=&courierId=&customerId=&beneficiaryId=&municipality=&from=&to=`, `GET /remittances/{id}`, `/remittances/code/{code}`, `/remittances/{id}/events`, `GET /remittance-workflow` | `remittances:create`, `remittances:read` |
| Courier work | `GET /remittances/assigned`, `POST /remittances/{id}/deliver`, `/deliver-with-pin` | `remittances:read-assigned`, `remittances:deliver` |
| Workflow | `GET /couriers` (assignable staff and their open remittances), `POST /remittances/{id}/assign`, `/transitions`, `/postpone` | `remittances:assign`, the transition's permission, `remittances:postpone` |
| Statistics | `GET /customers/{id}/stats`, `GET /beneficiaries/{id}/stats` | `remittances:read` |
| Cash | `GET /cash/business`, `/cash/business/movements`, `/cash/couriers`, `/cash/couriers/{id}`, `/cash/remittances/{id}/movements`; `POST /cash/couriers/{id}/funding`, `/returns` | `cash:read`, `cash:write` |
| Own cash | `GET /cash/me` | `cash:read-own` |
| Business box | `POST /cash/business/deposits`, `/withdrawals` | `cash:adjust` |

All paths start with `/api/v1`. Initial grants: ADMIN has everything; SALES manages customers, rates (read),
remittances (read, create, assign, postpone) and courier cash; DELIVERY sees and completes its own remittances,
postpones them and sees its own cash. Administrators can change this through the roles API.

## Errors

Every error is a ProblemDetail (`application/problem+json`) with a stable `code`; the English `detail` is for people
reading logs. Clients branch on and translate `code`. Codes are part of the contract: never rename one, add a new one.

```json
{"type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Amount too small: it rounds down to 0 CUP to deliver",
 "instance": "/api/v1/remittances", "code": "AMOUNT_TOO_SMALL"}
```

Throw `ApiException.badRequest("SOME_CODE", "English detail")` (or `conflict`, `notFound`, `forbidden`,
`unauthorized`); `ApiErrors` gives codes to the errors Spring raises.

| Status | Codes |
| --- | --- |
| 400 | `VALIDATION_FAILED` (with `errors: [{field, constraint}]`, e.g. `{"field": "email", "constraint": "NotBlank"}`), `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `AMOUNT_TOO_SMALL`, `BENEFICIARY_NOT_LINKED`, `BENEFICIARY_UNAVAILABLE`, `COURIER_REQUIRED`, `CUSTOMER_UNAVAILABLE`, `FEE_PERCENTAGE_TOO_HIGH`, `FIXED_RATE`, `INCORRECT_CURRENT_PASSWORD`, `INCORRECT_PIN`, `INVALID_AMOUNT`, `INVALID_COURIER`, `INVALID_FEE_RANGE`, `INVALID_PHONE`, `INVALID_POSTPONE_DATE`, `NO_ACTIVE_CORRIDOR`, `NO_BUSINESS_CASH`, `UNKNOWN_MUNICIPALITY`, `UNKNOWN_PERMISSIONS`, `UNKNOWN_ROLES` |
| 401 | `AUTHENTICATION_REQUIRED` (no token, or account no longer active), `INVALID_TOKEN` (malformed or expired: refresh or log in again), `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` |
| 403 | `FORBIDDEN` (missing permission), `PERMISSION_ESCALATION`, `TRANSITION_FORBIDDEN` |
| 404 | `NOT_FOUND` (unknown route), `BENEFICIARY_NOT_FOUND`, `CORRIDOR_NOT_FOUND`, `CUSTOMER_NOT_FOUND`, `REMITTANCE_NOT_FOUND`, `ROLE_NOT_FOUND`, `USER_NOT_FOUND` |
| 409 | `CONCURRENT_UPDATE` (retry), `BENEFICIARY_INACTIVE`, `BUILT_IN_ROLE`, `CANNOT_DEACTIVATE_SELF`, `COURIER_NOT_ASSIGNED`, `EMAIL_TAKEN`, `LAST_ADMIN`, `NO_EXCHANGE_RATE`, `NO_FEE_RULE`, `PHONE_ALREADY_REGISTERED`, `REMITTANCE_ALREADY_COMPLETED`, `REMITTANCE_NOT_ASSIGNABLE`, `REMITTANCE_NOT_COMPLETABLE`, `ROLE_CODE_TAKEN`, `ROLE_IN_USE`, `TRANSITION_NOT_ALLOWED` |

Other framework errors get the status name as code (e.g. `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`).

## Remittance rules

- **Quote:** fee = 10 % of the amount, at least 10 USD (a fee rule per corridor, versioned in `fee_rules`);
  total charged = amount + fee; amount to deliver = amount × rate, rounded down to 50 CUP (no rounding for USD-USD).
  Rates (`exchange_rates`) and fee rules are never edited: a change inserts a new row. Each remittance keeps a copy
  of what it used, and of the beneficiary's data.
- **Types:** DELIVERY (the courier hands cash over) and PICKUP (the courier collects USD). Pickup fee, rate and
  totals are visible only with `remittances:read-financials`.
- **Workflow:** statuses and transitions are rows in `remittance_statuses` and `remittance_transitions`; the code
  relies only on the flags `is_initial`, `requires_courier` and `is_final`. Seeded: Paid → Assigned → Delivered.
- **Due date and delays:** expected date = registration day + `default_delivery_days` (2, in `remittance_settings`),
  in Cuba's timezone (`ottos.timezone`). A remittance is late when it is not final and its expected date has
  passed; postponing moves the date with a required reason and is kept in the history.
- **Cash:** a double-entry ledger (`cash_accounts`, `cash_movements`). Registering a delivery adds the total
  charged to the business box; completing it moves the cash out of the courier; completing a pickup moves the
  collected USD into the courier. Couriers may go negative.

## Still to do

- Published Docker image and staging environment
- Scheduled cleanup of expired refresh tokens
- Cash closing and reconciliation (Phase 3)
