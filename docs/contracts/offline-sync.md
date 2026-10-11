# Contrato: app móvil offline-first

Estado: **cerrado con el frontend e implementado** (2026-10-10; módulo `sync`, ver `docs/modules/sync.md`). Decisiones de fondo en
[ROADMAP.md › App del repartidor offline-first](../../ROADMAP.md).

De día, Ventas y los repartidores usan la misma app móvil; lo que cada uno puede hacer depende de sus permisos, no de su
rol. De noche, Ventas y Admin usan el panel web. La app guarda en el teléfono lo que necesita, registra las acciones en
una cola local y las sincroniza cuando hay red.

Todas las rutas empiezan por `/api/v1`. Los errores siguen siendo ProblemDetail con `code` (ver README › Errors).

## 1. Sesión de jornada completa

### Login

`POST /auth/login` acepta un campo nuevo opcional `client`:

```json
{ "email": "…", "password": "…", "client": "MOBILE" }
```

| `client` | Access token | Uso |
| --- | --- | --- |
| `WEB` (por defecto si falta) | `ottos.security.access-token-ttl` (15 min) | Panel web |
| `MOBILE` | `ottos.security.mobile-access-token-ttl` (14 h por defecto) | App móvil, cualquier usuario |

- La respuesta no cambia de forma; `expiresIn` refleja la duración que corresponde.
- El JWT lleva un claim nuevo `client` (`WEB` o `MOBILE`).
- El refresh token recuerda el `client` con que se abrió la sesión: `POST /auth/refresh` emite tokens del mismo tipo
  sin que la app lo repita. El refresh token sigue durando 30 días.

### Reintento de refresh con mala red

Si la app envía un refresh, el servidor lo procesa y la respuesta se pierde, la app reintentará con el mismo token.

- Dentro de `ottos.security.refresh-reuse-grace` (60 s por defecto) desde que se usó, presentar de nuevo ese token
  **no** se considera robo: el servidor revoca el par que emitió en la primera petición (que la app nunca recibió) y
  devuelve un par nuevo válido.
- Fuera de ese margen se mantiene la regla actual: reusar un token revoca todas las sesiones del usuario (401
  `INVALID_REFRESH_TOKEN`).
- El margen no se aplica a tokens revocados por logout, cambio de contraseña o desactivación.

## 2. Descarga: `GET /sync/state`

Lo que la app guarda para trabajar sin conexión.

```
GET /sync/state                 primera vez o para reconstruir todo
GET /sync/state?since=<serverTime de la última respuesta>
```

Respuesta 200:

```json
{
  "serverTime": "2026-10-10T18:05:12.345Z",
  "full": false,
  "remittances": [ RemittanceView, … ],
  "removedIds": [ "uuid", … ],
  "cash": CourierStatement,
  "workflow": WorkflowView
}
```

| Campo | Contenido |
| --- | --- |
| `serverTime` | Reloj del servidor; es el `since` de la próxima petición. Nunca se usa la hora del teléfono. |
| `full` | `true` si la respuesta trae el estado completo (sin `since`, o `since` demasiado antiguo: más de 7 días). La app reemplaza su copia entera. |
| `remittances` | Remesas abiertas asignadas al usuario que cambiaron desde `since` (todas si `full`). Mismo `RemittanceView` que `GET /remittances/{id}`, con la misma visibilidad (`incident`, `review`, sin PIN). |
| `removedIds` | Remesas que la app debe quitar: dejaron de estar asignadas al usuario o se cerraron desde `since`. Vacío si `full`. |
| `cash` | Saldos y últimos movimientos del usuario (como `GET /cash/me`); solo si tiene `cash:read-own`, si no `null`. |
| `workflow` | Estados y transiciones (como `GET /remittance-workflow`). |

Permisos: autenticado. Las remesas se devuelven solo si el usuario tiene `remittances:read-assigned` y la caja solo si
tiene `cash:read-own`. Un usuario de Ventas en la app móvil recibe `remittances: []` y `cash: null`: es lo esperado,
porque Ventas trabaja en la app solo con conexión (sección 5), no un error.

## 3. Envío en lote: `POST /sync/actions`

