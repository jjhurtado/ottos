# Módulo `reporting` — Reportes y auditoría

Paquete `org.jobits.ottos.reporting`. Auditoría en Fase 1; KPIs y exportes en la Fase 4.

## Para qué sirve

Hoy: el **registro de auditoría** (quién cambió qué y cuándo) y el **informe mensual de ventas**. Más adelante:
otros indicadores y exportes.

## API pública

- `AuditLogService.search(filter, page, size)`.
- `ReportService.monthly(month)`: el informe mensual. Las cifras las calcula `remittances` (`salesSummary`), que es
  dueño de los datos; `reporting` usa su interfaz con nombre `application`.
- Escucha `org.jobits.ottos.AuditEvent` (paquete raíz, compartido): cualquier módulo lo publica y este lo guarda.

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /audit?targetType=&targetId=&actorId=&action=&from=&to=&page=&size=` (más reciente primero) | `audit:read` (solo ADMIN) |
| `GET /reports/monthly?month=AAAA-MM` (contrato: `docs/contracts/monthly-report.md`) | `reports:read` (ADMIN y SALES) |

## Datos

`audit_log` (`V1__baseline.sql`, sección *reporting*): hora, actor (null = el sistema), acción, tipo, id y nombre legible del objetivo, y
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

## Informe mensual de ventas

- Entran las remesas **registradas** en el mes (hora de Cuba), sea cual sea su estado; todo en USD salvo lo entregado,
  que va en su moneda y convertido a USD con la tasa de cada remesa.
- `collected` = monto + comisión; `fees` = comisiones de entregas y recogidas; `difference` = cobrado − entregado en USD
  (comisiones más margen de redondeo). `completed` = completadas antes de terminar el mes; `withIncident`, en el
  momento de pedir el informe.
- El PDF lo genera el panel.

## Dónde tocar

- Auditar algo nuevo: publicar `AuditEvent` con el `ApplicationEventPublisher` dentro de la transacción del cambio,
  con una `action` estable (no se renombra: el frontend puede traducirla).

- Reglas del informe mensual: `RemittanceServiceImpl.salesSummary`; forma de la respuesta: `ReportService`.

## Pendiente

Otros KPIs y exportes (Fase 4). Tasa propia por recogida (cambia cómo se calcula lo entregado de una recogida).
