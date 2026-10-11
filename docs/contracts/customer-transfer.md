# Contrato: importar y exportar clientes con sus beneficiarios

Estado: **implementado; pendiente de revisión del frontend** (2026-10-11). Decisiones del negocio en
[ROADMAP.md › Reglas del negocio decididas en 1.0](../../ROADMAP.md).

El archivo (Excel o CSV) lo lee y lo genera el frontend; el backend recibe y devuelve JSON. Todas las rutas empiezan
por `/api/v1`. Errores: ProblemDetail con `code`.

## 1. Cambio de regla: clientes duplicados

Una persona puede tener dos direcciones, y no hay un modelo de varias direcciones: es otro cliente con el mismo nombre
y teléfono.

- El teléfono y el documento del cliente **dejan de ser únicos**.
- Solo se rechaza un cliente **idéntico** a otro: mismo nombre, mismo teléfono y misma dirección (sin distinguir
  mayúsculas ni espacios repetidos o de los extremos; el teléfono normalizado; sin dirección = sin dirección). Al crear o editar:
  409 `CUSTOMER_ALREADY_REGISTERED`, con `customerId` del existente.
- `PHONE_ALREADY_REGISTERED` ya no se devuelve.

## 2. Exportar

```
GET /customers/export        permiso customers:export (nuevo, solo ADMIN)
```

```json
{
  "exportedAt": "2026-10-11T18:00:00Z",
  "customers": [
    {
      "id": "…", "fullName": "Ana Pérez", "phone": "+13055550101", "email": null,
      "documentType": null, "documentNumber": null, "address": "…", "notes": null,
      "active": true, "createdAt": "…",
      "beneficiaries": [
        { "id": "…", "fullName": "Rosa Martínez", "phone": "5352223344", "alternatePhone": null,
          "street": "Calle 10", "houseNumber": "512", "betweenStreets": null, "neighborhood": null,
          "municipalityCode": "2302", "municipalityName": "Plaza de la Revolución", "reference": null,
          "documentNumber": null, "notes": null, "active": true }
      ]
    }
  ]
}
```

- Todos los clientes, ordenados por nombre, con todos sus beneficiarios vinculados (también los inactivos, con
  `active`). Un beneficiario vinculado a varios clientes aparece bajo cada uno.
- Queda en la auditoría (`CUSTOMERS_EXPORTED`, con cuántos clientes).

## 3. Importar

```
POST /customers/import       permiso customers:import (nuevo, solo ADMIN)
```

```json
{
  "dryRun": true,
  "customers": [
    {
      "ref": "fila 2",
      "fullName": "Ana Pérez", "phone": "+1 305 555 0101", "email": null, "documentType": null,
      "documentNumber": null, "address": "Miami", "notes": null,
      "beneficiaries": [
        { "ref": "fila 2", "fullName": "Rosa Martínez", "phone": "53 5222 3344", "alternatePhone": null,
          "street": "Calle 10", "houseNumber": "512", "betweenStreets": "Línea y 23", "neighborhood": null,
          "municipality": "Plaza de la Revolución", "reference": null, "documentNumber": null, "notes": null }
      ]
    }
  ]
}
```

- `dryRun` (por defecto `false`): `true` evalúa todo y devuelve el mismo resultado sin guardar nada (vista previa).
- `ref` (opcional, ≤ 100): lo que el frontend quiera para localizar la fila en el archivo; se devuelve tal cual.
- `municipality`: código (`2302`) o nombre (`Plaza de la Revolución`, sin distinguir mayúsculas ni acentos).
- Obligatorios: cliente `fullName` y `phone`; beneficiario `fullName`, `phone`, `street` y `municipality`.
- Máximo 500 clientes por petición (400 `VALIDATION_FAILED` si hay más); el frontend parte archivos más grandes.

### Coincidencias

| Qué | Ya existe si coinciden | Si ya existe |
| --- | --- | --- |
| Cliente | nombre, teléfono y dirección | No se modifica; sus beneficiarios se vinculan a él |
| Beneficiario | nombre, teléfono, calle, número y municipio | No se modifica; se vincula al cliente si no lo estaba |

Lo que no coincide se crea, aunque comparta teléfono con otro.

### Respuesta 200

