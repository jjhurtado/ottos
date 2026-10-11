# Módulo `payments` — Pagos y caja

Paquete `org.jobits.ottos.payments`. Fase 1 (libro de caja); cierre y conciliación en la Fase 3.

## Para qué sirve

Llevar el efectivo del negocio y el de cada repartidor, por moneda, en un libro de doble entrada.

## API pública

Escucha los eventos de `remittances`. `CashLedgerService` (con `MovementType` y `AccountType`) lo usa el módulo `sync`
para incluir la caja del repartidor en la descarga de la app móvil.

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /cash/business`, `/cash/business/movements`, `/cash/couriers` (todos los repartidores actuales, también sin movimientos, y quien aún tenga caja de repartidor; nunca administradores), `/cash/couriers/{id}`, `/cash/remittances/{id}/movements` | `cash:read` |
| `GET /cash/me` (mi caja, como repartidor) | `cash:read-own` |
| `POST /cash/couriers/{id}/funding` (entregar efectivo al repartidor), `/returns` (lo que devuelve) | `cash:write` |
| `POST /cash/business/deposits`, `/withdrawals` (capital, cambio de moneda, gastos; con nota) | `cash:adjust` |
| `POST /cash/me/exchanges` (cambio de moneda de mi caja, como repartidor) | `cash:exchange-own` |
| `POST /cash/couriers/{id}/exchanges` (cambio en nombre de un repartidor) | `cash:write` |
| `GET /cash/exchanges?courierId=&review=&from=&to=` | `cash:read` |
| `POST /cash/exchanges/{id}/review/resolve` (con nota) | `cash:review` |

## Datos

`cash_accounts`, `cash_movements` y `cash_exchanges` (`V1__baseline.sql`, sección *payments*).

## Reglas

- **Doble entrada:** cada movimiento saca dinero de una cuenta y lo pone en otra; los saldos son la suma de movimientos
  y nunca se guardan.
- **Cuentas:** `BUSINESS` (la caja del negocio, una por moneda; una sola sucursal en 1.0), `COURIER` (una por
  repartidor y moneda, puede quedar en negativo) y `EXTERNAL` (el mundo exterior).
- **Movimientos automáticos** (en la misma transacción que la remesa):
  - registrar una entrega → `REMITTANCE_PAYMENT`: el total cobrado entra en la caja del negocio;
  - completar una entrega → `DELIVERY_PAYOUT`: el monto a entregar sale de la caja del repartidor;
  - completar una recogida → `PICKUP_COLLECTION`: los USD recogidos entran en la caja del repartidor.
- La primera cuenta de un repartidor se crea al vuelo y sin colisiones (`INSERT … ON CONFLICT DO NOTHING`).
- **Cambio de moneda del repartidor** (contrato: `docs/contracts/courier-exchange.md`): el negocio trabaja en USD y el
  repartidor cambia durante el viaje. Un cambio son dos movimientos dentro de su caja (`COURIER_EXCHANGE_OUT` y
  `COURIER_EXCHANGE_IN`, con el mismo `exchangeId` y la tasa); la caja del negocio no cambia. `rate` es siempre
  unidades de la otra moneda por 1 USD y una de las dos monedas es USD; el servidor calcula lo recibido. Si la tasa se
  aleja de la tasa de entrega configurada en ese momento más que `exchange_rate_tolerance_percent` (Configuración, 20),
  se registra igual pero queda marcado `UNUSUAL_EXCHANGE_RATE` hasta que Ventas lo resuelve con una nota. También desde
  la cola offline (`CASH_EXCHANGE`, idempotente por el id de la acción; `LATE_SYNC` si llega más de 7 días tarde).
- La fecha de un movimiento automático es la del evento: para una entrega sincronizada desde la app móvil, la hora en
  que ocurrió (`occurredAt`), no la de la sincronización.

## Pendiente

Cierre de caja diario por repartidor y conciliación (Fase 3).