```json
{
  "deviceId": "opcional, para auditoría",
  "actions": [
    {
      "id": "UUIDv4 generado en el teléfono",
      "type": "DELIVER | DELIVER_WITH_PIN | POSTPONE | REPORT_INCIDENT",
      "remittanceId": "uuid",
      "occurredAt": "2026-10-10T10:14:03-04:00",
      "payload": { … }
    }
  ]
}
```

| `type` | `payload` | Permiso | Equivale a |
| --- | --- | --- | --- |
| `DELIVER` | `{ "note"?: string ≤ 1000 }` | `remittances:deliver` | `POST /remittances/{id}/deliver` |
| `DELIVER_WITH_PIN` | `{ "pin": "6 dígitos", "strictPin"?: boolean, "note"?: string }` | `remittances:deliver` | `POST /remittances/{id}/deliver-with-pin` |
| `POSTPONE` | `{ "newDate": "AAAA-MM-DD", "reason": string ≤ 1000 }` | `remittances:postpone` | `POST /remittances/{id}/postpone` |
| `REPORT_INCIDENT` | `{ "reason": IncidentReason, "note"?: string }` | `remittances:report-incident` | `POST /remittances/{id}/incidents` |

### Respuesta

200 siempre que el lote sea válido, aunque se rechacen acciones:

```json
{
  "serverTime": "2026-10-10T18:05:12.345Z",
  "results": [
    { "id": "…", "status": "APPLIED", "remittance": RemittanceView, "flaggedForReview": false },
    { "id": "…", "status": "ALREADY_APPLIED", "remittance": RemittanceView, "flaggedForReview": false },
    { "id": "…", "status": "REJECTED", "code": "REMITTANCE_NOT_FOUND", "message": "Remittance not found" }
  ]
}
```

- Un resultado por acción, en el mismo orden.
- `APPLIED`: se aplicó ahora. `ALREADY_APPLIED`: ese `id` ya se había aplicado; no se repite nada. En los dos llega el
  `RemittanceView` actual para refrescar la copia local.
- `REJECTED`: no se aplicó; `code` es el mismo que daría el endpoint online equivalente (por ejemplo
  `REMITTANCE_ALREADY_COMPLETED`, `COURIER_NOT_ASSIGNED`, `INCIDENT_NOTE_REQUIRED`, `FORBIDDEN`), más los propios de
  la sincronización (tabla de abajo). La app la pasa a "No sincronizadas".
- Las acciones rechazadas no se guardan: si la app reenvía el mismo `id`, se vuelve a evaluar (útil si el rechazo fue
  `CONCURRENT_UPDATE`).
- `flaggedForReview: true`: se aplicó, pero la remesa queda marcada para que Ventas la revise (sección 4).

### Lote inválido (400, no se aplica nada)

`VALIDATION_FAILED` si el cuerpo está mal formado, falta un campo obligatorio de una acción (`id`, `type`,
`remittanceId`, `occurredAt`), hay `id` repetidos en el mismo lote o hay más de 200 acciones. La app debe partir colas
más largas.

Un `payload` incorrecto para su tipo (por ejemplo, `DELIVER_WITH_PIN` sin `pin` de 6 dígitos, `POSTPONE` sin
`newDate` o `reason`, un `reason` de incidencia desconocido o textos de más de 1000 caracteres) rechaza solo esa
acción: `REJECTED` con `VALIDATION_FAILED`, y las demás se aplican.

Si alguien cambia la misma remesa en el mismo instante, la acción sale `REJECTED` con `CONCURRENT_UPDATE`: reenviarla
la vuelve a evaluar.

### Orden y hora

- Las acciones se aplican **en el orden del array**; la app las envía en el orden en que ocurrieron.
- `occurredAt` (hora del teléfono, ISO-8601 con zona) es la hora de la acción: se usa en la remesa (`completedAt`), en
  el historial y en la caja. El servidor guarda además la hora en que la recibió.
- Se rechaza con `INVALID_OCCURRED_AT` si es más de 5 minutos posterior a la hora del servidor o anterior al registro
  de la remesa.
- Si es de hace más de 7 días: `POSTPONE` y `REPORT_INCIDENT` se rechazan con `INVALID_OCCURRED_AT`; `DELIVER` y
  `DELIVER_WITH_PIN` se aplican con `flaggedForReview: true` y revisión `LATE_SYNC`, porque el dinero ya cambió de manos
  y rechazarlas lo haría desaparecer del sistema.