```json
{
  "dryRun": false,
  "summary": { "customersCreated": 1, "customersExisting": 0, "customersRejected": 0,
               "beneficiariesCreated": 1, "beneficiariesExisting": 0, "beneficiariesRejected": 0, "linksCreated": 1 },
  "results": [
    { "index": 0, "ref": "fila 2", "status": "CREATED", "customerId": "…", "code": null, "message": null,
      "beneficiaries": [
        { "index": 0, "ref": "fila 2", "status": "CREATED", "beneficiaryId": "…", "linked": true,
          "code": null, "message": null }
      ] }
  ]
}
```

- Un resultado por cliente y por beneficiario, en el mismo orden. `status`: `CREATED`, `EXISTING` o `REJECTED`.
- `REJECTED` lleva `code` y `message` con los códigos de siempre: `VALIDATION_FAILED` (falta un obligatorio o un texto
  es demasiado largo), `INVALID_PHONE`, `UNKNOWN_MUNICIPALITY`.
- Un cliente rechazado no procesa sus beneficiarios (lista vacía). Un beneficiario rechazado no impide los demás.
- `linked`: `true` si se creó el vínculo en esta importación; `false` si ya existía.
- Cada cliente se guarda con sus beneficiarios en su propia transacción. En `dryRun` los ids de lo que se crearía son
  `null`.
- Una importación real queda en la auditoría (`CUSTOMERS_IMPORTED`, con el resumen). La vista previa no.

## 4. Beneficiarios sin cliente

El negocio tiene la lista de todos los beneficiarios (un chat de WhatsApp) pero no la de sus remitentes. Los
beneficiarios se cargan sueltos y se van vinculando a los clientes poco a poco.

### Importar beneficiarios sueltos

```
POST /beneficiaries/import   permiso customers:import (solo ADMIN)
```

```json
{
  "dryRun": true,
  "beneficiaries": [
    { "ref": "fila 2", "fullName": "Rosa Martínez", "phone": "53 5222 3344", "alternatePhone": null,
      "street": "Calle 10", "houseNumber": "512", "betweenStreets": "Línea y 23", "neighborhood": null,
      "municipality": "Plaza de la Revolución", "reference": null, "documentNumber": null, "notes": null }
  ]
}
```

- Cada beneficiario tiene los mismos campos, obligatorios (`fullName`, `phone`, `street`, `municipality`), coincidencias
  y rechazos que en la importación de clientes. Un beneficiario existente pero borrado se rechaza con
  `BENEFICIARY_INACTIVE`. Máximo 500 por petición; cada uno en su propia transacción.
- No se vincula a ningún cliente. Si después se importa un cliente con ese mismo beneficiario, se reutiliza y se vincula.

Respuesta 200:

```json
{
  "dryRun": false,
  "summary": { "created": 1, "existing": 0, "rejected": 0 },
  "results": [
    { "index": 0, "ref": "fila 2", "status": "CREATED", "beneficiaryId": "…", "linked": false, "code": null, "message": null }
  ]
}
```

`results` tiene la misma forma que los beneficiarios de la importación de clientes; `linked` siempre es `false`. Una
importación real queda en la auditoría (`BENEFICIARIES_IMPORTED`, con el resumen).

### Crear un beneficiario suelto

```
POST /beneficiaries          permiso customers:write
```

El mismo cuerpo que `POST /customers/{id}/beneficiaries` (`municipalityCode`). Devuelve 201 con el beneficiario, sin
vincularlo a ningún cliente.

### Vinculación gradual

- `GET /beneficiaries?q=` ya busca en **todos** los beneficiarios activos (nombre o teléfono), estén vinculados o no.
- **Registrar una remesa vincula al cliente con el beneficiario** si no lo estaban. Ya no hace falta vincular antes con
  `PUT /customers/{id}/beneficiaries/{beneficiaryId}`, y desaparece el error `BENEFICIARY_NOT_LINKED`.

## Permisos nuevos

| Permiso | Para qué | Roles iniciales |
| --- | --- | --- |
| `customers:export` | Exportar todos los clientes con sus beneficiarios (datos personales) | ADMIN |
| `customers:import` | Importar clientes y beneficiarios | ADMIN |

## Códigos

Nuevo: `CUSTOMER_ALREADY_REGISTERED`. Dejan de usarse: `PHONE_ALREADY_REGISTERED` y `BENEFICIARY_NOT_LINKED` (las
remesas vinculan solas).
