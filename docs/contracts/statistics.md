# Contrato: estadísticas de ventas

Estado: **acordado con el usuario y el frontend e implementado** (2026-10-23, rama
`feature/report-detail-and-statistics`; módulo `reporting`, cifras de `remittances`). Las vistas las decide el
frontend (panel y app móvil para ventas con permiso); el backend da las cifras ya agrupadas.

Qué pidió el negocio: ventas por semana, por día de la semana, importe medio por remesa, remesas por municipio y
remesas por la casa. Descartado: hora del día, comisiones por semana, entregas frente a recogidas y tiempo medio de
entrega. Pendiente para más adelante: entregas por repartidor.

## Petición

```
GET /api/v1/reports/statistics?from=2026-09-30&to=2026-10-06        permiso reports:read (ADMIN y SALES)
```

- `from` y `to` obligatorios, `AAAA-MM-DD`, **los dos incluidos**, en hora de Cuba (`ottos.timezone`).
- Como mucho **366 días** por llamada (`to − from + 1 ≤ 366`): un año entero, también bisiesto. Para más, varias
  llamadas.
- Errores: 400 `VALIDATION_FAILED` si falta una fecha o tiene otro formato, si `from` es posterior a `to` o si pasa de
  366 días. Sin permiso, 403.

## Reglas

- **Qué remesas entran:** las **registradas** entre `from` 00:00 y `to` 24:00 en hora de Cuba, sea cual sea su estado,
  **sin las anuladas**. Todo cuenta por fecha de registro, como el informe mensual.
- **Las mismas cifras que el informe mensual** ([monthly-report.md](monthly-report.md)), calculadas fila a fila con la
  misma regla (`remittances.SaleFigures`). Un mes entero (`from` = día 1, `to` = último día) da en `totals` lo mismo
  que `GET /reports/monthly` de ese mes.
- **Cada agrupación suma `totals`:** la suma de `byWeek`, de `byMonth`, de `byDayOfWeek` o de `byMunicipality` da
  `totals`, cifra a cifra (salvo `averageAmount`, que es una media).
- **Semanas ISO** (lunes a domingo; `2026-W53`, `2027-W01`…). La primera y la última se **recortan** al periodo: `from`
  y `to` son los días de esa semana dentro del periodo y `partial` es `true` si no es la semana entera. Lo mismo con los
  meses en `byMonth`.
- Se listan **todas** las semanas y meses del periodo y los **siete** días de la semana, también sin remesas (con
  ceros), en orden. En `byMunicipality` solo salen los municipios con remesas, de más a menos remesas (a igualdad, por
  código).
- Municipio: el del beneficiario, guardado en la remesa al registrarla.
- Importes en la moneda del negocio (`businessCurrency`, USD en Cuba), 2 decimales.

## Respuesta 200

```json
{
  "from": "2026-09-30", "to": "2026-10-06", "generatedAt": "2026-10-23T20:00:00Z", "businessCurrency": "USD",
  "totals": {
    "remittances": 4, "deliveries": 3, "pickups": 1,
    "deliveryAmount": 237.00, "averageAmount": 79.00,
    "collected": 257.00, "fees": 40.00, "deliveredCurrency": 236.95, "pickupCollected": 200.00,
    "onTheHouse": { "remittances": 1, "amount": 100.00, "waivedFees": 10.00 }
  },
  "byWeek": [
    { "week": "2026-W40", "from": "2026-09-30", "to": "2026-10-04", "partial": true,
      "remittances": 2, "deliveries": 2, "pickups": 0, "deliveryAmount": 137.00, "averageAmount": 68.50,
      "collected": 157.00, "fees": 20.00, "deliveredCurrency": 136.95, "pickupCollected": 0,
      "onTheHouse": { "remittances": 0, "amount": 0, "waivedFees": 0 } },
    { "week": "2026-W41", "from": "2026-10-05", "to": "2026-10-06", "partial": true,
      "remittances": 2, "deliveries": 1, "pickups": 1, "deliveryAmount": 100.00, "averageAmount": 100.00,
      "collected": 100.00, "fees": 20.00, "deliveredCurrency": 100.00, "pickupCollected": 200.00,
      "onTheHouse": { "remittances": 1, "amount": 100.00, "waivedFees": 10.00 } }
  ],
  "byMonth": [
    { "month": "2026-09", "from": "2026-09-30", "to": "2026-09-30", "partial": true, "remittances": 1, "…": "…" },
    { "month": "2026-10", "from": "2026-10-01", "to": "2026-10-06", "partial": true, "remittances": 3, "…": "…" }
  ],
  "byDayOfWeek": [
    { "dayOfWeek": "MONDAY", "remittances": 1, "…": "…" },
    { "dayOfWeek": "TUESDAY", "remittances": 1, "…": "…" },
    { "dayOfWeek": "WEDNESDAY", "remittances": 1, "…": "…" },
    { "dayOfWeek": "THURSDAY", "remittances": 1, "…": "…" },
    { "dayOfWeek": "FRIDAY", "remittances": 0, "…": "…" },
    { "dayOfWeek": "SATURDAY", "remittances": 0, "…": "…" },
    { "dayOfWeek": "SUNDAY", "remittances": 0, "…": "…" }
  ],
  "byMunicipality": [
    { "municipalityCode": "2302", "municipalityName": "Plaza de la Revolución", "remittances": 3, "…": "…" },
    { "municipalityCode": "2303", "municipalityName": "Centro Habana", "remittances": 1, "…": "…" }
  ]
}
```

(`"…": "…"` abrevia el ejemplo: cada elemento lleva **todas** las cifras, como `totals`.)

### Cifras (en `totals` y en cada elemento de cada agrupación)

| Campo | Contenido | En el informe mensual |
| --- | --- | --- |
| `remittances` | Remesas registradas, sin anuladas: `deliveries + pickups` | `remittances.total` |
| `deliveries`, `pickups` | Entregas y recogidas | `remittances.delivery`, `remittances.pickup` |
| `deliveryAmount` | Montos enviados en las entregas | Σ `byCurrency[].sent` |
| `averageAmount` | Importe medio por entrega: `deliveryAmount ÷ deliveries` (2 decimales, mitad hacia arriba); 0 sin entregas | — |
| `collected` | Cobrado en las entregas: monto + comisión | `collected` |
| `fees` | Comisiones de entregas y recogidas (ganancias) | `fees` |
| `deliveredCurrency` | Lo entregado, convertido con la tasa de cada remesa | `deliveredCurrency` |
| `pickupCollected` | Lo recogido por los repartidores en las recogidas | `pickups.collected` |
| `onTheHouse` | Entregas por la casa: `remittances`, `amount` (montos enviados) y `waivedFees` (comisiones no cobradas). Siguen contando en el resto con comisión 0 | `onTheHouse` |

### Agrupaciones

| Campo | Clave | Notas |
| --- | --- | --- |
| `byWeek` | `week` (`AAAA-Www`, semana ISO), `from`, `to`, `partial` | Ventas por semana; `deliveryAmount` y `onTheHouse` por semana |
| `byMonth` | `month` (`AAAA-MM`), `from`, `to`, `partial` | Para la tabla por mes cuando el periodo abarca varios meses |
| `byDayOfWeek` | `dayOfWeek`: `MONDAY` … `SUNDAY` (el frontend lo traduce) | Siempre 7, de lunes a domingo |
| `byMunicipality` | `municipalityCode`, `municipalityName` | Solo los que tienen remesas, de más a menos |

## Permiso

`reports:read`, el mismo del informe mensual (ADMIN y SALES). No hay permiso nuevo.
