# Contrato: informe mensual de ventas

Estado: **cerrado con el frontend e implementado**; desde 1.0.0-rc.4 `deliveredUsd` se llama `deliveredCurrency` (lo
entregado convertido a la moneda del negocio) y la respuesta lleva `businessCurrency` (2026-10-11; módulo `reporting`, cifras de `remittances`). Decisiones del negocio en
[ROADMAP.md › Reglas del negocio decididas en 1.0](../../ROADMAP.md).

El informe más importante para el negocio. El backend devuelve los números en JSON y el panel genera el PDF.

## Petición

```
GET /api/v1/reports/monthly?month=2026-10        permiso reports:read (nuevo; ADMIN y SALES)
```

- `month` obligatorio, `AAAA-MM`. Un mes futuro devuelve ceros.
- Errores: `VALIDATION_FAILED` si falta o tiene otro formato (por ejemplo `2026-13`).

## Reglas

- **Qué remesas entran:** las **registradas** en ese mes, en hora de Cuba (`ottos.timezone`), sea cual sea su estado.
  Todo el informe cuenta por fecha de registro, también lo entregado.
- **Moneda:** el negocio trabaja en USD. Todos los importes de cobro, comisión y diferencia están en USD (la moneda de
  origen de todas las remesas). Lo entregado se da en su moneda y convertido a USD.
- **Cobrado** (`collected`): `monto + comisión` de cada **entrega** (lo que se cobra por ella).
- **Comisiones / ganancias** (`fees`): suma de comisiones, de entregas **y de recogidas**.
- **Entregado** (`delivered`): el monto a entregar de cada entrega del mes, **completada o no**, en su moneda (CUP o
  USD). Así la diferencia es coherente con lo cobrado; las pendientes se ven en `remittances.open`.
- **Entregado en USD** (`deliveredCurrency`): cada monto entregado convertido con **la tasa de su propia remesa**
  (`montoAEntregar ÷ tasa`, 2 decimales, mitad hacia arriba, y luego sumado).
- **Diferencia** (`difference`): `cobrado − entregado en USD` de las entregas. Equivale a las comisiones de las
  entregas más el margen del redondeo a 50 CUP.
- **Margen de redondeo** (`roundingMargin`): `diferencia − comisiones de las entregas`.
- **Recogidas** (decidido con el negocio, [pickup-rate.md](pickup-rate.md)): en una recogida no se entrega nada y su
  tasa es solo informativa. No entran en cobrado, entregado, diferencia ni `byCurrency`; su comisión suma a `fees` y sus
  cifras salen en `pickups`: cuántas, lo recogido por los repartidores (`collected`, USD) y sus comisiones.

## Respuesta 200

Forma acordada con el frontend:

```json
{
  "month": "2026-10", "from": "2026-10-01", "to": "2026-10-31", "generatedAt": "2026-11-01T13:05:00Z",
  "businessCurrency": "USD",
  "remittances": { "total": 24, "delivery": 18, "pickup": 6, "completed": 21, "open": 3, "withIncident": 1 },
  "collected": 1980.00,
  "fees": 220.00,
  "deliveredCurrency": 1799.00,
  "difference": 181.00,
  "roundingMargin": 1.00,
  "pickups": { "remittances": 6, "collected": 30000.00, "fees": 40.00 },
  "onTheHouse": { "remittances": 0, "amount": 0.00, "waivedFees": 0.00 },
  "delivered": [ { "currency": "CUP", "amount": 737950.00 } ],
  "byCurrency": [
    { "currency": "CUP", "remittances": 18, "sent": 1800.00, "fees": 180.00, "collected": 1980.00,
      "delivered": 737950.00, "deliveredCurrency": 1799.00 }
  ],
  "byType": [ { "type": "DELIVERY", "remittances": 18, "collected": 1980.00, "fees": 180.00 },
              { "type": "PICKUP", "remittances": 6, "collected": 30000.00, "fees": 40.00 } ]
}
```

| Campo | Contenido |
| --- | --- |
| `businessCurrency` | La moneda del negocio de la instalación (USD en Cuba): todos los importes del informe están en ella salvo `delivered` y `byCurrency[].delivered` |
| `remittances` | Remesas registradas en el mes: `total`, por tipo (`delivery`, `pickup`), `completed` = completadas hasta el último instante del mes, `open` = el resto, `withIncident` = con una incidencia abierta en el momento de pedir el informe |
| `collected`, `deliveredCurrency`, `difference`, `roundingMargin` | En USD, solo entregas, según las reglas de arriba |
| `fees` | Ganancias en USD: comisiones de entregas y recogidas |
| `pickups` | Recogidas: `remittances`, `collected` (lo recogido, USD) y `fees` |
| `onTheHouse` | Entregas por la casa, sin comisión (desde la `V3`; contrato `on-the-house.md`): `remittances`, `amount` (montos enviados, USD) y `waivedFees` (comisiones no cobradas, USD). Sin anuladas. Siguen contando en el resto de cifras con comisión 0 |
| `delivered` | Monto a entregar de **todas** las entregas del mes (completadas o no), por moneda de entrega, en su moneda |
| `byCurrency` | Entregas por moneda de entrega: `sent` (montos enviados, USD), `fees`, `collected`, `delivered` (en su moneda) y `deliveredCurrency` |
| `byType` | Por tipo de remesa, en USD; `collected` es monto + comisión en las entregas y lo recogido en las recogidas |

- Todo en USD salvo `delivered` y `byCurrency[].delivered`, que van en su propia moneda.
- Listas siempre presentes (vacías si no hay datos), ordenadas por moneda o tipo. Importes con 2 decimales.
- `byDay` (para un gráfico) queda fuera por ahora; se puede añadir como campo nuevo sin romper nada.

