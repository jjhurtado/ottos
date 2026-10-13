# Contrato: anular remesas y registrar remesas antiguas a mano

Estado: **implementado en develop** (2026-10-13; migraciones `V4__void_remittances.sql` y
`V5__fix_imported_pickups.sql`). Decisiones del usuario: se puede anular cualquier remesa en cualquier estado; la caja
se corrige sola; las remesas antiguas se registran a mano en USD o CUP (recogidas solo en USD); ambas funciones son
solo del ADMIN.

## 1. Anular una remesa

```
POST /api/v1/remittances/{id}/void      permiso remittances:void (ADMIN)
{ "reason": "Era una entrega de efectivo, no una remesa" }
```

- `reason` obligatorio, ≤ 1000. Responde 200 con el `RemittanceView` ya anulado.
- Cualquier remesa, en cualquier estado. Queda guardada (no se borra) con quién, cuándo y por qué:
  `RemittanceView.voided = { at, by, byName, reason }` (`null` si está vigente).
- **Caja:** cada movimiento que causó la remesa se deshace con su espejo, `REMITTANCE_VOID_REVERSAL` (mismo monto,
  cuentas al revés, nota "Anulación de OT-…"). Una entrega anulada devuelve el efectivo a la caja del repartidor; una
  recogida anulada lo saca. `GET /cash/remittances/{id}/movements` devuelve también los espejos. Una remesa sin
  movimientos (pagada sin entregar, o importada) no genera ninguno.
- **Deja de contar** en el informe mensual, las estadísticas de clientes y beneficiarios, las listas de trabajo
  (atrasadas, incidencias, revisión; la incidencia o revisión pendiente se cierra), las remesas asignadas del
  repartidor y su sincronización (desaparece del teléfono), el recuento de remesas abiertas de `/couriers` y el aviso de
  remesas completadas.
- **Se sigue viendo** en su detalle, en su historial (evento `VOIDED` con el motivo) y en `GET /remittances`. Filtro
  nuevo: `voided=true` (solo anuladas) o `voided=false` (sin anuladas).
- Una remesa anulada no se puede cambiar: asignar, entregar, atrasar, reportar incidencias, resolver revisiones o
  sincronizar acciones sobre ella da 409 `REMITTANCE_VOIDED`. Anularla otra vez: 409 `REMITTANCE_ALREADY_VOIDED`.
- Auditoría: `REMITTANCE_VOIDED`, con `{code, reason}`.

## 2. Registrar una remesa antigua a mano

```
POST /api/v1/remittances/historical      permiso remittances:historical (ADMIN)
```

```json
{ "type": "DELIVERY", "customerId": "…", "beneficiaryId": "…", "occurredAt": "2025-03-10T10:00:00",
  "targetCurrency": "CUP", "amount": 100, "fee": 10, "rate": 300, "amountToDeliver": 30000, "notes": "…" }
```

| Campo | Regla |
| --- | --- |
| `type` | `DELIVERY` o `PICKUP` |
| `customerId`, `beneficiaryId` | Obligatorios y existentes (`CUSTOMER_UNAVAILABLE`, `BENEFICIARY_UNAVAILABLE`); el cliente real o el comodín |
| `occurredAt` | Fecha y hora de Cuba, sin zona; no futura (`INVALID_OCCURRED_AT`) |
| `targetCurrency` | `USD` (por defecto) o `CUP`. Una recogida, solo `USD` |
| `amount` | USD enviados (entrega) o recogidos (recogida), > 0 |
| `fee` | USD, ≥ 0 (en recogidas, normalmente 0) |
| `rate`, `amountToDeliver` | Obligatorios en CUP (la tasa y los CUP entregados, como en el ticket); en USD se ignoran (1 y = `amount`) |
| `notes` | Opcional, ≤ 1000 |

- Responde 201 con el `RemittanceView`: completada en esa fecha, sin repartidor, **sin mover ninguna caja**.
- Usa como referencia la tasa y la regla de comisión configuradas del corredor (para CUP, debe haber tasa USD-CUP
  configurada; si no, 409 `NO_EXCHANGE_RATE`); las cifras guardadas son las del ticket.
- Auditoría: `HISTORICAL_REMITTANCE_CREATED`, con `{code, type, amount, fee, currency, occurredAt}`.

## 3. Corrección de datos (`V5__fix_imported_pickups.sql`)

Tres tickets importados eran recogidas: `chat-20261006-210318` (2.500 USD), `chat-20260609-132726` (5.000 USD) y
`chat-20260731-111256` (1.000 USD). La migración anula cada uno (motivo "Era una recogida importada como remesa por
error…") y lo registra de nuevo como recogida en la misma fecha, comisión 0, con clave `historical:<ref>:pickup`. Deja
las mismas entradas de auditoría que la API. Solo actúa donde esos tickets existen y no están anulados.

## Códigos nuevos

`REMITTANCE_VOIDED`, `REMITTANCE_ALREADY_VOIDED`. Permisos nuevos: `remittances:void`, `remittances:historical`.
