# Contrato: páginas y total en las listas de clientes y beneficiarios

Estado: **implementado en develop** (2026-10-13). Propuesta del frontend, pedida por el usuario: con 339 beneficiarios
reales, las listas del panel (máximo 50 filas, sin total) ya no bastan.

## Petición

```
GET /api/v1/customers?q=&page=0&size=50          permiso customers:read
GET /api/v1/beneficiaries?q=&page=0&size=50      permiso customers:read
```

- **Con `page`** (desde 0) la respuesta es una página. **Sin `page`**, la respuesta es exactamente la de antes: una
  lista de como mucho 50 (la usan las apps instaladas y la búsqueda del formulario de remesas).
- `size`: por defecto 50, entre 1 y 100. Fuera de rango, o `page` negativo: 400 `VALIDATION_FAILED`.
- `q` (opcional): el mismo filtro que la lista, un fragmento del nombre o del teléfono.
- Clientes: todos los que coinciden. Beneficiarios: solo los activos, como la lista.
- Orden estable entre páginas: el más reciente primero (`createdAt` descendente y, a igualdad, `id`), con o sin `q`.

## Respuesta 200

```json
{ "items": [ { "id": "…", "fullName": "Rosa Martínez", "…": "…" } ], "page": 0, "size": 50, "totalItems": 339 }
```

- `items`: los mismos `CustomerInfo` / `BeneficiaryInfo` de siempre.
- `totalItems`: todas las coincidencias (sin `q`, todos los registros del conjunto). Una página más allá del final
  devuelve `items: []` con el total real.
- La misma forma que `GET /remittances`.
