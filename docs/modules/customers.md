# Módulo `customers` — Clientes

Paquete `org.jobits.ottos.customers`. Fase 1.

## Para qué sirve

Remitentes: las personas que envían remesas. Los registra Ventas; todavía no tienen cuenta ni acceso (llegará con la
App Cliente, Fase 2).

## API pública

- `CustomerService`: buscar por id (`find`, `get`), buscar por texto, registrar y actualizar. `remittances` y
  `beneficiaries` usan `find`/`get`.

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /customers?q=`, `GET /customers/{id}` | `customers:read` |
| `POST /customers`, `PUT /customers/{id}` | `customers:write` |

Las estadísticas de un cliente (`GET /customers/{id}/stats`) viven en `remittances`.

## Datos

`customers` (`V1__baseline.sql`, sección *customers*): nombre, teléfono, correo, documento,
dirección, notas, activo.

## Reglas

- El **teléfono identifica al cliente**: se guarda normalizado (solo dígitos y `+` inicial, `Phones.normalize`) y es
  único (`PHONE_ALREADY_REGISTERED`). Debe tener dígitos (`INVALID_PHONE`).
- La búsqueda coincide por fragmento de nombre o de teléfono; sin texto devuelve los más recientes (máximo 50).
