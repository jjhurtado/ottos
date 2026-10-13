# Scripts de datos

Operaciones sobre los **datos** que no cambian la estructura de la base: registrar una versión de la app, corregir
filas después de una importación, etc. No son migraciones de Flyway (regla de `STANDARDS.md`): las migraciones son
solo para cambios estructurales.

## Orden al crear un entorno

1. Base vacía → arrancar el backend: Flyway crea los esquemas con la `V1` y se crea el admin (`OTTOS_ADMIN_*`).
2. `seed/cuba-la-habana.sql` (el alta de la instalación).
3. En staging, los datos de prueba (`seed_staging.py` de infraestructura); en producción, la carga real.
4. `app-releases/…` de las versiones publicadas.

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
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/data/app-releases/20261013-android-1.0.0-rc.3.sql
```

## Carpetas

| Carpeta | Para qué |
| --- | --- |
| `seed/` | **Alta de una instalación**, una vez tras crear el entorno y su primer arranque: moneda del negocio, regla de teléfonos, zonas, corredores, tasas y comisiones iniciales, roles (`cuba-la-habana.sql`) |
| `app-releases/` | Registrar una versión publicada de la app móvil (`app_releases`; contrato `docs/contracts/app-version.md`) |
| `fixes/` | Correcciones puntuales de datos |

## Historial

Antes de esta regla, dos operaciones de datos se hicieron como migración (`V3`: versión Android 1.0.0-rc.3; `V5`:
corregir tres recogidas importadas como remesas). El 2026-10-13 las bases se recrearon con una `V1` única, solo
estructural: la versión Android quedó en `app-releases/20261013-android-1.0.0-rc.3.sql` y la corrección dejó de hacer
falta, porque las recogidas se volvieron a cargar como recogidas.

Todo el SQL lleva el esquema del módulo delante (`apps.app_releases`, `remittances.remittances`).
