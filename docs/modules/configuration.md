# Módulo `configuration` — Configuración

Paquete `org.jobits.ottos.configuration`. Fase 1.

## Para qué sirve

Todo valor que el negocio puede cambiar sin desplegar, gestionado desde un solo sitio (una pantalla del panel):

- **Ajustes:** monto mínimo por remesa (USD; 0 = sin mínimo) y días de entrega por defecto.
- **Corredores** (pares de monedas: USD-CUP, USD-USD) y, para cada uno, el historial de **tasas de cambio** y de
  **reglas de comisión**, por separado para entregas y para recogidas.

Regla del proyecto (`STANDARDS.md` › Configuración): los demás módulos leen sus valores aquí y no guardan ajustes propios.

## API pública

- `ConfigurationService`: lectura para otros módulos (`minimumAmount`, `defaultDeliveryDays`, `activeCorridor`,
  `corridors`) y administración (ajustes, tasas, comisiones).
- `FeeType` (`PERCENTAGE`, `FIXED`) y `ServiceType` (`DELIVERY`, `PICKUP`; espejo de `RemittanceType`, porque este
  módulo no puede depender de `remittances`).

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /configuration` (todo junto), `GET /configuration/settings`, `GET /configuration/corridors` | `configuration:read` |
| `GET /configuration/corridors/{code}/rates`, `/fee-rules` (`?remittanceType=DELIVERY\|PICKUP`) | `configuration:read` |
| `PUT /configuration/settings` (solo los campos enviados) | `configuration:write` |
| `POST /configuration/corridors/{code}/rates`, `/fee-rules` (`remittanceType` opcional, `DELIVERY` por defecto) | `configuration:write` |

## Datos

`settings` (`V8__configuration.sql`), `corridors`, `exchange_rates`, `fee_rules` (`V4__rates.sql`, columna
`remittance_type` en `V10__pickup_rates.sql`).

## Reglas

- **Tasas y comisiones nunca se editan:** cada cambio es una fila nueva que rige desde ese momento (`valid_from`); la
  vigente es la última. Cada remesa guarda una copia de lo que usó.
- **Entregas y recogidas** tienen tasa y comisión propias. La de recogida empezó en 1 (1x1); su comisión empezó como
  copia de la de entrega.
- Un corredor de la misma moneda (USD-USD) siempre tiene tasa 1 (`FIXED_RATE`).
- Comisión porcentual ≤ 100 (`FEE_PERCENTAGE_TOO_HIGH`); `minFee ≤ maxFee` (`INVALID_FEE_RANGE`).
- Ajustes: mínimo ≥ 0 con 2 decimales; días de entrega entre 1 y 60.
- Los valores concretos (por ejemplo, el mínimo de 50 USD) se fijan desde la API o el panel, **nunca en migraciones**;
  un entorno nuevo empieza con los valores por defecto.

## Dónde tocar

- Ajuste nuevo: fila en `settings` (migración), constante en `domain.Setting`, método tipado en `ConfigurationService`
  y su campo en `SettingsView` y en `PUT /configuration/settings`.
- Corredor nuevo: migración en `corridors` (y sus tasas y comisiones iniciales si las necesita).
