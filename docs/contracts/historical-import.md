# Contrato: importar remesas antiguas

Estado: **implementado en develop** (2026-10-12). Decisiones del usuario: las remesas de los tickets del chat de
WhatsApp (junio de 2024 en adelante) entran como remesas entregadas en su fecha real, sin cambiar el esquema; todas
están pagadas; solo las de USD (las de CUP, EUR y MLC las trata el usuario a mano); no mueven ninguna caja (la caja del
negocio es el efectivo en Cuba, no lo cobrado; decisión del 2026-10-13); el endpoint queda permanente y cada uso queda
en la auditoría.

## Petición

```
POST /api/v1/remittances/import          permiso customers:import (solo ADMIN)
```

```json
{
  "dryRun": true,
  "customerId": "…cliente comodín creado desde el panel, p. ej. «Cliente histórico sin identificar»…",
  "remittances": [
    { "ref": "chat-20240603-153047", "occurredAt": "2024-06-03T15:30:47",
      "beneficiaryPhone": "+53 5 2223344", "beneficiaryName": "Rosa Martínez",
      "amountDelivered": 200, "fee": 20 }
  ]
}
```

| Campo | Regla |
| --- | --- |
| `dryRun` | `true` calcula el mismo resultado sin guardar nada (vista previa). Por defecto `false` |
| `customerId` | Obligatorio y existente (`CUSTOMER_UNAVAILABLE` si no). Todas las filas del lote se atribuyen a él |
| `ref` | Obligatorio, ≤ 100. Identifica el ticket: importarlo otra vez da `EXISTING` y no duplica nada |
| `occurredAt` | Obligatorio, fecha y hora de Cuba sin zona. No puede ser futura (`INVALID_OCCURRED_AT`) |
| `beneficiaryPhone` | Como venga escrito; se compara por los últimos 8 dígitos con los beneficiarios (también los borrados) |
| `beneficiaryName` | Opcional; distingue entre varios beneficiarios con el mismo teléfono |
| `amountDelivered` | USD entregados, > 0, máximo 2 decimales |
| `fee` | USD de comisión, ≥ 0, máximo 2 decimales, no mayor que `amountDelivered` |

Máximo 500 filas por petición; cada fila en su propia transacción y un rechazo no frena las demás.

## Respuesta 200

```json
{ "dryRun": false, "summary": { "created": 1, "existing": 0, "rejected": 0 },
  "results": [ { "index": 0, "ref": "chat-20240603-153047", "status": "CREATED", "remittanceId": "…",
                 "code": null, "message": null } ] }
```

`status`: `CREATED`, `EXISTING` o `REJECTED`. Códigos de rechazo: `VALIDATION_FAILED`, `INVALID_OCCURRED_AT`,
`BENEFICIARY_NOT_FOUND`, `AMBIGUOUS_BENEFICIARY` (varios comparten el teléfono y el nombre no los distingue).

## Qué se guarda

- Una remesa `DELIVERY` en estado final (`DELIVERED`), USD → USD, con fecha de registro y de entrega = `occurredAt` y
  fecha prevista = ese día. Monto y monto a entregar = `amountDelivered`; comisión = `fee` (`feeOverridden: true`);
  total = monto + comisión. Sin repartidor. PIN aleatorio (nunca se usa). Tasa y regla de comisión vigentes de USD-USD
  solo como referencia. Nota: "Imported from the old records (ref)". Clave de idempotencia `historical:<ref>`.
- Su historial: un evento `CREATED` en esa fecha.
- **Caja:** ninguna se mueve. Como en cualquier remesa, lo cobrado no entra en la caja del negocio (que es el efectivo
  en Cuba) y, sin repartidor, ninguna caja de mensajero cambia. No hace falta ningún ajuste después de importar.
- **Auditoría:** una entrada por lote real (`HISTORICAL_REMITTANCES_IMPORTED`) con el cliente, cuántas filas se crearon,
  existían o se rechazaron y el total cobrado. La vista previa no se audita.
- No se vincula el beneficiario al cliente comodín.

## Dónde aparecen

En el historial y las estadísticas del beneficiario, en `GET /remittances` (con `customerId=<comodín>` se ven todas) y
en el informe mensual de su mes. No aparecen en las listas de trabajo (están entregadas), en el aviso de remesas
completadas a Ventas ni en la caja o las estadísticas de ningún repartidor.

## Por qué es seguro dejarlo

Usarlo de nuevo no pasa desapercibido: cada lote queda en la auditoría con quién lo hizo, cuántas remesas creó y el
total cobrado, y las remesas importadas se reconocen por el cliente comodín y la nota. Solo un ADMIN puede usarlo.
