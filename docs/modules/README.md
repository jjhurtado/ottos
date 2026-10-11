# Módulos del backend

Un documento por módulo con lo que hace, su API pública, endpoints y permisos, datos, reglas y dónde tocar para
cambiar algo. Se actualiza en la misma rama que cambia el módulo (ver `STANDARDS.md`).

| Módulo | Qué hace | Estado |
| --- | --- | --- |
| [identity](identity.md) | Personal, login JWT, refresh tokens, roles y permisos | Implementado |
| [branches](branches.md) | Provincias y municipios; sucursales (una sola en 1.0) | Zonas implementadas |
| [customers](customers.md) | Remitentes | Implementado |
| [beneficiaries](beneficiaries.md) | Beneficiarios, sus direcciones y vínculos con clientes | Implementado |
| [configuration](configuration.md) | Ajustes, corredores, tasas y comisiones (entrega y recogida) | Implementado |
| [rates](rates.md) | Cotizador | Implementado |
| [remittances](remittances.md) | Remesas: registro, workflow, asignación, incidencias, entrega, estadísticas | Implementado |
| [payments](payments.md) | Caja del negocio y de cada repartidor (doble entrada) | Libro implementado; cierre en Fase 3 |
| [dispatch](dispatch.md) | Asignación por zona, prueba de entrega | Vacío (después de 1.0) |
| [notifications](notifications.md) | Push, correo, SMS | Vacío (Fase 2) |
| [reporting](reporting.md) | KPIs, exportes, auditoría | Vacío (Fase 4) |

Dependencias entre módulos (solo por su API pública o eventos):

```
remittances → customers, beneficiaries, rates, configuration, identity, branches
rates       → configuration
beneficiaries → customers, branches
payments    → identity  (+ escucha eventos de remittances)
```
