# Contrato: aviso y bloqueo de versiones de la app móvil

Estado: **en producción desde 1.0.0-rc.2** (tabla `apps.app_releases` en `V1__baseline.sql`, módulo `apps`). Decisiones del
usuario: Android se distribuye por Play Store (antes APK directo) e iOS por TestFlight; las versiones viejas se
bloquean también en el servidor; las versiones las registra el backend con un script de datos que ejecuta
infraestructura, nunca un usuario o un script con credenciales de la API.

## Qué sabe el backend

Una fila por build publicado en `app_releases` (plataforma, build, nombre de versión, si es obligatoria, notas). Para
cada plataforma:

- **Último build:** el más alto publicado. De él salen el nombre de versión, las notas y el enlace de descarga.
- **Build mínimo:** el más alto de los marcados como obligatorios; 0 si no hay ninguno.

El enlace de descarga no se guarda: el servidor lo construye con una dirección fija por plataforma
(`OTTOS_ANDROID_DOWNLOAD_URL`, con `{version}` sustituido por el nombre de versión, y `OTTOS_IOS_DOWNLOAD_URL`, el
enlace de TestFlight). Nunca puede apuntar a otro sitio.

## 1. Consultar la versión

```
GET /api/v1/app/version?platform=ANDROID      público, sin token (sirve en la pantalla de login)
```

`platform`: `ANDROID` o `IOS` (otro valor: 400 `INVALID_PARAMETER`).

```json
{ "platform": "ANDROID", "latestBuild": 15, "latestVersion": "1.0.0-rc.3", "minimumBuild": 14,
  "downloadUrl": "https://descargas.remesadeotto.com/ottos-1.0.0-rc.3.apk", "notes": "…" }
```

Qué hace la app con su propio build:

| Su build | Qué muestra |
| --- | --- |
| Menor que `minimumBuild` | Pantalla "Actualiza para continuar", sin poder cerrarla, con el enlace |
| Menor que `latestBuild` | Aviso que se puede cerrar ("Hay una versión nueva") |
| Igual o mayor | Nada |

Sin versiones publicadas para la plataforma: `latestBuild` 0, `latestVersion` y `downloadUrl` `null`, `minimumBuild` 0.
`downloadUrl` es `null` si la dirección no está configurada. La app consulta al arrancar y al volver a primer plano
cuando hay red; sin red, usa la última respuesta guardada.

## 2. Bloqueo en el servidor

La app móvil envía en **todas** sus peticiones:

```
X-App-Platform: ANDROID          (o IOS)
X-App-Build: 14                  (versionCode en Android, CFBundleVersion en iOS)
```

Si el build es menor que el mínimo de su plataforma, el backend responde **426** `APP_UPDATE_REQUIRED`:

```json
{ "status": 426, "code": "APP_UPDATE_REQUIRED", "detail": "…", "minimumBuild": 14, "latestBuild": 15,
  "latestVersion": "1.0.0-rc.3", "downloadUrl": "https://…" }
```

y la app muestra la pantalla de actualizar.

- **Siempre permitidas**, aunque el build esté por debajo: `POST /auth/login`, `/auth/refresh`, `/auth/logout`,
  `POST /sync/actions` y `GET /app/version`. Así el repartidor sube las entregas que ya hizo sin conexión antes de
  actualizar. La app debe vaciar su cola primero y después mostrar la pantalla de actualizar.
- Sin las dos cabeceras (el panel web, o builds anteriores a este cambio) no se comprueba nada. Una plataforma o un
  build mal formados se ignoran igual.

## 3. Publicar una versión

Lo hace el backend, no la app, con un **script de datos** (`scripts/data/app-releases/AAAAMMDD-<plataforma>-<versión>.sql`)
que inserta su fila:

```sql
INSERT INTO apps.app_releases (id, platform, build, version_name, mandatory, notes, released_at)
SELECT gen_random_uuid(), 'IOS', 5, '1.0.0-rc.3', FALSE, 'Notas…', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM apps.app_releases WHERE platform = 'IOS' AND build = 5);
```

El frontend avisa al backend con plataforma, build, nombre de versión, si es obligatoria y las notas; el script entra
por una rama a `develop` y lo ejecuta infraestructura en staging y en producción (con la orden del usuario). El
backend lo recoge en como mucho un minuto, sin reiniciar. No hay endpoint ni credencial para publicar versiones.
Una versión es obligatoria cuando las anteriores ya no funcionan bien con el backend (por ejemplo, un cambio del
contrato). La primera versión (Android 1.0.0-rc.3) está en `scripts/data/app-releases/20261013-android-1.0.0-rc.3.sql`.

## Códigos nuevos

`APP_UPDATE_REQUIRED` (426).
