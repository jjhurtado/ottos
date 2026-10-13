# Ottos Backend

Backend for the **Ottos** remittance platform: Java 21 + Spring Boot 3.5, PostgreSQL, modular monolith.
Base package: `org.jobits.ottos`.

## Requirements

- JDK 21
- No Gradle install needed: use the included wrapper (`./gradlew`, or `gradlew.bat` on Windows)
- Docker, for the local PostgreSQL and for the tests; your user must be able to use it without `sudo`
  (`sudo usermod -aG docker $USER`, then log in again)

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

Tests run against a real PostgreSQL 16 (the same as docker-compose) that Testcontainers starts in Docker and removes
afterwards, with the same Flyway migrations, so they need Docker running. `ConcurrencyTest` fires requests at the same
time to check idempotency, locking and cash under races. `ModularityTest` fails if a module uses another module's
internal classes.

## Structure

Every direct package under `org.jobits.ottos` is a module (Spring Modulith). Types in the module's root
package are its public API; sub-packages are internal.

| Module | Package | Phase |
| --- | --- | --- |
| Identity and access (staff users, roles, permissions) | `identity` | 0 (implemented) |
| Branches and zones (provinces, municipalities) | `branches` | 1 (zones implemented; a single branch in 1.0, so no branch management yet) |
| Customers (senders, no login yet) | `customers` | 1 (implemented) |
| Configuration (minimum amount, delivery days, corridors, exchange rates, fee rules) | `configuration` | 1 (implemented) |
| Quotes (calculator over the configuration) | `rates` | 1 (implemented) |
| Mobile sync (offline-first app: download and queued actions) | `sync` | 1 (implemented) |
| Mobile app versions (update notice, outdated builds blocked) | `apps` | 1 (implemented) |
| Beneficiaries | `beneficiaries` | 1 (implemented) |
| Remittances (deliveries and pickups) | `remittances` | 1 (implemented) |
| Dispatch and deliveries (routes, proof photos, assignment by zone) | `dispatch` | after 1.0; manual assignment lives in `remittances` |
| Payments and cash (cash ledger) | `payments` | 1 (ledger implemented); closing and reconciliation in 3 |
| Notifications | `notifications` | 2 |
| Reporting and audit (audit log; KPIs later) | `reporting` | 1 (audit log implemented); KPIs in 4 |

