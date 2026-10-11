# Contrato: informe mensual de ventas

Estado: **cerrado con el frontend e implementado** (2026-10-11; módulo `reporting`, cifras de `remittances`). Decisiones del negocio en
[ROADMAP.md › Funcionalidades pedidas en diseño](../../ROADMAP.md).

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
- **Cobrado** (`collected`): `monto + comisión` de cada remesa (lo que se cobra por ella).
- **Comisiones / ganancias** (`fees`): suma de comisiones, de entregas **y de recogidas**.
- **Entregado** (`delivered`): el monto a entregar de cada remesa del mes, **completada o no**, en su moneda (CUP o
  USD). Así la diferencia es coherente con lo cobrado; las pendientes se ven en `remittances.open`.
- **Entregado en USD** (`deliveredUsd`): cada monto entregado convertido con **la tasa de su propia remesa**
  (`montoAEntregar ÷ tasa`, 2 decimales, mitad hacia arriba, y luego sumado).
- **Diferencia** (`difference`): `cobrado − entregado en USD`. Equivale a las comisiones más el margen del redondeo a
  50 CUP.
- **Margen de redondeo:** `diferencia − comisiones`; no viene en la respuesta, lo calcula el panel.
- **Recogidas:** entran con su tasa y su comisión tal como quedaron en la remesa. Cuando se pueda indicar la tasa de
  cada recogida al registrarla, el informe usará la que se indicó; la forma de la respuesta no cambia.

## Respuesta 200

Forma acordada con el frontend:

```json
{
  "month": "2026-10", "from": "2026-10-01", "to": "2026-10-31", "generatedAt": "2026-11-01T13:05:00Z",
  "remittances": { "total": 24, "delivery": 18, "pickup": 6, "completed": 21, "open": 3, "withIncident": 1 },
  "collected": 2640.00,
  "fees": 240.00,
  "deliveredUsd": 2399.00,
  "difference": 241.00,
  "delivered": [ { "currency": "CUP", "amount": 1367240.00 }, { "currency": "USD", "amount": 600.00 } ],
  "byCurrency": [
    { "currency": "CUP", "remittances": 16, "sent": 1800.00, "fees": 180.00, "collected": 1980.00,
      "delivered": 1367240.00, "deliveredUsd": 1799.00 }
  ],
  "byType": [ { "type": "DELIVERY", "remittances": 18, "collected": 1980.00, "fees": 180.00 } ]
}
```

| Campo | Contenido |
| --- | --- |
| `remittances` | Remesas registradas en el mes: `total`, por tipo (`delivery`, `pickup`), `completed` = completadas hasta el último instante del mes, `open` = el resto, `withIncident` = con una incidencia abierta en el momento de pedir el informe |
| `collected`, `fees`, `deliveredUsd`, `difference` | En USD, según las reglas de arriba. El margen de redondeo es `difference − fees` |
| `delivered` | Monto a entregar de **todas** las remesas del mes (completadas o no), por moneda de entrega, en su moneda |
| `byCurrency` | Por moneda de entrega: `sent` (montos enviados, USD), `fees`, `collected`, `delivered` (en su moneda) y `deliveredUsd` |
| `byType` | Por tipo de remesa, en USD |

- Todo en USD salvo `delivered` y `byCurrency[].delivered`, que van en su propia moneda.
- Listas siempre presentes (vacías si no hay datos), ordenadas por moneda o tipo. Importes con 2 decimales.
- `byDay` (para un gráfico) queda fuera por ahora; se puede añadir como campo nuevo sin romper nada.

## Permiso nuevo

| Permiso | Para qué | Roles iniciales |
| --- | --- | --- |
| `reports:read` | Ver los informes del negocio | ADMIN, SALES |
