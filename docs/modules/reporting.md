# Módulo `reporting` — Reportes y auditoría

Paquete `org.jobits.ottos.reporting`. Auditoría en Fase 1; KPIs y exportes en la Fase 4.

## Para qué sirve

Hoy: el **registro de auditoría**, quién cambió qué y cuándo. Más adelante: indicadores, KPIs y exportes.

## API pública

- `AuditLogService.search(filter, page, size)`.
- Escucha `org.jobits.ottos.AuditEvent` (paquete raíz, compartido): cualquier módulo lo publica y este lo guarda.

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /audit?targetType=&targetId=&actorId=&action=&from=&to=&page=&size=` (más reciente primero) | `audit:read` (solo ADMIN) |

## Datos

`audit_log` (`V14__audit_log.sql`): hora, actor (null = el sistema), acción, tipo, id y nombre legible del objetivo, y
`details` (JSON con lo que cambió).

## Reglas

- La entrada se guarda **en la transacción del cambio** (`@EventListener` síncrono): si el cambio se deshace, no queda
  entrada. Las entradas nunca se editan ni se borran.
- `details` nunca contiene secretos (contraseñas, hashes, tokens, PIN): solo que se cambió.
- La consulta añade el nombre actual del actor (`actorName`).

## Qué se audita hoy

Lo publica `identity` (`security.StaffAudit`):

| `targetType` | `action` | `details` |
| --- | --- | --- |
| `USER` | `USER_CREATED` | `name`, `roles` (y `reason` para el primer admin, sin actor) |
| `USER` | `USER_RENAMED` | `before`, `after` |
| `USER` | `USER_ROLES_CHANGED` | `before`, `after` (códigos de rol) |
| `USER` | `USER_DEACTIVATED`, `USER_ACTIVATED`, `USER_PASSWORD_RESET` | — |
| `USER` | `PASSWORD_CHANGED` (la propia, el actor es el usuario) | — |
| `ROLE` | `ROLE_CREATED` | `name`, `description`, `permissions` |
| `ROLE` | `ROLE_DESCRIBED` | `before` y `after` (`name`, `description`) |
| `ROLE` | `ROLE_PERMISSIONS_CHANGED` | `added`, `removed` |
| `ROLE` | `ROLE_DELETED` | `name`, `permissions` |

Las remesas y la caja ya guardan su propio historial con autor (`remittance_events`, `cash_movements`), y las tasas,
comisiones y ajustes guardan quién los cambió; si más adelante se quieren en este registro, basta con publicar un
`AuditEvent` desde ese módulo.

## Dónde tocar

- Auditar algo nuevo: publicar `AuditEvent` con el `ApplicationEventPublisher` dentro de la transacción del cambio,
  con una `action` estable (no se renombra: el frontend puede traducirla).

## Pendiente

KPIs y exportes (Fase 4).
