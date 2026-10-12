# Ottos — Roadmap

> Origen: doc "App de Remesas — Arquitectura y Roadmap"
> (https://claude.ai/code/artifact/f5b0a1ca-40c2-463d-a531-e2276fc2cce2), copiado el 2026-10-01 y mantenido aquí desde
> entonces. Cómo se trabaja (Git Flow, commits, arquitectura, tests): [STANDARDS.md](STANDARDS.md).

## 1. Dónde estamos

**Versión actual: `1.0.0-rc.1`.** La Fase 1 (MVP de remesas) está completa en el backend y se revisa la arquitectura
antes de dar la versión por estable. La rc.1 se cerró el 2026-10-11 tras la revisión de arquitectura y la prueba
general del frontend; lo que salga en staging va en una `rc.2`.

Lo que falta para `1.0.0`:

| # | Paso | Quién |
| --- | --- | --- |
| 1 | ~~Revisión de arquitectura y refactor sobre la RC~~ (hecha, 2026-10-11) | Usuario y backend |
| 2 | ~~Prueba general del frontend, con las pantallas de importar/exportar y de auditoría~~ (hecha, 2026-10-11) | Frontend |
| 3 | ~~Cerrar la rc.1~~ (2026-10-11: migraciones en `V1__baseline.sql`, tag `1.0.0-rc.1`); probar en staging | Infraestructura y frontend |
| 4 | Entorno de producción: base de datos con copias de seguridad, dominio, CORS, secretos | Infraestructura |
| 5 | Datos de cada entorno: tasa USD-CUP de entrega, monto mínimo, comisión de recogida por defecto, importar los beneficiarios | Admin del negocio |
| 6 | Revisión legal (licencias, conocer al cliente, antilavado) antes de mover dinero real | Negocio |
| 7 | Primera remesa real en producción: hito de la Fase 1 y versión `1.0.0` | Todos |

## 2. El producto

Plataforma para gestionar remesas de punta a punta: Ventas registra y cobra, un repartidor entrega el efectivo en el
domicilio del beneficiario (o lo recoge, en las recogidas) y el negocio lleva la caja. Por ahora solo La Habana y una
sola sucursal.

| Actor | Qué hace | Dónde | En 1.0 |
| --- | --- | --- | --- |
| Admin | Configura tasas, comisiones y límites; gestiona usuarios y roles; ve informes y auditoría | Panel web | Sí |
| Ventas | Registra remesas en nombre del remitente, cobra, asigna repartidores, resuelve revisiones | Panel web y app móvil | Sí |
| Repartidor | Ve sus remesas, entrega o recoge, reporta incidencias, cambia moneda, lleva su caja; **sin conexión** | App móvil | Sí |
| Cliente | Cotiza, envía y sigue sus remesas por su cuenta | App móvil | No (Fase 2) |

## 3. Versionado

Versionado semántico (`MAYOR.MENOR.PARCHE`) con etiquetas de pre-release. La versión vive en `build.gradle`, se sube en
la rama `release/` o `hotfix/` de Git Flow y cada versión publicada lleva su tag en `main`, sin prefijo
(`1.0.0-rc.1`).

| Etapa | Formato | Significa | Qué entra |
| --- | --- | --- | --- |
| Beta | `1.0.0-beta.N` | Fase 1 en construcción; se prueba en local o staging, sin dinero real | Funcionalidad nueva, refactors y arreglos |
| Release candidate | `1.0.0-rc.N` | Fase 1 completa; candidata a producción | Solo arreglos y el refactor de la revisión de arquitectura |
| Estable | `1.0.0` | Primera remesa real en producción | — |
| Parche | `1.0.X` | Arreglo urgente en producción (rama `hotfix/`) | Solo el arreglo |
| Menor | `1.X.0` | Cada fase siguiente, con su propio ciclo beta → rc → estable | Funcionalidad nueva compatible con la API |
| Mayor | `X.0.0` | Cambio incompatible del contrato de la API (rutas, campos, códigos de error) | Solo con migración planificada para las apps |

Historial: `1.0.0-beta.1` (Fase 1 en construcción) → `1.0.0-rc.1` (Fase 1 completa, migraciones fusionadas en
`V1__baseline.sql`).

## 4. Fases

| Fase | Contenido | Hito de salida | Versión | Estado |
| --- | --- | --- | --- | --- |
| 0 · Fundamentos | Repositorio y CI · Auth y roles · Modelo de datos · Docker | API con login por rol | — | Hecha |
| 1 · MVP de remesas | Tasas y comisiones · Clientes y beneficiarios · Entregas y recogidas · Asignar y atrasar · Caja · App del repartidor sin conexión | Primera remesa real | `1.0.0` | RC |
| 2 · App Cliente | Cotizar y enviar · Pago en línea · Estado de la remesa · Notificaciones push · Historial | App en tiendas | `1.1.0` | — |
| 3 · Caja y control | Cierre de caja · Conciliación · Prueba de entrega con foto o firma · Verificar identidad · Devoluciones | Caja cuadrada | `1.2.0` | — |
| 4 · Consolidación | Informes y KPIs · Seguridad y carga · Lanzamiento público | — | `1.3.0` | — |

El orden de las fases 2 y 3 está por confirmar. Ninguna fase empieza sin pasar el hito de la anterior.

## 5. Versión 1.0

### Qué incluye

Cada módulo tiene su documento en [docs/modules](docs/modules/README.md); lo acordado con el frontend, en
[docs/contracts](docs/contracts).

| Área | Qué hace | Módulo |
| --- | --- | --- |
| Acceso | Login con JWT y refresh token de un solo uso; sesión de jornada completa en el móvil; roles y permisos editables; límite de intentos fallidos por cuenta y por IP | `identity` |
| Configuración | Monto mínimo, días de entrega, tolerancia de tasa; por corredor (USD-CUP, USD-USD), tasas y comisiones de entrega y de recogida, con historial | `configuration`, `rates` |
| Clientes y beneficiarios | Alta, búsqueda, vínculos N:M, borrado lógico y restauración; beneficiarios sin cliente que se vinculan al enviarles una remesa; importar y exportar ([contrato](docs/contracts/customer-transfer.md)) | `customers`, `beneficiaries` |
| Remesas | Entregas y recogidas, estados y transiciones como datos, PIN, asignar, atrasar con motivo, incidencias, revisiones; tasa y comisión propias en cada recogida ([contrato](docs/contracts/pickup-rate.md)) | `remittances` |
| Caja | Libro de doble entrada: caja del negocio y de cada repartidor, fondeo y devoluciones, cambio de moneda del repartidor ([contrato](docs/contracts/courier-exchange.md)) | `payments` |
| Sin conexión | Descarga incremental y cola de acciones sincronizada en lote, idempotente ([contrato](docs/contracts/offline-sync.md)) | `sync` |
| Avisos | Feed de remesas completadas para las notificaciones locales de Ventas ([contrato](docs/contracts/completed-feed.md)) | `remittances` |
| Informes y auditoría | Informe mensual de ventas ([contrato](docs/contracts/monthly-report.md)); registro de quién hizo qué | `reporting` |

### Qué queda fuera, y por qué

| Tema | Decisión para 1.0 | Por qué |
| --- | --- | --- |
| Asignación por zona | Ventas asigna a mano desde la lista de repartidores; el módulo `dispatch` queda vacío | Una sola ciudad y pocos repartidores |
| Sucursales | Una sola; sin gestión de sucursales | La caja del negocio es única |
| Vencimiento de la cotización | No hay | Ventas cotiza y registra en el momento; la remesa guarda la tasa y la comisión con que se cobró. Vuelve con la App Cliente |
| Límites de monto | Solo un mínimo global en USD, configurable | Los acumulados por cliente esperan a la revisión legal |
| Notificaciones push | No hay Firebase: la app genera notificaciones locales al consultar al servidor | Sin coste ni dependencia externa; el módulo `notifications` queda vacío |
| Comprobantes de venta | Los genera el frontend con los datos de la remesa | No necesitan numeración ni almacenamiento en el servidor |
| Archivos (fotos, firmas) | No hay almacenamiento de archivos | La prueba de entrega con foto o firma es de la Fase 3 |
| Mapas y geolocalización | Fuera del roadmap | Futuro sin fase asignada |

## 6. Reglas del negocio decididas en 1.0

**Dinero y tasas**

- El negocio trabaja en **USD**: todo se cobra en USD. Si hay que entregar en CUP, el repartidor cambia los USD durante
  el viaje a la tasa que consigue y lo registra; un cambio que se aleja más del 20 % (configurable) de la tasa
  configurada se guarda igual pero queda marcado para revisión.
- Las tasas se expresan siempre como unidades de la otra moneda por 1 USD. El monto en CUP se redondea hacia abajo a
  múltiplos de 50.
- Valores concretos (monto mínimo, tasas, comisiones) se configuran por la API, nunca en una migración.

**Recogidas**

- Son variables y casi siempre las registra el admin, el único que conoce sus condiciones: indica la tasa y la
  comisión de cada una (si no, las configuradas). Solo quien ve las cifras financieras ve la comisión y la tasa.
- El repartidor solo **recoge el monto**; en una recogida **no se entrega nada** y la tasa es solo informativa.
- La comisión la paga a veces el cliente (fuera del país) y a veces el beneficiario (va incluida en lo que se recoge).
  Nunca pasa por la caja del repartidor ni sale en su resumen: solo cuenta como ganancia en el informe.

**Clientes y beneficiarios**

- Un cliente ya existe solo si coinciden **nombre, teléfono y dirección**. La misma persona con otra dirección es otro
  cliente, a propósito: no hay modelo de varias direcciones.
- Un beneficiario ya existe si coinciden nombre, teléfono, calle, número y municipio. Puede no tener cliente: el negocio
  tiene la lista de beneficiarios pero no la de sus remitentes, y se vinculan al registrar una remesa.

**Remesas**

- Estados configurables en la base: **Pagada → Asignada → Entregada**. Una remesa está atrasada si pasó su fecha prevista
  (2 días tras el registro, configurable); se puede atrasar con motivo. Sin cancelaciones.
- El repartidor puede entregar con o sin PIN.

**Informe mensual**

- Cuenta por **fecha de registro**, en hora de Cuba, en USD. Las comisiones de las recogidas también son ganancia. La
  diferencia es lo cobrado menos lo entregado convertido a USD con la tasa de cada remesa. El PDF lo genera el frontend.

**Permisos delicados**

- Exportar e importar clientes es solo para ADMIN y queda en la auditoría. La auditoría nunca guarda secretos.

### App del repartidor sin conexión (offline-first)

Hay días en que el repartidor pasa la jornada entera sin internet. La app guarda en el teléfono sus remesas y su caja,
registra las acciones en una cola local y las sincroniza cuando hay red.

| # | Decisión | Detalle |
| --- | --- | --- |
| 1 | Sincronización en lote | Un endpoint recibe la cola completa, aplica las acciones en orden y devuelve el resultado de cada una; una rechazada no frena las siguientes |
| 2 | Sesión de una jornada | El token del móvil dura 14 h (configurable aparte del resto del personal) |
| 3 | Margen al reusar el refresh token | Con mala red, repetir un refresh en los 60 s siguientes devuelve el mismo par nuevo en vez de cerrar las sesiones |
| 4 | Acciones idempotentes | Cada acción lleva un id generado en el teléfono; si llega dos veces, se aplica una sola vez |
| 5 | Hora real de la acción | Se usa la hora del teléfono (`occurredAt`) en la remesa, el historial y la caja; no puede ser futura, anterior a la remesa ni de hace más de unos días |
| 6 | Se acepta lo ya hecho | Una entrega sincronizada tras una reasignación se acepta si ese repartidor la tenía a la hora de la acción, y queda marcada para revisión |
| 7 | PIN sin conexión | El servidor comprueba el PIN al sincronizar; si no coincide, la entrega queda marcada para revisión. El PIN nunca viaja al teléfono |
| 8 | Descarga incremental | La app pide solo lo que cambió, incluidas las remesas que dejaron de ser suyas |

## 7. Arquitectura

**Monolito modular** en Spring Boot (Spring Modulith), no microservicios: un solo despliegue es más barato de
construir, probar y operar con un equipo pequeño, y los módulos bien separados permiten extraer un servicio más
adelante si hiciera falta. Los módulos solo se hablan por interfaces públicas o eventos, nunca leyendo las tablas de
otro; un test lo comprueba.

```
Panel web (Flutter Web) ─┐
                         ├──► API REST /api/v1 (Spring Boot, monolito modular) ──► PostgreSQL
App móvil (Flutter) ─────┘
```

| Módulo | Responsabilidad | Fase |
| --- | --- | --- |
| `identity` | Login, tokens, usuarios del personal, roles y permisos | 0 |
| `branches` | Provincias y municipios (zonas) | 0–1 |
| `customers` | Remitentes | 1 |
| `beneficiaries` | Beneficiarios, vínculos con clientes, importar y exportar | 1 |
| `configuration` | Ajustes, corredores, tasas y comisiones | 1 |
| `rates` | Cotizador | 1 |
| `remittances` | Remesas, estados, historial, incidencias, revisiones, feed de completadas | 1 |
| `payments` | Caja del negocio y de los repartidores, cambios de moneda | 1 y 3 |
| `sync` | Sincronización de la app sin conexión | 1 |
| `reporting` | Auditoría e informes | 1 y 4 |
| `dispatch` | Asignación automática por zona (vacío en 1.0) | Después de 1.0 |
| `notifications` | Push, correo, SMS (vacío en 1.0) | 2 |

| Tema | Elegido | Motivo |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3, Spring Modulith, Spring Security, Spring Data JPA | Estándar maduro |
| Base de datos | PostgreSQL 16 con Flyway | Transacciones fiables para dinero y estados |
| Autenticación | JWT de corta duración y refresh token opaco; permisos dinámicos por rol | Igual en web y móvil |
| Errores | ProblemDetail con un `code` estable; textos en inglés que el frontend traduce por código | Contrato estable entre apps |
| Pruebas | JUnit 5 y Testcontainers con PostgreSQL real | Probar contra la base de verdad |
| Documentación API | springdoc-openapi (Swagger, solo fuera de producción) | Contrato compartido con Flutter |
| Despliegue | Docker; CI con GitHub Actions; hosting en Railway | Entregas repetibles; recursos ligeros |
| Observabilidad | Spring Actuator y logs estructurados (ECS) en producción | Detectar fallos de entrega y caja |
| Frontend | Panel web y app móvil en Flutter, con menús por rol | Mismo código para Ventas y repartidores |

Todo el sistema maneja dinero: montos con `BigDecimal`, operaciones idempotentes y auditoría de los cambios.

## 8. Después de 1.0

Ideas ya habladas, sin compromiso de fecha; se ordenan al planificar cada fase.

- Asignación automática por zona (`dispatch`) y varias sucursales con su propia caja.
- App Cliente con cotización con vencimiento, pago en línea y notificaciones push (Fase 2).
- Cierre de caja diario, conciliación, prueba de entrega con foto o firma, devoluciones (Fase 3).
- Límites acumulados por cliente, según la revisión legal.
- Informes adicionales (por día, por repartidor) y KPIs (Fase 4).

## 9. Riesgos

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| Requisitos legales de remesas (licencias, conocer al cliente, antilavado) | Puede bloquear la salida a producción | Consultar con un asesor legal antes de `1.0.0` |
| Efectivo en manos de los repartidores | Pérdidas o descuadres | Caja por repartidor con doble entrada; entregas y cambios dudosos marcados para revisión; cierre diario en la Fase 3 |
| Conectividad débil de los repartidores | Entregas sin confirmar | App sin conexión con cola de sincronización (en 1.0) |
| Alcance que crece dentro de una fase | Retrasos en cadena | Respetar el "No incluye" de cada fase; en una RC solo arreglos |
| Permisos mal aplicados entre roles | Fugas de datos | Tests de permisos por rol; permisos propios para datos personales |

## 10. Plantilla de fase

Copia este bloque una vez por fase y rellénalo antes de empezarla; se cierra solo cuando se cumplen sus criterios de
salida.

```markdown
# Fase N — <Nombre>

Objetivo: <una frase con el resultado que se podrá usar al terminar>
Fechas: <inicio> → <fin>      Responsable: <nombre>

## Alcance
- Incluye: <funcionalidades, por actor>
- No incluye: <lo que queda para fases siguientes>

## Historias por actor
| Actor | Historia (Como… quiero… para…) | Prioridad |
| --- | --- | --- |

## Entregables
- Backend: <endpoints, módulos, migraciones>
- Flutter: <pantallas por app>
- Infraestructura: <entornos, pipelines>

## Dependencias y riesgos
| Riesgo o dependencia | Impacto | Plan |
| --- | --- | --- |

## Criterios de salida
- [ ] Flujo principal probado de punta a punta en staging
- [ ] Pruebas automáticas en verde
- [ ] Contratos con el frontend en docs/contracts
- [ ] Demo validada por <quien decide>
```

Definición de terminado para cada historia:

- [ ] Pruebas de integración, incluidos los permisos por rol
- [ ] Endpoint documentado en Swagger y en el README
- [ ] Documento del módulo actualizado
- [ ] Frontend avisado del cambio
