# Módulo `payments` — Pagos y caja

Paquete `org.jobits.ottos.payments`. Fase 1 (libro de caja); cierre y conciliación en la Fase 3.

## Para qué sirve

Llevar el efectivo del negocio y el de cada repartidor, por moneda, en un libro de doble entrada.

## API pública

Ninguna para otros módulos: escucha los eventos de `remittances`.

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /cash/business`, `/cash/business/movements`, `/cash/couriers`, `/cash/couriers/{id}`, `/cash/remittances/{id}/movements` | `cash:read` |
| `GET /cash/me` (mi caja, como repartidor) | `cash:read-own` |
| `POST /cash/couriers/{id}/funding` (entregar efectivo al repartidor), `/returns` (lo que devuelve) | `cash:write` |
| `POST /cash/business/deposits`, `/withdrawals` (capital, cambio de moneda, gastos; con nota) | `cash:adjust` |

## Datos

`cash_accounts`, `cash_movements` (`V7__cash.sql`).

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

## Pendiente

Cierre de caja diario por repartidor y conciliación (Fase 3).
