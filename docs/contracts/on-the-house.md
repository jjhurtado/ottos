# Contrato: remesas «por la casa» (sin comisión)

Estado: **en develop** (2026-10-23; migración `V3`). Decisión del usuario: el negocio puede enviar remesas libres de
comisión a familiares u otras personas que decida, marcando una casilla «por la casa». Todo queda auditado y se ve en
el panel web y en las apps iOS y Android, sin exigir actualizar la app.

Acordado con el usuario:

- Lo marcan quienes tengan el permiso `remittances:on-the-house`, que se da o se quita desde Roles. La `V3` se lo da
  solo a ADMIN; a Ventas se lo concede el usuario a mano desde Roles (no hay script de datos ni va en el seed).
- El motivo es obligatorio.
- Solo entregas (`DELIVERY`). En una recogida basta con poner la comisión a mano en 0, como hasta ahora.
- La tasa y el monto mínimo se aplican como siempre. Solo cambia la comisión.
- Se marca al registrar y no se puede marcar ni desmarcar después: si hay un error, se anula la remesa y se registra
  otra.
- El repartidor ve la insignia si tiene la app actualizada; una app antigua ignora el campo.
- El informe mensual muestra un bloque «por la casa».

## `POST /api/v1/remittances`

Dos campos nuevos, opcionales:

```json
{ "type": "DELIVERY", "customerId": "…", "beneficiaryId": "…", "amount": 200, "targetCurrency": "CUP",
  "onTheHouse": true, "onTheHouseReason": "Su madre" }
```

| Campo | Regla |
| --- | --- |
| `onTheHouse` | `true` registra la entrega por la casa. Ausente, `null` o `false`: remesa normal, exactamente como antes |
| `onTheHouseReason` | Obligatorio con `onTheHouse: true`, ≤ 500 caracteres; se guarda sin espacios al principio ni al final. Con `onTheHouse` falso o ausente se ignora |

Con `onTheHouse: true` la remesa se cotiza como siempre (tasa de entrega configurada, monto mínimo, redondeo de la
entrega), pero:

- `fee` = 0 y `total` = `amount`: al cliente no se le cobra comisión.
- `waivedFee` = la comisión que habría aplicado la regla configurada, con su mínimo y su máximo (p. ej. 37 USD con la
  regla «10 %, mínimo 10 USD» da `waivedFee: 10.00`). La remesa guarda también la regla (`fee_rule_id`).
- `amountToDeliver` y `cashAmount` no cambian: el beneficiario recibe lo mismo que en una remesa normal.

La `Idempotency-Key` funciona igual: repetirla devuelve la primera remesa.

### Errores

En este orden:

| Caso | Respuesta |
| --- | --- |
| `onTheHouse: true` sin el permiso `remittances:on-the-house` | 403 `ON_THE_HOUSE_NOT_ALLOWED` |
| `onTheHouse: true` en una recogida (`PICKUP`) | 400 `ON_THE_HOUSE_DELIVERY_ONLY` |
| `onTheHouse: true` con `fee` | 400 `FEE_NOT_ALLOWED` (el mismo código que una entrega con `fee`) |
| `onTheHouse: true` sin motivo, o solo con espacios | 400 `ON_THE_HOUSE_REASON_REQUIRED` |
| Motivo de más de 500 caracteres | 400 `VALIDATION_FAILED` |
| Monto por debajo del mínimo | 400 `AMOUNT_BELOW_MINIMUM`, como siempre |

## `POST /api/v1/quotes`

Campo nuevo opcional `onTheHouse` (permiso `rates:read`, como hasta ahora; la cotización no exige el permiso de por la
casa, solo lo muestra):

```json
{ "sourceCurrency": "USD", "targetCurrency": "CUP", "amount": 37, "onTheHouse": true }
```

Responde como siempre, con `fee: 0`, `total` = `amount` y los campos nuevos `onTheHouse: true` y `waivedFee`. Sin
`onTheHouse` (o con `false`) la respuesta es la de siempre, con `onTheHouse: false` y `waivedFee: null`. Errores:
`ON_THE_HOUSE_DELIVERY_ONLY` con `remittanceType: PICKUP` y `FEE_NOT_ALLOWED` con `fee`.

