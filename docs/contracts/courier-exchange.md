# Contrato: cambio de moneda del repartidor

Estado: **cerrado con el frontend e implementado** (2026-10-11; módulo `payments`, migración `V2__courier_exchanges.sql`). Contexto en
[ROADMAP.md › Funcionalidades pedidas en diseño](../../ROADMAP.md).

El negocio trabaja en USD. Si hay que entregar en CUP, el repartidor cambia los USD durante el viaje a la tasa que
consigue en ese momento, lo registra en la app (con o sin conexión) y su caja se actualiza al instante. Un cambio
mueve dinero solo dentro de la caja del repartidor: sale una moneda y entra otra. La caja del negocio no cambia.

Todas las rutas empiezan por `/api/v1`. Errores: ProblemDetail con `code` (README › Errors).

## 1. Registrar un cambio

```
POST /cash/me/exchanges                       el repartidor, sobre su propia caja      permiso cash:exchange-own
POST /cash/couriers/{courierId}/exchanges     Ventas o Admin, en nombre de un repartidor   permiso cash:write
```

```json
{ "fromCurrency": "USD", "fromAmount": 100, "toCurrency": "CUP", "rate": 480, "note": "Cambio en el agro de 23" }
```

| Campo | Regla |
| --- | --- |
| `fromCurrency`, `toCurrency` | Monedas distintas que la caja maneja (hoy `USD` y `CUP`), y una de las dos es `USD`; si no, `INVALID_CURRENCY` |
| `fromAmount` | > 0, máximo 2 decimales (`INVALID_AMOUNT`) |
| `rate` | Siempre **unidades de la otra moneda por 1 USD**, en los dos sentidos, como se cotiza en Cuba ("480 CUP por 1 USD"). > 0, máximo 6 decimales (`INVALID_RATE`) |
| `note` | Opcional, ≤ 1000 |

- `toAmount` lo calcula el servidor, redondeado a 2 decimales (mitad hacia arriba): USD → CUP `fromAmount × rate`;
  CUP → USD `fromAmount ÷ rate` (100 000 CUP a 480 = 208,33 USD). La app muestra el mismo cálculo y pide siempre
  "Tasa (CUP por 1 USD)"; el servidor no acepta un `toAmount` del cliente.
- Se puede cambiar más de lo que hay: la caja de un repartidor ya puede quedar en negativo y se marca como tal.
- Respuesta 201 con el cambio (`ExchangeView`, sección 3).

### Movimientos en caja

Dos movimientos con el mismo `exchangeId`, en la fecha del cambio:

| Tipo | De | A | Importe |
| --- | --- | --- | --- |
| `COURIER_EXCHANGE_OUT` | caja del repartidor (`fromCurrency`) | exterior (`fromCurrency`) | `fromAmount` |
| `COURIER_EXCHANGE_IN` | exterior (`toCurrency`) | caja del repartidor (`toCurrency`) | `toAmount` |

`MovementView` (estado de caja del repartidor, `/cash/me`, `/cash/couriers/{id}`) gana `exchangeId` y `rate` (null en
los demás movimientos), y las dos patas llevan la nota del cambio en `note`: así se muestra "Cambio USD → CUP a 480"
sin otra petición.

## 2. Tasa inusual → revisión

- Se compara `rate` tal cual (sin invertir, en los dos sentidos) con la tasa de **entrega** vigente del corredor
  `USD-<otra moneda>` (hoy `USD-CUP`). Sin tasa configurada no se compara.
- Si se aleja más que el ajuste nuevo `exchangeRateTolerancePercent` (Configuración, `GET/PUT /configuration/settings`;
  20 por defecto), el cambio **se registra igual** y queda marcado para revisión con motivo `UNUSUAL_EXCHANGE_RATE`.
- Igual que en las remesas: `review = { reason, flaggedAt }` en el cambio; el panel lista los marcados y los resuelve
  con una nota obligatoria.

```
GET  /cash/exchanges?courierId=&review=&from=&to=&page=&size=   permiso cash:read   (más reciente primero; panel)
POST /cash/exchanges/{id}/review/resolve  { "note": "..." }      permiso cash:review (ADMIN y SALES), nuevo
```

`REVIEW_NOT_PENDING` si no hay nada que resolver.

## 3. `ExchangeView`

```json
{
  "id": "…", "courierId": "…", "courierName": "…",
  "fromCurrency": "USD", "fromAmount": 100.00, "toCurrency": "CUP", "toAmount": 48000.00, "rate": 480,
  "configuredRate": 410, "deviationPercent": 17.07,
  "note": "…", "occurredAt": "…", "recordedBy": "…",
  "review": null
}
```

- `configuredRate` y `deviationPercent`: la tasa con la que se comparó y cuánto se alejó (null si no había tasa).
- `recordedBy`: quién lo registró (el propio repartidor, o Ventas/Admin en su nombre).
- `review`: `null` si nunca se marcó, o `{ "reason", "flaggedAt", "resolvedAt", "resolvedBy", "resolvedNote" }`. Está
  pendiente mientras `resolvedAt` es `null`; al resolverlo se rellenan los tres campos y la marca queda como historial.
  `reason`: `UNUSUAL_EXCHANGE_RATE`, o `LATE_SYNC` si se sincronizó más de 7 días después.

El repartidor ve sus cambios como movimientos de su caja (`/cash/me`); no necesita `/cash/exchanges`.

## 4. Sin conexión

Acción nueva en `POST /sync/actions` (contrato en [offline-sync.md](offline-sync.md)):

```json
{ "id": "UUIDv4", "type": "CASH_EXCHANGE", "remittanceId": null, "occurredAt": "…",
  "payload": { "fromCurrency": "USD", "fromAmount": 100, "toCurrency": "CUP", "rate": 480, "note": "…" } }
```

- `remittanceId` es `null` (no hay remesa). Mismo id como clave de idempotencia. `occurredAt` no puede ser más de
  5 minutos futuro; si es de hace más de 7 días, el cambio se aplica igualmente (el dinero ya cambió de manos) y queda
  marcado `LATE_SYNC`, salvo que ya lo esté por `UNUSUAL_EXCHANGE_RATE`.
- Un `payload` incompleto (faltan `fromCurrency`, `fromAmount`, `toCurrency` o `rate`) rechaza solo esa acción con
  `VALIDATION_FAILED`.
- Resultado: `APPLIED` / `ALREADY_APPLIED` con `exchange: ExchangeView` (y `remittance: null`), o `REJECTED` con su
  `code`. `flaggedForReview: true` si la tasa fue inusual.
- La tasa se compara con la configurada **a la hora del cambio** (`occurredAt`), no a la de la sincronización.
- `GET /sync/state` ya trae la caja (`cash`), con los cambios aplicados.

## 5. Permisos nuevos

| Permiso | Para qué | Roles iniciales |
| --- | --- | --- |
| `cash:exchange-own` | Registrar cambios en la propia caja | DELIVERY, ADMIN |
| `cash:review` | Resolver cambios marcados para revisión | ADMIN, SALES |

Registrar un cambio en nombre de un repartidor usa `cash:write` (ya lo tienen ADMIN y SALES).

## 6. Auditoría

No va al registro de auditoría: cada movimiento de caja ya guarda quién lo hizo y cuándo, y la revisión y su nota
quedan en el propio cambio.

## Códigos nuevos

`INVALID_CURRENCY`, `INVALID_RATE`, `EXCHANGE_NOT_FOUND`. Motivos de revisión de un cambio: `UNUSUAL_EXCHANGE_RATE`,
`LATE_SYNC`.