Inside each module: `domain` (entities, repositories), services as an interface plus its `Impl` (in the module root for the
public API, or in `management`, `security` when internal) and `web` (controllers). Conventions for Git, layers and
services: [STANDARDS.md](STANDARDS.md). What each module does: [docs/modules](docs/modules/README.md). Versions: [ROADMAP.md](ROADMAP.md#3-versionado).

## Environment variables

| Variable | Purpose | Default |
| --- | --- | --- |
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/ottos` |
| `DB_USER` / `DB_PASSWORD` | Database credentials (also used by docker compose) | Required |
| `OTTOS_JWT_SECRET` | Base64 HMAC key, at least 32 bytes (`openssl rand -base64 32`) | Required |
| `OTTOS_ADMIN_EMAIL` / `OTTOS_ADMIN_PASSWORD` | Creates the first ADMIN if none exists | empty |
| `PORT` | HTTP port | `8080` |
| `OTTOS_ANDROID_DOWNLOAD_URL` | Where the latest Android APK is downloaded; `{version}` is replaced by its version name | empty: `downloadUrl` null |
| `OTTOS_IOS_DOWNLOAD_URL` | The iOS app's TestFlight link | empty: `downloadUrl` null |
| `OTTOS_TIMEZONE` | The installation's time zone (e.g. `America/Havana`): due dates, late remittances and monthly reports go by its days | Required |
| `OTTOS_CLIENT_IP_HEADER` | `prod` only: header the hosting's proxy sets with the client's real IP, used by the per-IP login limit | `X-Real-Ip` |
| `OTTOS_CORS_ALLOWED_ORIGINS` | Browser origins allowed to call the API, comma-separated (the web panel's domain) | empty: no CORS |
| `SPRING_PROFILES_ACTIVE` | `dev` locally; `prod` in staging and production | — |

**Profile `prod`** (staging and production, `application-prod.yml`): small Tomcat (20 threads) and Hikari (5
connections) pools, Swagger and the OpenAPI document off, health without details, JSON logs (ECS) and graceful
shutdown. JVM flags (heap, GC) are set by the infrastructure through `JAVA_TOOL_OPTIONS`.

Token lifetimes: `ottos.security.access-token-ttl` (15m, web panel), `ottos.security.mobile-access-token-ttl` (14h, mobile
app logging in with `"client": "MOBILE"`) and `ottos.security.refresh-token-ttl` (30d). Retrying a refresh within
`ottos.security.refresh-reuse-grace` (60s) gets a new pair instead of signing out everywhere.

Failed logins are limited (`ottos.security.login.*`): 5 wrong passwords on an email within 15 minutes lock it for 15
minutes, and 50 failures from one IP lock that IP; the answer is 429 `TOO_MANY_LOGIN_ATTEMPTS` with
`retryAfterSeconds` and `Retry-After`. Expired refresh
tokens are deleted daily at `ottos.security.refresh-token-cleanup-cron` (Spring cron, default `0 30 3 * * *`, in
`ottos.timezone`); revoked ones are kept until they expire so reuse is still detected.

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
| Auth | `POST /auth/login` (`client`: `WEB` or `MOBILE`), `/auth/refresh`, `/auth/logout`, `/auth/change-password`; `GET /auth/me` | public / authenticated |
| Staff users | `GET/POST /users`, `PUT /users/{id}`, `/users/{id}/roles`, `/users/{id}/password`, `POST /users/{id}/activate`, `/deactivate` | `users:read`, `users:write` |
| Roles | `GET /permissions`, `GET/POST /roles`, `PUT /roles/{id}`, `/roles/{id}/permissions`, `DELETE /roles/{id}` | `roles:read`, `roles:write` |
| Zones | `GET /provinces`, `GET /municipalities?province=` | authenticated |
| Configuration | `GET /configuration` (everything at once), `GET/PUT /configuration/settings`, `GET /configuration/corridors`, `GET/POST /configuration/corridors/{code}/rates`, `GET/POST /configuration/corridors/{code}/fee-rules` (`remittanceType=DELIVERY|PICKUP`) | `configuration:read`, `configuration:write` |
| Quotes | `POST /quotes` (`remittanceType` defaults to `DELIVERY`) | `rates:read` |
| Customers | `GET/POST /customers?q=` (with `page`/`size`: a page with `totalItems`; contract: `docs/contracts/customer-beneficiary-pages.md`), `GET/PUT /customers/{id}` | `customers:read`, `customers:write` |
| Customer import/export | `GET /customers/export`, `POST /customers/import`, `POST /beneficiaries/import` (without a customer) (`dryRun`; contract: `docs/contracts/customer-transfer.md`) | `customers:export`, `customers:import` (ADMIN) |
| Beneficiaries | `GET /beneficiaries?q=` (with `page`/`size`: a page with `totalItems`), `POST /beneficiaries` (without a customer), `GET/PUT/DELETE /beneficiaries/{id}` (delete deactivates it for every customer), `POST /beneficiaries/{id}/restore`, `GET/POST /customers/{id}/beneficiaries`, `PUT/DELETE /customers/{id}/beneficiaries/{beneficiaryId}` | `customers:read`, `customers:write` |
| Historical import | `POST /remittances/import` (old records, delivered on their date; contract: `docs/contracts/historical-import.md`) | `customers:import` (ADMIN) |
| Void and old remittances | `POST /remittances/{id}/void` (cash reversed), `POST /remittances/historical` (one old remittance or pickup; contract: `docs/contracts/void-and-historical.md`) | `remittances:void`, `remittances:historical` (ADMIN) |
| Remittances | `POST /remittances` (header `Idempotency-Key`), `GET /remittances?status=&type=&late=&incident=&review=&courierId=&customerId=&beneficiaryId=&municipality=&from=&to=`, `GET /remittances/{id}`, `/remittances/code/{code}`, `/remittances/{id}/events`, `GET /remittance-workflow`, `GET /remittances/completed?since=` (feed for notifications; contract: `docs/contracts/completed-feed.md`) | `remittances:create`, `remittances:read` |
| Courier work | `GET /remittances/assigned`, `POST /remittances/{id}/deliver`, `/deliver-with-pin` | `remittances:read-assigned`, `remittances:deliver` |
| Incidents | `POST /remittances/{id}/incidents` (`{reason, note}`) | `remittances:report-incident` |
| Review | `GET /remittances?review=true`, `POST /remittances/{id}/review/resolve` | `remittances:read`, `remittances:review` |
| Reports | `GET /reports/monthly?month=YYYY-MM` (monthly sales report; contract: `docs/contracts/monthly-report.md`) | `reports:read` (ADMIN, SALES) |
| Audit | `GET /audit?targetType=&targetId=&actorId=&action=&from=&to=` (who changed users and roles, and when) | `audit:read` (ADMIN) |
| Mobile sync | `GET /sync/state?since=`, `POST /sync/actions` (contract: `docs/contracts/offline-sync.md`) | authenticated; each action needs its endpoint's permission |
| App versions | `GET /app/version?platform=ANDROID\|IOS`; mobile requests send `X-App-Platform` and `X-App-Build` (contract: `docs/contracts/app-version.md`) | public |
| Workflow | `GET /couriers` (assignable staff and their open remittances: active, with `remittances:deliver`, not administrators), `POST /remittances/{id}/assign`, `/transitions`, `/postpone` | `remittances:assign`, the transition's permission, `remittances:postpone` |
| Statistics | `GET /customers/{id}/stats`, `GET /beneficiaries/{id}/stats` | `remittances:read` |
| Cash | `GET /cash/business`, `/cash/business/movements`, `/cash/couriers`, `/cash/couriers/{id}`, `/cash/remittances/{id}/movements`; `POST /cash/couriers/{id}/funding`, `/returns` | `cash:read`, `cash:write` |
| Own cash | `GET /cash/me` | `cash:read-own` |
| Business box | `POST /cash/business/deposits`, `/withdrawals` | `cash:adjust` |
| Courier exchanges | `POST /cash/me/exchanges`, `POST /cash/couriers/{id}/exchanges`, `GET /cash/exchanges?review=`, `POST /cash/exchanges/{id}/review/resolve` (contract: `docs/contracts/courier-exchange.md`) | `cash:exchange-own`, `cash:write`, `cash:read`, `cash:review` |

All paths start with `/api/v1`. Initial grants: ADMIN has everything; SALES manages customers, reads the configuration, quotes,
remittances (read, create, assign, postpone, report incidents) and courier cash; DELIVERY sees and completes its own
remittances, postpones them, reports incidents on them and sees its own cash. Administrators can change this through the roles API.

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
| 400 | `AMOUNT_BELOW_MINIMUM` (with `minimumAmount`, `currency`), `INCIDENT_NOTE_REQUIRED`, `INVALID_OCCURRED_AT` (sync), `INVALID_CURRENCY`, `INVALID_RATE`, `RATE_NOT_ALLOWED`, `FEE_NOT_ALLOWED`, `VALIDATION_FAILED` (with `errors: [{field, constraint}]`, e.g. `{"field": "email", "constraint": "NotBlank"}`), `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `AMOUNT_TOO_SMALL`, `BENEFICIARY_UNAVAILABLE`, `COURIER_REQUIRED`, `CUSTOMER_UNAVAILABLE`, `FEE_PERCENTAGE_TOO_HIGH`, `FIXED_RATE`, `INCORRECT_CURRENT_PASSWORD`, `INCORRECT_PIN`, `INVALID_AMOUNT`, `INVALID_COURIER`, `INVALID_FEE_RANGE`, `INVALID_PHONE`, `INVALID_POSTPONE_DATE`, `NO_ACTIVE_CORRIDOR`, `NO_BUSINESS_CASH`, `UNKNOWN_MUNICIPALITY`, `UNKNOWN_PERMISSIONS`, `UNKNOWN_ROLES` |
| 401 | `AUTHENTICATION_REQUIRED` (no token, or account no longer active), `INVALID_TOKEN` (malformed or expired: refresh or log in again), `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN` |
| 403 | `FORBIDDEN` (missing permission), `PERMISSION_ESCALATION`, `TRANSITION_FORBIDDEN` |
| 404 | `NOT_FOUND` (unknown route), `BENEFICIARY_NOT_FOUND`, `EXCHANGE_NOT_FOUND`, `CORRIDOR_NOT_FOUND`, `CUSTOMER_NOT_FOUND`, `REMITTANCE_NOT_FOUND`, `ROLE_NOT_FOUND`, `USER_NOT_FOUND` |
| 409 | `REMITTANCE_VOIDED`, `REMITTANCE_ALREADY_VOIDED`, `CONCURRENT_UPDATE` (retry), `ACTION_ID_CONFLICT` (sync), `REVIEW_NOT_PENDING`, `BENEFICIARY_INACTIVE`, `BUILT_IN_ROLE`, `CANNOT_DEACTIVATE_SELF`, `COURIER_NOT_ASSIGNED`, `EMAIL_TAKEN`, `LAST_ADMIN`, `NO_EXCHANGE_RATE`, `NO_FEE_RULE`, `CUSTOMER_ALREADY_REGISTERED` (with `customerId`), `REMITTANCE_ALREADY_COMPLETED`, `REMITTANCE_NOT_ASSIGNABLE`, `REMITTANCE_NOT_COMPLETABLE`, `ROLE_CODE_TAKEN`, `ROLE_IN_USE`, `TRANSITION_NOT_ALLOWED` |
| 426 | `APP_UPDATE_REQUIRED` (with `minimumBuild`, `latestBuild`, `latestVersion`, `downloadUrl`) |
| 429 | `TOO_MANY_LOGIN_ATTEMPTS` (with `retryAfterSeconds` and the `Retry-After` header) |

Other framework errors get the status name as code (e.g. `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`).

## Remittance rules

- **Configuration:** every configurable value lives in the `configuration` module and is managed from one place
  (`GET /configuration`): `minimum_amount` (USD, 0 = no minimum) and `default_delivery_days` in `settings`, and per
  corridor its exchange rates and fee rules, one history for deliveries and another for pickups (`remittance_type`).
  Requests without `remittanceType` mean deliveries. The pickup rate started at 1 (1x1).
- **Quote:** amount ≥ the configured minimum amount, otherwise `AMOUNT_BELOW_MINIMUM` (with `minimumAmount` and
  `currency`); rate and fee rule are those of the corridor for the remittance's kind (delivery or pickup);
  fee = 10 % of the amount, at least 10 USD (a fee rule per corridor and kind, versioned in `fee_rules`);
  total charged = amount + fee; amount to deliver = amount × rate, rounded down to 50 CUP (no rounding for USD-USD).
  Rates (`exchange_rates`) and fee rules are never edited: a change inserts a new row. Each remittance keeps a copy
  of what it used, and of the beneficiary's data.
- **Types:** DELIVERY (the courier hands cash over) and PICKUP (the courier collects USD). Pickup fee, rate and
  totals are visible only with `remittances:read-financials`.
- **Workflow:** statuses and transitions are rows in `remittance_statuses` and `remittance_transitions`; the code
  relies only on the flags `is_initial`, `requires_courier` and `is_final`. Seeded: Paid → Assigned → Delivered.
- **Due date and delays:** expected date = registration day + `default_delivery_days` (2, a configuration setting),
  in Cuba's timezone (`ottos.timezone`). A remittance is late when it is not final and its expected date has
  passed; postponing moves the date with a required reason and is kept in the history.
- **Incidents:** when a delivery or pickup cannot be done, the courier (or Sales) reports a reason — `NOT_HOME`,
  `WRONG_ADDRESS`, `UNREACHABLE`, `REFUSED` or `OTHER` (needs a note). The remittance keeps its status and shows the
  open `incident` (listed with `incident=true`) until someone acts on it: reassign, postpone, complete or any status
  change. Every incident stays in the history (`INCIDENT_REPORTED`).
- **Cash:** a double-entry ledger (`cash_accounts`, `cash_movements`). Registering a delivery adds the total
  charged to the business box; completing it moves the cash out of the courier; completing a pickup moves the
  collected USD into the courier. Couriers may go negative.

## Still to do

- Published Docker image and staging environment
- Cash closing and reconciliation (Phase 3)