## `RemittanceView`

Tres campos nuevos, en todas las respuestas que devuelven remesas (`GET /remittances`, `/remittances/{id}`,
`/remittances/code/{code}`, `/remittances/assigned`, `/remittances/completed`, `GET /sync/state`, los resultados de
`POST /sync/actions` y las respuestas de cada acción):

| Campo | Quién lo ve | Contenido |
| --- | --- | --- |
| `onTheHouse` | Todos, también el repartidor | `true` si es por la casa; `false` en el resto (también en todas las remesas anteriores a la `V3`) |
| `waivedFee` | Quien ve los datos financieros de la remesa (en entregas, `remittances:read`) | La comisión no cobrada; `null` si no es por la casa o si no los ve |
| `onTheHouseReason` | Igual que `waivedFee` | El motivo; `null` si no es por la casa o si no lo ve |

El repartidor recibe `onTheHouse` pero no `waivedFee` ni `onTheHouseReason`, igual que no recibe `fee` ni `total`.

## `GET /api/v1/remittances?onTheHouse=true|false`

Filtro nuevo, combinable con los demás: `true` lista solo las de por la casa; `false` las excluye; sin él, todas.

## Informe mensual (`GET /api/v1/reports/monthly`)

Bloque nuevo, siempre presente:

```json
"onTheHouse": { "remittances": 2, "amount": 237.00, "waivedFees": 30.00 }
```

| Campo | Contenido |
| --- | --- |
| `remittances` | Entregas por la casa registradas en el mes |
| `amount` | Suma de sus montos enviados (moneda del negocio, USD) |
| `waivedFees` | Suma de las comisiones no cobradas (USD) |

Como el resto del informe, no cuenta las anuladas. Estas remesas siguen sumando en las demás cifras con comisión 0:
`collected` incluye su `amount`, `fees` no suma nada por ellas y `byCurrency`/`byType` las cuentan.

## Auditoría

Al registrarla, en la misma transacción, `GET /audit` registra la acción `REMITTANCE_ON_THE_HOUSE`, con `targetType`
`REMITTANCE`, `targetId` el id de la remesa, `targetLabel` su código, `actorId` quién la registró y `details`:

```json
{ "code": "OT-7K3F9Q", "type": "DELIVERY", "amount": 37.00, "currency": "USD", "waivedFee": 10.00,
  "customerId": "…", "customerName": "Ana Pérez", "beneficiaryId": "…", "beneficiaryName": "Rosa Martínez",
  "reason": "Su madre" }
```

Anularla después se audita como cualquier anulación (`REMITTANCE_VOIDED`).

## Base de datos (`V3__on_the_house.sql`)

- `remittances.remittances`: `on_the_house BOOLEAN NOT NULL DEFAULT FALSE`, `waived_fee NUMERIC(19,2)` y
  `on_the_house_reason VARCHAR(500)`. Las remesas existentes quedan con `false` y `NULL`.
- Tres CHECK: por la casa solo en entregas con `fee = 0` y `total = amount`; `waived_fee` (≥ 0) presente solo si es por
  la casa; motivo no vacío presente solo si es por la casa.
- Permiso `remittances:on-the-house` en el catálogo, concedido solo a ADMIN.
- `lock_timeout` de 10 s: si la tabla está bloqueada, la migración falla en vez de quedarse esperando.

## Compatibilidad

- Un cliente que no envía los campos nuevos funciona igual que antes (tests en `OnTheHouseTest`).
- Las apps antiguas ignoran los campos nuevos de las respuestas. No hace falta actualización obligatoria.
- Los permisos van en el token: quien reciba el permiso desde Roles necesita volver a entrar (o renovar el token).
- El panel debería mostrar la casilla solo con el permiso y solo en entregas, con el motivo obligatorio, y usar
  `POST /quotes` con `onTheHouse` para mostrar el total sin comisión y la comisión perdonada.
