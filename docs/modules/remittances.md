# Módulo `remittances` — Remesas

Paquete `org.jobits.ottos.remittances`. Fase 1. Es el núcleo del negocio.

## Para qué sirve

Registrar remesas ya cobradas, asignarlas a repartidores, posponerlas, reportar incidencias y completarlas, con su
historial y estadísticas.

- **DELIVERY (entrega):** el repartidor entrega el dinero al beneficiario.
- **PICKUP (recogida):** el repartidor recoge USD del beneficiario.

## API pública

- `RemittanceType`, `IncidentReason`, `ReviewReason` y los eventos de `RemittanceEvents` (`RemittanceRegistered`,
  `RemittanceCompleted`), que escucha `payments` para mover la caja en la misma transacción.
- Interfaz con nombre `application` (`RemittanceService`, `RemittanceView`, `Viewer`, tipos de sincronización), usada
  solo por el módulo `sync`.

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `POST /remittances` (cabecera `Idempotency-Key`) | `remittances:create` |
| `GET /remittances?status=&type=&late=&incident=&courierId=&customerId=&beneficiaryId=&municipality=&from=&to=`, `GET /remittances/code/{code}` | `remittances:read` |
| `GET /remittances/{id}`, `/remittances/{id}/events`, `GET /remittance-workflow` | `remittances:read` o `read-assigned` |
| `GET /remittances/completed?since=` (completadas que el servidor registró después de `since`, para las notificaciones de Ventas; contrato `docs/contracts/completed-feed.md`) | `remittances:read` |
| `GET /remittances/assigned` (las mías, como repartidor) | `remittances:read-assigned` |
| `GET /couriers` (personal asignable con sus remesas abiertas: activo, con `remittances:deliver` y sin ser administrador), `POST /remittances/{id}/assign` | `remittances:assign` |
| `POST /remittances/{id}/deliver`, `/deliver-with-pin` | `remittances:deliver` |
| `POST /remittances/{id}/incidents` | `remittances:report-incident` |
| `POST /remittances/{id}/postpone` | `remittances:postpone` |
| `POST /remittances/{id}/review/resolve` (con nota) | `remittances:review` |
| `POST /remittances/{id}/transitions` | el permiso que exija la transición |
| `GET /customers/{id}/stats`, `GET /beneficiaries/{id}/stats` | `remittances:read` |

## Datos

`remittances`, `remittance_events`, `remittance_statuses`, `remittance_transitions`, `synced_actions`
(`V1__baseline.sql`, sección *remittances*).

## Reglas

- **Registro:** cliente y beneficiario activos y vinculados; cotiza con `QuoteService` según el tipo y guarda una copia
  de la cotización y del beneficiario. Genera código `OT-XXXXXX` y PIN de 6 dígitos. La misma `Idempotency-Key`
  devuelve la primera remesa, también con peticiones simultáneas.
- **Workflow configurable:** estados y transiciones son filas; el código solo usa las marcas `is_initial`,
  `requires_courier` e `is_final`. Sembrado: Pagada → Asignada → Entregada. Los nombres de estado están en español en la
  BD y son solo respaldo: el frontend traduce por código y tipo (`DELIVERED` = Entregada / Recogida).
- **Recogidas:** se hacen cuando el negocio quiere efectivo, normalmente 1x1 y para montos grandes, sin ganancia (la
  comisión de recogida se configura en 0). Al registrarlas se puede indicar la tasa del momento (`rate`); la remesa
  guarda `rateOverridden` (contrato `docs/contracts/pickup-rate.md`). Las entregas siempre usan la tasa configurada.
- **Asignar:** el repartidor debe estar activo y tener `remittances:deliver` (la misma regla que lista `GET /couriers`).
- **Completar:** exige repartidor asignado. Los repartidores pueden completar sin PIN (`/deliver`) o con PIN
  (`/deliver-with-pin`); es una decisión de producto.
- **Atraso:** fecha prevista = día de registro + `default_delivery_days` (configuración), en hora de Cuba. Atrasada si no
  es final y pasó la fecha. Posponer exige una fecha posterior y un motivo.
- **Incidencias:** motivo (`NOT_HOME`, `WRONG_ADDRESS`, `UNREACHABLE`, `REFUSED`, `OTHER` con nota obligatoria). La
  remesa sigue en su estado con la incidencia abierta hasta que alguien actúa (reasignar, posponer, completar o cambiar
  de estado); cada incidencia queda en el historial.
- **Visibilidad (`Viewer`):** `remittances:read` ve todo; `read-assigned` solo las suyas. El PIN solo con
  `remittances:read`. Comisión, tasa y totales de las recogidas solo con `remittances:read-financials`.
- **Concurrencia:** `@Version` en `Remittance`; dos cambios simultáneos dan 409 `CONCURRENT_UPDATE`.
- **Revisión:** una remesa puede quedar marcada (`review`: `DELIVERED_AFTER_REASSIGNMENT`, `PIN_MISMATCH`,
  `DUPLICATE_DELIVERY`, `LATE_SYNC`) por una acción sincronizada desde la app móvil. Se lista con `review=true` y se
  resuelve con una nota; ambos pasos quedan en el historial (`REVIEW_FLAGGED`, `REVIEW_RESOLVED`).
- **Acciones sincronizadas** (`applySynced`, usado por el módulo `sync`): cada una en su transacción, a la hora
  `occurredAt` (también en la caja). El id de la acción se guarda en `synced_actions` junto con su efecto: reenviarla da
  `ALREADY_APPLIED`. `occurredAt` no puede ser futuro (más de 5 min) ni anterior a la remesa. Una entrega de quien tenía
  la remesa a esa hora se acepta aunque la hayan reasignado (y pasa a ser suya); una segunda entrega se rechaza y marca
  la remesa; un PIN incorrecto se rechaza con `strictPin` o se marca; las entregas de hace más de 7 días se marcan, el
  resto de acciones tan antiguas se rechaza. Posponer o reportar incidencias exige que la remesa sea suya ahora.

## Dónde tocar

- Estado nuevo del workflow: filas en `remittance_statuses` y `remittance_transitions` (migración), sin código.
- Reglas de registro, asignación, completado e incidencias: `RemittanceServiceImpl`.
- Qué ve cada usuario: `Viewer` y `RemittanceViews`.
- Reglas de las acciones sincronizadas: `RemittanceServiceImpl.applySynced` (contrato en
  `docs/contracts/offline-sync.md`).

## Pendiente

Asignación por zona fuera de 1.0.
