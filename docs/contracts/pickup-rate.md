# Contrato: tasa y comisión propias de cada recogida

Estado: **cerrado con el frontend e implementado** (2026-10-11; columna `rate_overridden` en `V1__baseline.sql`). Contexto en
[ROADMAP.md › Reglas del negocio decididas en 1.0](../../ROADMAP.md).

Las recogidas son variables y casi siempre las registra el admin, que es quien conoce sus condiciones. Al registrar
una, indica la tasa y la comisión de ese momento; si no las indica, se usan las de recogida configuradas.

**Qué es cada cifra en una recogida** (decidido con el negocio, 2026-10-11):

- `amount`: lo que el repartidor **recoge** al beneficiario, en USD. Es lo único que mueve la caja (`cashAmount`).
- En una recogida **no se entrega nada**: `amountToDeliver` se sigue calculando (`amount × rate`) pero no significa
  nada y el frontend no debe mostrarlo. `rate` es **solo informativa**.
- `fee`: la comisión, ingreso del negocio. A veces la paga el cliente (fuera del país) y a veces el beneficiario (va
  incluida en lo que se recoge). En ningún caso pasa por la caja del repartidor ni sale en su resumen; solo cuenta como
  ganancia en el informe mensual. Solo la ve quien ve las cifras financieras (`remittances:read-financials`).

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
   - Acepta también `fee` opcional, solo para `PICKUP`: la comisión indicada a mano, ≥ 0 con 2 decimales como mucho
     (puede ser 0). Sin `fee`, la regla de recogida configurada. En una entrega, 400 `FEE_NOT_ALLOWED`.

2. `POST /api/v1/quotes` acepta los mismos `rate` y `fee` opcionales cuando `remittanceType` es `PICKUP`, para que el
   formulario muestre la cotización antes de registrar. Mismos errores en una entrega.

3. `RemittanceView` y `Quote` ganan `rateOverridden` y `feeOverridden` (`boolean`): `true` si la tasa o la comisión se
   indicaron a mano, `false` si son las configuradas. En `RemittanceView`, como `rate`, solo lo ve quien ve las cifras
   financieras de esa remesa (en una recogida, `remittances:read-financials`); si no, viene `null`.

## Efecto en caja e informe

- Caja: completar una recogida mete en la caja del repartidor los USD recogidos (`amount`). Nada más.
- Informe mensual: la recogida no entra en cobrado, entregado ni diferencia; su comisión suma a las ganancias (`fees`) y
  sus cifras salen aparte en `pickups` ([monthly-report.md](monthly-report.md)).

## Códigos nuevos

`RATE_NOT_ALLOWED`, `FEE_NOT_ALLOWED`. Un `rate` ≤ 0 o con más de 6 decimales, o un `fee` < 0 o con más de 2
decimales: 400 `VALIDATION_FAILED`.
