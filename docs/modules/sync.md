# Módulo `sync` — Sincronización de la app móvil

Paquete `org.jobits.ottos.sync`. Fase 1. Contrato completo: [docs/contracts/offline-sync.md](../contracts/offline-sync.md).

## Para qué sirve

La app móvil (Ventas y repartidores, de día) es offline-first: guarda en el teléfono lo que necesita, registra las
acciones en una cola local y las sincroniza cuando hay red. Este módulo sirve esa descarga y aplica esa cola. No tiene
datos propios: orquesta `remittances` y `payments`.

## API pública

- `SyncService`: `state(since, viewer)` y `apply(deviceId, actions, viewer)`.

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /sync/state[?since=<serverTime>]` | autenticado (remesas con `remittances:read-assigned`, caja con `cash:read-own`) |
| `POST /sync/actions` | autenticado; cada acción exige el permiso de su endpoint online |

## Reglas

- **Descarga:** remesas abiertas asignadas al usuario, ids a quitar (reasignadas o cerradas), su caja y el workflow.
  Completa sin `since` o si es de hace más de 7 días; si no, solo lo cambiado (columna `remittances.updated_at`).
  `serverTime` es el `since` de la próxima vez. Ventas recibe listas vacías: trabaja en la app solo con conexión.
- **Lote:** las acciones (`DELIVER`, `DELIVER_WITH_PIN`, `POSTPONE`, `REPORT_INCIDENT` y `CASH_EXCHANGE`) se aplican
  en orden, cada una en su propia transacción (`RemittanceService.applySynced` o
  `CashLedgerService.applySyncedExchange`); este servicio no es transaccional a propósito. Un `payload` mal formado
  rechaza solo su acción.
- La idempotencia, la hora real (`occurredAt`), los conflictos y las marcas de revisión viven en `remittances` (ver
  su documento).

## Dependencias

Usa los paquetes `remittances.application` y `payments.application`, expuestos como interfaces con nombre
(`@NamedInterface("application")`) solo para este módulo.

## Dónde tocar

- Tipos de acción y su validación: `SyncServiceImpl.toSyncAction` y `remittances.application.SyncActionType`.
- Reglas de aplicación y conflictos: `RemittanceServiceImpl.applySynced`.
- Duración de la sesión móvil y margen de refresh: `identity` (`ottos.security.mobile-access-token-ttl`,
  `ottos.security.refresh-reuse-grace`).