## Permiso nuevo

| Permiso | Para qué | Roles iniciales |
| --- | --- | --- |
| `reports:read` | Ver los informes del negocio | ADMIN, SALES |

## Detalle

Estado: **acordado con el usuario y el frontend e implementado** (2026-10-23, rama
`feature/report-detail-and-statistics`). Para ver y descargar (CSV y PDF, los genera el panel) cada remesa del informe.

```
GET /api/v1/reports/monthly/remittances?month=2026-10        permiso reports:read (ADMIN y SALES)
```

- `month` como en el informe: obligatorio, `AAAA-MM`; `VALIDATION_FAILED` si falta o tiene otro formato; un mes sin
  remesas devuelve la lista vacía. Sin permiso, 403.
- **Qué remesas entran:** todas las **registradas** en el mes, en hora de Cuba, sea cual sea su estado, **también las
  anuladas**, marcadas con `voided`.
- **Orden:** `createdAt` y, a igualdad, `code`. Sin paginar (un mes son unos cientos de filas).
- **Sin datos personales:** no lleva nombres, teléfonos ni ids de cliente o beneficiario, ni dirección, referencia,
  PIN, notas, motivo de «por la casa» ni quién la registró (`createdBy`). Sí lleva el municipio del beneficiario y el
  nombre del repartidor.
- **Cuadra con el informe:** las filas sin `voided` suman lo mismo que `GET /reports/monthly` del mismo mes: `collected`
  de las entregas = `collected`; `fee` de todas = `fees`; `deliveredCurrency` = `deliveredCurrency`; `collected` de las
  recogidas = `pickups.collected`; `waivedFee` de las de por la casa = `onTheHouse.waivedFees`. Cada fila se calcula
  con la misma regla que el informe (`remittances.SaleFigures`).

### Respuesta 200

```json
{
  "month": "2026-10", "from": "2026-10-01", "to": "2026-10-31", "generatedAt": "2026-11-01T13:05:00Z",
  "businessCurrency": "USD",
  "counts": { "lines": 25, "voided": 1, "total": 24, "delivery": 18, "pickup": 6, "completed": 21, "open": 3,
              "withIncident": 1 },
  "remittances": [
    { "id": "…", "code": "OT-7KQ2M9", "type": "DELIVERY", "status": "DELIVERED", "statusName": "Entregada",
      "createdAt": "2026-10-01T12:30:00Z", "expectedDate": "2026-10-03", "completedAt": "2026-10-02T18:00:00Z",
      "completedFromBusiness": false,
      "municipalityCode": "2302", "municipalityName": "Plaza de la Revolución", "courierName": "Pedro Díaz",
      "sourceCurrency": "USD", "targetCurrency": "CUP", "amount": 100.00, "fee": 10.00, "total": 110.00,
      "rate": 410.00, "amountToDeliver": 41000.00, "collected": 110.00, "deliveredCurrency": 100.00,
      "onTheHouse": false, "waivedFee": null, "withIncident": false, "voided": null },
    { "id": "…", "code": "OT-P3X8RA", "type": "PICKUP", "status": "PAID", "statusName": "Pagada",
      "createdAt": "2026-10-01T14:00:00Z", "expectedDate": "2026-10-03", "completedAt": null,
      "completedFromBusiness": false, "municipalityCode": "2303", "municipalityName": "Centro Habana",
      "courierName": null, "sourceCurrency": "USD", "targetCurrency": "CUP", "amount": 200.00, "fee": 20.00,
      "total": 220.00, "rate": 410.00, "amountToDeliver": 82000.00, "collected": 200.00, "deliveredCurrency": null,
      "onTheHouse": false, "waivedFee": null, "withIncident": false,
      "voided": { "at": "2026-10-02T09:00:00Z", "by": "…", "byName": "Admin", "reason": "Registrada dos veces" } }
  ]
}
```

| Campo | Contenido |
| --- | --- |
| `counts.lines` | Filas de la lista, anuladas incluidas |
| `counts.voided` | Filas anuladas |
| `counts.total` … `counts.withIncident` | Las no anuladas: **lo mismo que `remittances` de `GET /reports/monthly`** (`completed` = completadas antes de acabar el mes; `withIncident`, ahora) |
| `status`, `statusName` | Código del estado (el frontend lo traduce por código y tipo) y su nombre guardado, de respaldo |
| `createdAt`, `completedAt` | Instantes en UTC; el frontend los muestra en hora de Cuba |
| `completedFromBusiness` | Entregada o recogida por el negocio, sin repartidor |
| `municipalityCode`, `municipalityName` | Municipio del beneficiario |
| `courierName` | Repartidor asignado ahora (o quien la completó); null si no tiene |
| `amount`, `fee`, `total`, `rate`, `amountToDeliver` | Los de la remesa: en USD salvo `amountToDeliver`, en `targetCurrency`. En una recogida `rate` es informativa |
| `collected` | Lo que cuenta como cobrado: `amount + fee` en una entrega; lo recogido (`amount`) en una recogida |
| `deliveredCurrency` | Lo entregado en USD, `amountToDeliver ÷ rate` (2 decimales, mitad hacia arriba); null en una recogida |
| `onTheHouse`, `waivedFee` | Por la casa y la comisión no cobrada (null si no es por la casa) |
| `withIncident` | Con una incidencia abierta ahora |
| `voided` | null, o `{at, by, byName, reason}` si está anulada: sale en la lista pero no cuenta en ninguna suma |
