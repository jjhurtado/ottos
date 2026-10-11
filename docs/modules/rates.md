# Módulo `rates` — Cotizador

Paquete `org.jobits.ottos.rates`. Fase 1.

## Para qué sirve

Calcula cuánto cuesta una remesa y cuánto se entrega, con la configuración vigente. No guarda datos: los valores
vienen de `configuration`.

## API pública

- `QuoteService.quote(remittanceType, sourceCurrency, targetCurrency, amount, customRate)`. `customRate` es la tasa
  indicada a mano para una recogida (`null` usa la configurada; en una entrega, `RATE_NOT_ALLOWED`). `remittances` lo usa al registrar cada
  remesa (con el tipo de la remesa).

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `POST /quotes` (`remittanceType` opcional, `DELIVERY` por defecto; `rate` opcional solo en recogidas) | `rates:read` |

## Cálculo

```
monto              ≥ mínimo configurado (USD)                 si no: AMOUNT_BELOW_MINIMUM (con minimumAmount, currency)
comisión           = regla del corredor y tipo (p. ej. 10 %, mínimo 10 USD), redondeada a céntimos
total              = monto + comisión                         lo que paga el remitente
monto a entregar   = monto × tasa del corredor y tipo, redondeado hacia abajo al múltiplo de entrega (50 CUP)
                                                              si queda en 0: AMOUNT_TOO_SMALL
```

Otros errores: `INVALID_AMOUNT` (≤ 0 o más de 2 decimales), `NO_ACTIVE_CORRIDOR`, `NO_EXCHANGE_RATE`, `NO_FEE_RULE`.

## Decisiones

- **Sin vencimiento de la cotización en 1.0:** Ventas cotiza y registra en el momento, y la remesa guarda la tasa con
  que se cobró. Se retoma con la App Cliente (Fase 2).
