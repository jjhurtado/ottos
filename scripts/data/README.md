# Scripts de datos

Operaciones sobre los **datos** que no cambian la estructura de la base: registrar una versión de la app, corregir
filas después de una importación, etc. No son migraciones de Flyway (regla de `STANDARDS.md`): las migraciones son
solo para cambios estructurales.

## Reglas

- **Idempotente:** ejecutarlo dos veces no cambia nada la segunda vez (`WHERE NOT EXISTS`, `ON CONFLICT DO NOTHING`).
- **Un archivo por operación**, con fecha y descripción en el nombre: `AAAAMMDD-<descripcion>.sql`. Empieza con un
  comentario que dice qué hace, por qué y quién lo pidió.
- **Revisado en una rama** como cualquier código: entra por `feature/` a `develop`. El repositorio es el registro de qué
  se ejecutó.
- **Lo ejecuta infraestructura**, que tiene acceso a la base: primero en staging, después en producción, siempre con
  una copia de seguridad antes. Producción solo con la orden explícita del usuario.
- Cuando cambia lo que ve la app (por ejemplo, una versión nueva), el backend lo recoge solo en como mucho un minuto,
  sin reiniciar.

Ejecución:

```
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/data/app-releases/20261014-ios-1.0.0-rc.3.sql
```

## Carpetas

| Carpeta | Para qué |
| --- | --- |
| `app-releases/` | Registrar una versión publicada de la app móvil (`app_releases`; contrato `docs/contracts/app-version.md`) |
| `fixes/` | Correcciones puntuales de datos |

## Historial

Antes de esta regla (2026-10-13), dos operaciones de datos se hicieron como migración: `V3__app_release_android_1_0_0_rc_3.sql`
y `V5__fix_imported_pickups.sql`. Se quedan como migraciones porque ya están aplicadas en staging y producción.
