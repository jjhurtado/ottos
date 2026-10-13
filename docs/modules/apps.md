# Módulo `apps` — Versiones de la app móvil

Paquete `org.jobits.ottos.apps`. Fase 1.

## Para qué sirve

Saber qué build de la app móvil debe usar cada plataforma (Android por APK directo, iOS por TestFlight), para que la
app avise de que hay versión nueva u obligue a actualizar, y para que el backend rechace los builds por debajo del
mínimo. Contrato: `docs/contracts/app-version.md`.

## API pública

- `AppVersionService`: `current(platform)` (último build, mínimo, enlace) y `minimumBuild(platform)`.
- `Platform`: `ANDROID`, `IOS`.

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /app/version?platform=` | público |

Además, `AppBuildFilter` responde 426 `APP_UPDATE_REQUIRED` a las peticiones con `X-App-Platform` y `X-App-Build`
por debajo del mínimo, salvo login, refresh, logout, `POST /sync/actions` y la consulta de versión.

## Datos

`apps.app_releases` (`V1__baseline.sql`, sección *apps*): una fila por build publicado; nunca se edita ni se borra.

## Reglas

- **Solo se publica con un script de datos** (`scripts/data/app-releases/`), que ejecuta infraestructura. No hay
  endpoint para registrar versiones: una credencial robada podría anunciar una "actualización obligatoria" falsa a
  todos los clientes o bloquearlos a todos.
- El enlace de descarga no se guarda; se construye con `ottos.apps.android-download-url` (con `{version}`) y
  `ottos.apps.ios-download-url`, así que solo puede apuntar a la dirección configurada.
- Último build = el más alto; mínimo = el más alto obligatorio (0 si ninguno).
- Las versiones se guardan en memoria y se releen como mucho una vez por minuto: el bloqueo no consulta la base en
  cada petición y una versión nueva se ve en un minuto sin reiniciar.

## Dónde tocar

- Publicar una versión: un script en `scripts/data/app-releases/` con su `INSERT`.
- Qué rutas se permiten a un build viejo: `AppBuildFilter.ALWAYS_ALLOWED`.
