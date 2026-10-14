# Contrato: entregar o recoger desde el negocio, sin repartidor

Estado: **en develop** (2026-10-14; migración `V2`). Decisión del usuario: ventas o el admin pueden marcar una remesa
entregada (o una recogida recogida) sin pasar por un repartidor; el dinero sale de (o entra en) la caja del negocio.
También vale para una remesa ya asignada: se le quita el repartidor. No pide PIN.

## Endpoint

```
POST /api/v1/remittances/{id}/complete-from-business      permiso remittances:complete-from-business (ADMIN, SALES)
{ "note": "Entregada en la oficina" }                      cuerpo opcional; note ≤ 1000
```

Responde 200 con el `RemittanceView` completado:

- `status` el estado final (`DELIVERED`), `completedAt` el momento de la llamada.
- `courierId` y `courierName` `null`: si estaba asignada, se le quita al repartidor (desaparece de su lista y de su
  teléfono en la siguiente sincronización).
- Campo nuevo **`completedFromBusiness: true`**. Es `false` en el resto: entregadas por un repartidor, remesas
  antiguas registradas a mano y abiertas.

En el historial queda un `STATUS_CHANGED` hacia el estado final, con quién lo hizo, sin repartidor y con la nota.

## Caja

En la moneda de la remesa (`cashAmount`, `cashCurrency`):

| Tipo | Movimiento | Cuentas |
| --- | --- | --- |
| Entrega | `DELIVERY_PAYOUT` | caja del negocio → exterior |
| Recogida | `PICKUP_COLLECTION` | exterior → caja del negocio |

La caja del repartidor no se toca. La caja del negocio puede quedar en negativo, como la de un repartidor. Anular la
remesa después deshace el movimiento con `REMITTANCE_VOID_REVERSAL`, como siempre.

## Errores

| Caso | Respuesta |
| --- | --- |
| Sin el permiso (p. ej. un repartidor) | 403 |
| Ya completada | 409 `REMITTANCE_ALREADY_COMPLETED` |
| Anulada | 409 `REMITTANCE_VOIDED` |
| No existe | 404 `REMITTANCE_NOT_FOUND` |
| Moneda que el negocio no maneja (sin corredor activo) | 400 `NO_BUSINESS_CASH` |

## Si el repartidor también la entrega

Si el repartidor la tenía en el teléfono sin conexión y la entrega después, al sincronizar no se aplica nada: queda
marcada para revisar con `DUPLICATE_DELIVERY`, como cuando la completó otra persona.

## Permisos y despliegue

- `V2` crea el permiso y se lo da al ADMIN. Las instalaciones nuevas lo dan a SALES en `seed/cuba-la-habana.sql`; las
  ya instaladas, con `scripts/data/fixes/20261014-sales-complete-from-business.sql` (tras desplegar la V2).
- Los permisos van en el token: un usuario que ya tenía sesión necesita volver a entrar (o renovar el token) para
  tenerlo.
- El panel debería mostrar el botón solo con el permiso, en remesas abiertas y no anuladas.
