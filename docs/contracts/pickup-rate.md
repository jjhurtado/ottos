# Contrato: tasa propia de cada recogida

Estado: **cerrado con el frontend e implementado** (2026-10-11; columna `rate_overridden` en `V1__baseline.sql`). Contexto en
[ROADMAP.md › Reglas del negocio decididas en 1.0](../../ROADMAP.md).

Las recogidas se hacen cuando al negocio le interesa tener efectivo, normalmente 1x1 y para montos grandes, y no dejan
ganancia. Al registrar una recogida, Ventas indica la tasa de ese momento (1x1 u otra); si no la indica, se usa la tasa
de recogida configurada, como hoy.

## Cambios

1. `POST /api/v1/remittances` acepta un campo nuevo opcional `rate`, **solo para `type: "PICKUP"`**:

   ```json
   { "type": "PICKUP", "customerId": "…", "beneficiaryId": "…", "amount": 5000, "targetCurrency": "USD", "rate": 1 }
   ```

   - `rate`: unidades de la moneda de entrega por 1 USD, > 0, máximo 6 decimales. Sin `rate`, la tasa de recogida
     configurada del corredor.
   - En una entrega (`DELIVERY`), enviar `rate` da 400 `RATE_NOT_ALLOWED`: las entregas siempre usan la tasa
     configurada.
   - El monto a entregar se calcula igual que hoy, con esa tasa y el redondeo del corredor.
   - La comisión sigue siendo la regla de recogida configurada. Como las recogidas no dejan ganancia, conviene
     configurarla en 0 (Configuración › Recogidas).

2. `POST /api/v1/quotes` acepta el mismo `rate` opcional cuando `remittanceType` es `PICKUP`, para que el formulario
   muestre la cotización antes de registrar. Mismo error `RATE_NOT_ALLOWED` en una entrega.

3. `RemittanceView` y `Quote` ganan `rateOverridden: boolean`: `true` si la tasa se indicó a mano, `false` si es la
   configurada. `rate` sigue siendo la tasa aplicada. En `RemittanceView`, como `rate`, solo lo ve quien ve las cifras
   financieras de esa remesa (en una recogida, `remittances:read-financials`); si no, viene `null`.

## Efecto en caja e informe

- Caja: no cambia. Completar una recogida mete en la caja del repartidor los USD recogidos (`amount`), como hoy.
- Informe mensual: la recogida entra con la tasa indicada (su entregado en USD = monto a entregar ÷ esa tasa) y con su
  comisión (0 si así está configurada).

## Códigos nuevos

`RATE_NOT_ALLOWED`. Un `rate` ≤ 0 o con más de 6 decimales: 400 `VALIDATION_FAILED`.
