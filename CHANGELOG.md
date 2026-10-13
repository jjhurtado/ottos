# Changelog

Cambios notables de Ottos por versión. Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y
versionado según [ROADMAP.md › Versionado](ROADMAP.md#3-versionado). Cómo y cuándo se escribe: [STANDARDS.md](STANDARDS.md#changelog).

## [Unreleased]

### Cambiado
- **Incompatible (operación):** la base de datos está dividida en un esquema por módulo (`identity`, `branches`,
  `customers`, `beneficiaries`, `configuration`, `remittances`, `payments`, `reporting`, `apps`); `public` solo guarda
  el historial de Flyway. Todas las migraciones se reunieron en una `V1__baseline.sql` única y solo estructural, y
  staging y producción se recrearon desde cero el 2026-10-13. Las consultas SQL directas deben llevar el esquema
  delante. La API no cambia.
- `POST /api/v1/remittances/historical` acepta `ref` (opcional): repetir la misma `ref` devuelve 200 con la remesa ya
  creada en vez de duplicarla.
- La versión Android 1.0.0-rc.3 se registra con `scripts/data/app-releases/20261013-android-1.0.0-rc.3.sql`.
- Versiones de la app: se publican con un script de datos (`scripts/data/app-releases/`) en vez de una migración, y
  `GET /app/version` las recoge en como mucho un minuto sin reiniciar. Las correcciones de datos también van en
  scripts; las migraciones quedan solo para cambios estructurales.

## [1.0.0-rc.3] - 2026-10-13

### Añadido
- `POST /api/v1/remittances/{id}/void` (permiso `remittances:void`, ADMIN): anula cualquier remesa con un motivo; deja
  de contar en informes, estadísticas, listas, el aviso a Ventas y la sincronización, y su caja se deshace con
  movimientos `REMITTANCE_VOID_REVERSAL`. `RemittanceView.voided`, filtro `voided` en `GET /remittances`, códigos
  `REMITTANCE_VOIDED` y `REMITTANCE_ALREADY_VOIDED` ([contrato](docs/contracts/void-and-historical.md)).
- `POST /api/v1/remittances/historical` (permiso `remittances:historical`, ADMIN): registra a mano una remesa (USD o
  CUP) o recogida (USD) antigua, completada en su fecha, sin repartidor ni caja.
- `GET /api/v1/customers` y `GET /api/v1/beneficiaries` con `page` (desde 0) y `size` (1–100, por defecto 50):
  devuelven una página `{items, page, size, totalItems}`, la más reciente primero. Sin `page`, la lista de siempre
  ([contrato](docs/contracts/customer-beneficiary-pages.md)).
- Versión de la app móvil Android 1.0.0-rc.3 (build 4), no obligatoria: `GET /app/version?platform=ANDROID` la anuncia
  como la última (migración `V3__app_release_android_1_0_0_rc_3.sql`).

## [1.0.0-rc.2] - 2026-10-13

### Añadido
- `GET /api/v1/app/version?platform=ANDROID|IOS` (público): último build, build mínimo y enlace de descarga de la app
  móvil, para avisar de que hay versión nueva u obligar a actualizar. Las versiones se publican solo por migración
  (tabla `app_releases`); el enlace sale de `OTTOS_ANDROID_DOWNLOAD_URL` y `OTTOS_IOS_DOWNLOAD_URL`
  ([contrato](docs/contracts/app-version.md)).
- Cabeceras `X-App-Platform` y `X-App-Build` en las peticiones de la app móvil: un build por debajo del mínimo recibe
  426 `APP_UPDATE_REQUIRED` (con `minimumBuild`, `latestBuild`, `latestVersion`, `downloadUrl`). Login, refresh,
  logout y `POST /sync/actions` siguen permitidos para vaciar la cola offline. Sin las cabeceras no se comprueba nada.
- `POST /api/v1/remittances/import` (permiso `customers:import`, ADMIN): importa remesas antiguas como entregadas en su
  fecha real, en USD, sin repartidor y con un cliente comodín; no mueve ninguna caja y cada lote queda en la auditoría
  (`HISTORICAL_REMITTANCES_IMPORTED`). Códigos
  nuevos: `BENEFICIARY_NOT_FOUND`, `AMBIGUOUS_BENEFICIARY` ([contrato](docs/contracts/historical-import.md)).

### Cambiado
- Datos: tres remesas importadas que eran recogidas (`chat-20261006-210318`, `chat-20260609-132726`,
  `chat-20260731-111256`) quedan anuladas y registradas de nuevo como recogidas en su fecha, sin comisión
  (`V5__fix_imported_pickups.sql`).
- Caja del negocio: registrar una remesa ya no crea el movimiento `REMITTANCE_PAYMENT`. La caja del negocio es el
  efectivo disponible en Cuba: sube con entradas y con lo que devuelven los repartidores (incluido lo recogido) y baja
  con fondeos y retiradas. Lo cobrado a los remitentes, que pagan fuera, se ve en el informe mensual. Los
  `REMITTANCE_PAYMENT` ya registrados se quedan como están.
- Base de datos: `V1__baseline.sql` queda congelada desde que hay producción. Los cambios de esquema llegan en
  migraciones nuevas (`V2__app_releases.sql`), que staging y producción aplican solos al desplegar, sin recrear la base.
- Git Flow: `main` (producción) solo recibe ramas `release/` y `hotfix/` con su tag; las features van a `develop`
  (staging).

## [1.0.0-rc.1] - 2026-10-11

Primera versión candidata: la Fase 1 completa (MVP de remesas). En producción desde el 2026-10-11 en
`api.remesadeotto.com`. Resume lo que contiene la versión; el detalle de cada parte está en los contratos de
`docs/contracts` y en los documentos de `docs/modules`.

### Añadido
- Acceso: login con JWT y refresh token de un solo uso con detección de reutilización; sesión de jornada completa en el
  móvil (`client: MOBILE`); usuarios del personal, roles y permisos editables por el admin.
- Configuración: monto mínimo, días de entrega y tolerancia de tasa; por corredor (USD-CUP, USD-USD), tasas y
  comisiones de entrega y de recogida con historial; cotizador (`POST /quotes`).
- Clientes y beneficiarios: alta, búsqueda, vínculos N:M, borrado lógico y restauración de beneficiarios,
  beneficiarios sin cliente que se vinculan al registrarles una remesa, importar y exportar en JSON.
- Remesas: entregas y recogidas con estados y transiciones configurables, PIN, asignación, atrasos con motivo,
  incidencias y revisiones; tasa y comisión propias en cada recogida; feed de remesas completadas para Ventas
  (`GET /remittances/completed`).
- Caja: contabilidad de doble entrada de la caja del negocio y de cada repartidor, fondeos, devoluciones, depósitos y
  retiradas, y cambio de moneda del repartidor marcado para revisión si la tasa se aleja de la configurada.
- App del repartidor sin conexión: descarga incremental (`GET /sync/state`) y cola de acciones idempotente
  (`POST /sync/actions`) con reglas de conflicto y revisión.
- Informes y auditoría: informe mensual de ventas (`GET /reports/monthly`) y registro de quién cambió qué
  (`GET /audit`).
- Errores como ProblemDetail con un `code` estable que el frontend traduce.

### Seguridad
- Límite de inicios de sesión fallidos por cuenta y por IP (429 `TOO_MANY_LOGIN_ATTEMPTS`); en producción la IP sale
  de la cabecera del proxy (`X-Real-Ip`), nunca de `X-Forwarded-For`.
- El token de un usuario desactivado o borrado deja de valer en la siguiente petición.
- Los administradores no cuentan como repartidores: no se les asignan remesas ni tienen caja de repartidor.
