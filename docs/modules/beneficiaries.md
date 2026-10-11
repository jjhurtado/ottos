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
| `POST /customers/{id}/beneficiaries` (crear y vincular), `POST /beneficiaries` (crear sin cliente), `PUT /beneficiaries/{id}` | `customers:write` |
| `PUT` / `DELETE /customers/{id}/beneficiaries/{beneficiaryId}` (vincular / desvincular de ese cliente) | `customers:write` |
| `DELETE /beneficiaries/{id}` (borrar para todos), `POST /beneficiaries/{id}/restore` | `customers:write` |
| `GET /customers/export` (todos los clientes con sus beneficiarios, JSON) | `customers:export` (ADMIN) |
| `POST /customers/import`, `POST /beneficiaries/import` (sin cliente) (`dryRun` para la vista previa) | `customers:import` (ADMIN) |

## Datos

`beneficiaries`, `customer_beneficiaries` (`V1__baseline.sql`, sección *beneficiaries*).

## Reglas

- La dirección exige un municipio conocido (`UNKNOWN_MUNICIPALITY`, vía `ZoneService`); el teléfono se normaliza.
- **Borrar es lógico:** el beneficiario queda inactivo para todos los clientes; desaparece de búsquedas y listas, no
  puede recibir remesas (`BENEFICIARY_UNAVAILABLE` en `remittances`) ni vincularse (`BENEFICIARY_INACTIVE`). No se
  borra la fila porque las remesas apuntan a él. Restaurar lo reactiva con los vínculos que tenía.
- **Beneficiarios sin cliente:** se pueden crear o importar sin ningún cliente (el negocio tiene la lista de
  beneficiarios, pero no sus remitentes). La búsqueda global (`GET /beneficiaries?q=`) los encuentra, y se vinculan
  a un cliente a mano o, como muy tarde, al registrarle una remesa: `remittances` llama a `link` al registrar.
- Desvincular de un cliente no afecta a los demás ni a las remesas pasadas.
- Las remesas guardan una copia de los datos del beneficiario: editarlo no cambia las remesas ya registradas.
- **Importar y exportar** (`CustomerTransferService`, contrato `docs/contracts/customer-transfer.md`): vive aquí porque
  trabaja con clientes y beneficiarios a la vez. El archivo lo lee y genera el frontend; aquí es JSON. Al importar, un
  cliente ya existe si coinciden nombre, teléfono y dirección, y un beneficiario si coinciden nombre, teléfono, calle,
  número y municipio (que se acepta por código o por nombre); lo existente no se modifica, solo se vincula. Cada
  cliente se guarda con sus beneficiarios en su propia transacción y un rechazo no frena el resto; `dryRun` calcula lo
  mismo sin guardar. Importaciones y exportaciones quedan en la auditoría. Los rechazos de un beneficiario se
  comprueban antes de llamar a `BeneficiaryService`, porque una excepción dentro de una llamada transaccional marcaría
  para deshacer toda la transacción del cliente.
