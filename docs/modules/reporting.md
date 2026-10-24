# Módulo `reporting` — Reportes y auditoría

Paquete `org.jobits.ottos.reporting`. Auditoría en Fase 1; KPIs y exportes en la Fase 4.

## Para qué sirve

Hoy: el **registro de auditoría** (quién cambió qué y cuándo), el **informe mensual de ventas** con su detalle y las
**estadísticas de ventas**. Más adelante: otros indicadores y exportes.

## API pública

- `AuditLogService.search(filter, page, size)`.
- `ReportService.monthly(month)`, `monthlyRemittances(month)` y `statistics(from, to)`: el informe mensual, su detalle
  y las estadísticas. Las cifras las calcula `remittances` (`salesSummary`, `salesDetail`), que es
  dueño de los datos.
- Escucha `org.jobits.ottos.AuditEvent` (paquete raíz, compartido): cualquier módulo lo publica y este lo guarda.

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /audit?targetType=&targetId=&actorId=&action=&from=&to=&page=&size=` (más reciente primero) | `audit:read` (solo ADMIN) |
| `GET /reports/monthly?month=AAAA-MM` (contrato: `docs/contracts/monthly-report.md`) | `reports:read` (ADMIN y SALES) |
| `GET /reports/monthly/remittances?month=AAAA-MM` (detalle del informe, una fila por remesa, anuladas incluidas, sin datos personales; mismo contrato, sección «Detalle») | `reports:read` |
| `GET /reports/statistics?from=AAAA-MM-DD&to=AAAA-MM-DD` (≤ 366 días; contrato: `docs/contracts/statistics.md`) | `reports:read` |

## Datos

`audit_log` (esquema `reporting`, `V1__baseline.sql`, sección *reporting*): hora, actor (null = el sistema), acción, tipo, id y nombre legible del objetivo, y
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
- El PDF lo genera el panel, como el CSV y el PDF del detalle.

## Detalle y estadísticas

- Detalle: todas las remesas registradas en el mes, también las anuladas (con `voided`), por `createdAt` y `code`, sin
  paginar y sin datos personales (ni nombres, teléfonos, dirección, PIN, notas, motivo de por la casa ni `createdBy`);
  con municipio y repartidor. Las no anuladas suman lo mismo que el informe.
- Estadísticas: por semana ISO, mes, día de la semana y municipio, sin anuladas, en hora de Cuba; semanas y meses
  recortados al periodo con `partial`. Cada agrupación suma `totals`, y un mes entero da lo mismo que el informe.

## Dónde tocar

- Auditar algo nuevo: publicar `AuditEvent` con el `ApplicationEventPublisher` dentro de la transacción del cambio,
  con una `action` estable (no se renombra: el frontend puede traducirla).

- Reglas del informe mensual: cada fila en `remittances.SaleFigures` (compartido por el resumen, el detalle y las
  estadísticas), las sumas en `remittances.SalesSummaries`, las filas del detalle en `remittances.SalesLines`, las
  agrupaciones de las estadísticas en `reporting.SalesStatistics`; forma de las respuestas: `ReportService`.

## Pendiente

Entregas por repartidor en las estadísticas; columna del vendedor en el detalle. Otros KPIs y exportes (Fase 4). Tasa propia por recogida (cambia cómo se calcula lo entregado de una recogida).
