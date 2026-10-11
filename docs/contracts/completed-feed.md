# Contrato: aviso de remesas completadas (notificaciones de Ventas)

Estado: **implementado** (2026-10-11; columna `completion_recorded_at` en `V1__baseline.sql`). Propuesta del frontend; decisiones del
usuario: lo ven todas las personas con `remittances:read` (no solo quien registró la remesa) y entran también las
recogidas.

No hay push ni Firebase: la app genera notificaciones locales cuando consulta al servidor. El repartidor ya detecta sus
remesas nuevas con `GET /sync/state?since=`; esto es para que Ventas se entere de las entregas y recogidas completadas.

## Petición

```
GET /api/v1/remittances/completed?since=2026-10-11T18:00:00Z     permiso remittances:read
```

- `since`: hora **del servidor** (ISO UTC con `Z`), la `serverTime` de la respuesta anterior. Misma semántica que en
  `/sync/state`.
- Sin `since`: devuelve `remittances: []` y una `serverTime` para empezar. No notifica el pasado.
- Un `since` con otro formato: 400 `INVALID_PARAMETER`.

## Respuesta 200

```json
{ "serverTime": "2026-10-11T18:04:55Z", "hasMore": false, "remittances": [ { "id": "…", "type": "DELIVERY", "status": "DELIVERED", "completedAt": "…" } ] }
```

- `remittances`: `RemittanceView` completos (entregas y recogidas, se distinguen por `type`), con la misma visibilidad
  de cifras financieras que el resto de vistas. Ordenadas de la más antigua a la más reciente, como mucho 100.
- `hasMore`: `true` si quedan más; se llama otra vez enseguida con la nueva `serverTime` (útil si el móvil estuvo días
  sin conexión).
- `serverTime`: el `since` de la siguiente llamada. Hay que guardarla siempre, aunque la lista venga vacía.

## Reglas

- Entra una remesa cuando el **servidor registra** que se completó, no cuando se entregó: una entrega hecha sin
  conexión y sincronizada horas después sale en la siguiente consulta, aunque su `completedAt` (la hora real de la
  entrega) sea anterior al `since`. Por eso la base guarda `completion_recorded_at` aparte de `completed_at`.
- La respuesta va unos 5 segundos por detrás del reloj del servidor, para no saltarse una entrega que se esté
  guardando justo en ese momento. Cada remesa sale una sola vez si se encadenan las `serverTime`.
- Consulta ligera: índice parcial sobre `completion_recorded_at`.
