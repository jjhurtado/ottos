# Módulo `identity` — Identidad y acceso

Paquete `org.jobits.ottos.identity`. Fase 0.

## Para qué sirve

Cuentas del personal (Admin, Ventas, Repartidores), inicio de sesión con JWT, refresh tokens y control de acceso por
permisos (RBAC dinámico). Los clientes (remitentes) no tienen cuenta todavía.

## API pública (para otros módulos)

- `StaffDirectoryService`: buscar un miembro del personal y sus permisos efectivos (`find`), y listar los activos que
  tienen un permiso (`activeWith`). Lo usan `remittances` (repartidores asignables) y `payments` (caja por repartidor).

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `POST /auth/login`, `/auth/refresh`, `/auth/logout` | público |
| `GET /auth/me`, `POST /auth/change-password` | autenticado |
| `GET /users`, `GET /users/{id}` | `users:read` |
| `POST /users`, `PUT /users/{id}`, `PUT /users/{id}/roles`, `PUT /users/{id}/password`, `POST /users/{id}/activate`, `/deactivate` | `users:write` |
| `GET /permissions`, `GET /roles`, `GET /roles/{id}` | `roles:read` |
| `POST /roles`, `PUT /roles/{id}`, `PUT /roles/{id}/permissions`, `DELETE /roles/{id}` | `roles:write` |

## Datos

`users`, `roles`, `user_roles`, `permissions`, `role_permissions`, `refresh_tokens` (migración `V1__identity.sql`;
`client` y `replaced_by` en `V11__mobile_sessions.sql`; cada módulo añade sus permisos en su propia migración).

## Reglas

- **Tokens:** access token JWT HS256 con `roles`, `permissions` y `client`; refresh token opaco de un solo uso (30
  días), guardado solo como hash SHA-256 y rotado en cada refresh.
- **Tipo de cliente:** el login acepta `client` (`WEB` por defecto, `MOBILE` para la app). Web:
  `ottos.security.access-token-ttl` (15 min; 8 h en `dev`). Móvil: `ottos.security.mobile-access-token-ttl` (14 h),
  para trabajar la jornada sin conexión. El refresh token recuerda el tipo y emite del mismo.
- **Reutilización de refresh token:** presentar uno ya usado revoca todas las sesiones del usuario (se asume robo).
  Excepción: dentro de `ottos.security.refresh-reuse-grace` (60 s) desde su uso, se toma como reintento por mala red:
  se revoca el par que no llegó (`replaced_by`) y se emite uno nuevo. No aplica tras logout o cierre de todas las
  sesiones.
- **Limpieza:** `RefreshTokenCleanup` borra a diario los caducados (`ottos.security.refresh-token-cleanup-cron`); los
  revocados se conservan hasta que caducan para seguir detectando reutilización.
- **RBAC:** los permisos son el catálogo que comprueba el código (`@PreAuthorize("hasAuthority('…')")`) y solo se crean
  por migración. Los roles son datos: el Admin los crea y edita. `ADMIN` es built-in (no se edita) y debe tener todos
  los permisos (`PermissionCatalogTest`).
- **Protecciones:** nadie concede o quita permisos que no tiene (`PERMISSION_ESCALATION`); siempre queda al menos un
  administrador activo (`LAST_ADMIN`); nadie se desactiva a sí mismo (`CANNOT_DEACTIVATE_SELF`); un rol asignado no se
  borra (`ROLE_IN_USE`).
- **Cambios de permisos** llegan al usuario en el siguiente login o refresh. Desactivar o cambiar contraseña revoca sus
  refresh tokens.
- **Primer admin:** `InitialAdminBootstrap` lo crea al arrancar desde `OTTOS_ADMIN_EMAIL` / `OTTOS_ADMIN_PASSWORD` si no
  existe ninguno.
- Los roles semilla y las descripciones de permisos están en inglés; el frontend traduce por código.

## Dónde tocar

- Duración de tokens: `JwtProperties` / `application*.yml`.
- Contenido del JWT: `TokenServiceImpl`; su lectura: `SecurityConfig`.
- Rutas públicas y manejo de 401/403: `SecurityConfig`, `SecurityProblemHandlers`.
- Permiso nuevo: migración que lo inserta y lo concede a `ADMIN`, y su uso en `@PreAuthorize`.

## Pendiente

Auditoría de cambios de usuarios y roles (antes de la RC).
