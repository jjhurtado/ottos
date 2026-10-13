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
| `GET /customers?q=` (con `page`/`size`, una página con `totalItems`; contrato `docs/contracts/customer-beneficiary-pages.md`), `GET /customers/{id}` | `customers:read` |
| `POST /customers`, `PUT /customers/{id}` | `customers:write` |

Las estadísticas de un cliente (`GET /customers/{id}/stats`) viven en `remittances`.

## Datos

`customers` (esquema `customers`, `V1__baseline.sql`, sección *customers*; ni el teléfono ni el documento son únicos): nombre, teléfono,
correo, documento, dirección, notas, activo.

## Reglas

- El teléfono se guarda normalizado (solo dígitos y `+` inicial, `Phones.normalize`) y debe tener dígitos
  (`INVALID_PHONE`).
- **Un cliente es su nombre, teléfono y dirección.** Una persona con dos direcciones son dos clientes con el mismo
  nombre y teléfono (no hay modelo de varias direcciones). Ni el teléfono ni el documento son únicos; solo se rechaza
  un cliente idéntico (mismo nombre, teléfono y dirección, sin distinguir mayúsculas ni espacios):
  `CUSTOMER_ALREADY_REGISTERED`, con el `customerId` existente.
- La búsqueda coincide por fragmento de nombre o de teléfono; sin texto devuelve los más recientes (máximo 50).
