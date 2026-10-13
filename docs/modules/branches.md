# Módulo `branches` — Sucursales y zonas

Paquete `org.jobits.ottos.branches`. Fase 1.

## Para qué sirve

Catálogo de zonas de entrega: provincias y municipios oficiales de Cuba. Hoy solo La Habana (15 municipios).

## API pública

- `ZoneService`: provincias, municipios de una provincia y búsqueda de un municipio por código. Lo usan
  `beneficiaries` (validar la dirección) y `remittances` (nombre del municipio en vistas y estadísticas).

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /provinces`, `GET /municipalities?province=` | autenticado |

## Datos

`provinces`, `municipalities` (esquema `branches`, `V1__baseline.sql`, sección *branches*; códigos de la división político-administrativa).

## Reglas

- Las zonas son datos de referencia de solo lectura; para añadir provincias se escribe una migración nueva.

## Decisiones

- **Sucursales:** una sola en la versión 1.0, sin gestión de sucursales. Cuando haya más de una, este módulo las
  añadirá (y `payments`, cajas por sucursal).
- **Asignación por zona:** fuera de 1.0 (irá en `dispatch`).
