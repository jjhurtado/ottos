# Módulo `beneficiaries` — Beneficiarios

Paquete `org.jobits.ottos.beneficiaries`. Fase 1.

## Para qué sirve

Las personas que reciben el dinero (o lo entregan, en las recogidas), con su dirección de entrega, y sus vínculos con
los clientes que les envían. Un beneficiario puede estar vinculado a varios clientes.

## API pública

- `BeneficiaryService`: `find` e `isLinked` (los usa `remittances` al registrar), búsqueda, alta, edición, vínculos,
  borrado y restauración.

## Endpoints

| Endpoint | Permiso |
| --- | --- |
| `GET /beneficiaries?q=`, `GET /beneficiaries/{id}`, `GET /customers/{id}/beneficiaries` | `customers:read` |
| `POST /customers/{id}/beneficiaries` (crear y vincular), `PUT /beneficiaries/{id}` | `customers:write` |
| `PUT` / `DELETE /customers/{id}/beneficiaries/{beneficiaryId}` (vincular / desvincular de ese cliente) | `customers:write` |
| `DELETE /beneficiaries/{id}` (borrar para todos), `POST /beneficiaries/{id}/restore` | `customers:write` |

## Datos

`beneficiaries`, `customer_beneficiaries` (`V5__customers_and_beneficiaries.sql`).

## Reglas

- La dirección exige un municipio conocido (`UNKNOWN_MUNICIPALITY`, vía `ZoneService`); el teléfono se normaliza.
- **Borrar es lógico:** el beneficiario queda inactivo para todos los clientes; desaparece de búsquedas y listas, no
  puede recibir remesas (`BENEFICIARY_UNAVAILABLE` en `remittances`) ni vincularse (`BENEFICIARY_INACTIVE`). No se
  borra la fila porque las remesas apuntan a él. Restaurar lo reactiva con los vínculos que tenía.
- Desvincular de un cliente no afecta a los demás ni a las remesas pasadas.
- Las remesas guardan una copia de los datos del beneficiario: editarlo no cambia las remesas ya registradas.