- Para posponer, "la fecha no puede estar en el pasado" se mide contra el día de `occurredAt` (hora de Cuba).

### Conflictos

| Situación al sincronizar | Resultado |
| --- | --- |
| Entrega de una remesa que ya no está asignada al usuario, pero lo estaba a la hora `occurredAt` (Ventas la reasignó mientras no había red) | `APPLIED`, `flaggedForReview: true`, revisión `DELIVERED_AFTER_REASSIGNMENT`. El dinero ya cambió de manos: la caja se mueve en la del usuario que entregó. |
| Entrega de una remesa que no estaba asignada al usuario en `occurredAt` | `REJECTED`, `REMITTANCE_NOT_FOUND` |
| Entrega de una remesa ya completada por otro | `REJECTED`, `REMITTANCE_ALREADY_COMPLETED`, y la remesa queda marcada `DUPLICATE_DELIVERY` para revisión (dos personas dicen haberla entregado) |
| `DELIVER_WITH_PIN` con PIN incorrecto y `strictPin: false` o ausente (acción guardada sin conexión) | `APPLIED`, `flaggedForReview: true`, revisión `PIN_MISMATCH`. Sin conexión no se puede comprobar el PIN antes de entregar; nunca se envía el PIN ni su hash al teléfono. |
| `DELIVER_WITH_PIN` con PIN incorrecto y `strictPin: true` (la app la envía al momento, con red) | `REJECTED`, `INCORRECT_PIN`, como el endpoint online: el repartidor vuelve a pedir el PIN al beneficiario. Así la app usa siempre la cola, con o sin red. |
| Posponer o reportar incidencia en una remesa que ya no es del usuario | `REJECTED` con el código normal (`REMITTANCE_NOT_FOUND`): no movieron dinero, no hace falta aceptarlas. |

### Códigos nuevos

| Código | Status | Cuándo |
| --- | --- | --- |
| `INVALID_OCCURRED_AT` | en `results` | `occurredAt` futuro o anterior a la remesa; o de hace más de 7 días en `POSTPONE` y `REPORT_INCIDENT` |
| `ACTION_ID_CONFLICT` | en `results` | ese `id` ya lo usó otra persona o para otra acción |

## 4. Revisión de remesas

`RemittanceView` gana un campo `review`, `null` o:

```json
{ "reason": "DELIVERED_AFTER_REASSIGNMENT | PIN_MISMATCH | DUPLICATE_DELIVERY | LATE_SYNC", "flaggedAt": "…", "actionId": "…" }
```

- `review` viene en todo `RemittanceView`: detalle, listados, `GET /remittances/assigned`, `GET /sync/state` y los
  resultados de `POST /sync/actions`. El repartidor ve así que una entrega suya quedó marcada.

- Panel: `GET /remittances?review=true` lista las marcadas (filtro nuevo, junto a `late` e `incident`).
- `POST /remittances/{id}/review/resolve` con `{ "note": string }` la desmarca y deja la nota en el historial. Permiso
  nuevo `remittances:review` (ADMIN y SALES).
- Historial (`GET /remittances/{id}/events`): eventos nuevos `REVIEW_FLAGGED` y `REVIEW_RESOLVED`, con los campos de
  siempre. `EventView` gana `reviewReason` (el motivo en `REVIEW_FLAGGED`, `null` en los demás tipos), igual que
  `incidentReason`; la nota de la resolución va en `note`.

## 5. Solo con conexión

No entran en la cola offline (necesitan cotización o saldos al día):

- Registrar remesas (`POST /remittances`).
- Asignar o reasignar.
- Movimientos de caja entre el negocio y los repartidores (funding, returns, depósitos, retiros).
- Configuración, usuarios y roles.

## 6. Caja

Una entrega o recogida aplicada desde la cola mueve la caja como la online (`DELIVERY_PAYOUT`, `PICKUP_COLLECTION`),
con fecha `occurredAt`. En `DELIVERED_AFTER_REASSIGNMENT` el movimiento va a la caja del usuario que entregó.
