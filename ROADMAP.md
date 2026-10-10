# App de Remesas — Arquitectura y Roadmap

> Fuente: doc "App de Remesas — Arquitectura y Roadmap" (https://claude.ai/code/artifact/f5b0a1ca-40c2-463d-a531-e2276fc2cce2), copiado el 2026-10-01.

## Resumen del proyecto

La plataforma gestiona el envío de remesas de punta a punta para cuatro actores, con backend en Java + Spring Boot y frontend en Flutter. Se construye por fases: primero el flujo mínimo (registrar remesa → cobrar → asignar repartidor → entregar al beneficiario → confirmar) y después lo que lo hace escalable.

| Actor | Qué hace en el sistema | Dónde lo usa |
| --- | --- | --- |
| Admin | Configura tasas de cambio, comisiones, límites, sucursales y zonas; gestiona usuarios y roles; ve reportes y auditoría | Panel web (Flutter Web) |
| Ventas | Registra remesas en nombre del remitente, cobra, emite comprobantes, cierra caja | App móvil |
| Delivery | Ve sus remesas asignadas, entrega el dinero al beneficiario, confirma con firma o código, reporta incidencias, rinde el efectivo | App móvil |
| Cliente | Cotiza, envía remesas, guarda beneficiarios, sigue el estado, recibe notificaciones, consulta su historial | App móvil |

Supuestos: Ventas es personal interno (agentes o sucursales). Delivery entrega la remesa en efectivo en el domicilio del beneficiario o, en las remesas de recogida, recoge USD del beneficiario. Por ahora solo La Habana.

## Arquitectura propuesta

Recomendación: un **monolito modular en Spring Boot**, no microservicios. Con un equipo pequeño y un producto que aún está definiéndose, un solo despliegue es más barato de construir, probar y operar; los módulos bien separados permiten extraer un servicio más adelante (por ejemplo, Pagos y caja) si la carga o la regulación lo piden.

```
App Cliente (Android/iOS) ─┐
App Repartidor (Android) ──┼──► API REST (Spring Boot, monolito modular, 9 módulos) ──► PostgreSQL
Panel de Operaciones (Web) ┘                                                        └─► S3 (archivos), FCM/SMS/correo
```

Las tres apps usan la misma API; el rol del token decide qué puede hacer cada usuario.

| Opción | A favor | En contra |
| --- | --- | --- |
| Monolito modular (recomendada) | Un despliegue, transacciones simples, rápido de arrancar | Escala como un todo; exige disciplina entre módulos |
| Microservicios | Escala y despliegue por servicio | Red, colas, trazas y despliegues múltiples desde el día uno |

## Módulos del backend

Cada módulo es un paquete de Spring con su propio dominio, servicios, repositorios y controladores REST. Los módulos solo se hablan por interfaces públicas o eventos, nunca accediendo a las tablas de otro.

| Módulo | Responsabilidad | Actores que lo usan | Fase |
| --- | --- | --- | --- |
| Identidad y acceso | Login, JWT, refresh tokens, roles y permisos | Todos | 0 |
| Usuarios y sucursales | Alta de empleados, clientes, sucursales y zonas de entrega | Admin, Ventas | 0–1 |
| Tasas y comisiones | Tipo de cambio vigente, comisión por monto y zona, límites; cotizador | Admin, Ventas, Cliente | 1 |
| Beneficiarios | Datos y documento del beneficiario, dirección de entrega, beneficiarios frecuentes | Ventas, Cliente | 1 |
| Remesas | Crear remesa, código de seguimiento, máquina de estados, historial de eventos | Ventas, Cliente, Delivery | 1 |
| Asignación y entregas | Asignar remesas a repartidores por zona, prueba de entrega (firma, código, foto), incidencias | Admin, Delivery | 1 |
| Pagos y caja | Cobro al remitente, efectivo entregado a cada repartidor, cierre de caja, conciliación | Ventas, Admin, Delivery | 1 y 3 |
| Notificaciones | Push (Firebase Cloud Messaging), correo, SMS ante cambios de estado | Cliente, Delivery | 2 |
| Reportes y auditoría | Indicadores, exportes, registro de quién hizo qué | Admin | 4 |

Estados de una remesa (configurables en la base de datos): **Pagada → Asignada → Entregada**. Una remesa está atrasada si no se ha entregado y pasó su fecha prevista (2 días tras el pago); se puede atrasar con motivo. Sin cancelaciones por ahora.

## Estructura del frontend en Flutter

Recomendación: tres apps que comparten un paquete común, dentro de un solo repositorio (monorepo con Melos). Así el cliente no descarga pantallas de empleados y cada app se publica a su ritmo.

| App | Plataforma | Rol(es) | Por qué separada |
| --- | --- | --- | --- |
| App Cliente | Android, iOS | Cliente | Es la app pública de tiendas; debe ser ligera y cuidar la marca |
| App Repartidor | Android (iOS opcional) | Delivery | Necesita cámara, modo sin conexión y sincronización |
| Panel de Operaciones | Flutter Web | Admin, Ventas | Pantallas con tablas y formularios largos; el menú cambia según el rol |
| `core` (paquete compartido) | — | — | Modelos, cliente HTTP, autenticación, tema visual, validaciones, traducciones |

Estructura interna sugerida para cada app: carpetas por funcionalidad (`features/beneficiarios`, `features/remesas`…), cada una con capas `data` (repositorios, DTO), `domain` (entidades, casos de uso) y `presentation` (pantallas, estado).

- Gestión de estado: Riverpod o Bloc; elegir uno y usarlo en las tres apps.
- Navegación: go_router, con guardas por rol.
- Contrato con el backend: OpenAPI generado por springdoc, y cliente Dart generado a partir de él.

Alternativa más simple: una sola app con menús por rol. Sirve si el equipo es de 1–2 personas, a costa de una app más pesada y permisos más difíciles de auditar.

## Decisiones técnicas

Propuesta inicial; cada fila es una decisión que conviene confirmar en la Fase 0.

| Tema | Propuesta | Motivo |
| --- | --- | --- |
| Backend | Spring Boot 3, Java 21, Spring Web, Spring Data JPA, Spring Security | Estándar maduro, buena documentación |
| Base de datos | PostgreSQL + migraciones con Flyway | Transacciones fiables para dinero y estados |
| Autenticación | JWT con refresh token; roles ADMIN, VENTAS, DELIVERY, CLIENTE | Funciona igual en web y móvil |
| Archivos | Almacenamiento de objetos (S3 o compatible) para firmas, fotos de entrega y comprobantes | No guardar binarios en la base de datos |
| Notificaciones | Firebase Cloud Messaging; correo y SMS vía proveedor externo | Push nativo en Android e iOS |
| Tareas en segundo plano | Eventos internos de Spring y tareas programadas | Notificar y recalcular sin bloquear peticiones |
| Documentación API | springdoc-openapi (Swagger) | Contrato compartido con Flutter |
| Pruebas | JUnit 5, Testcontainers (backend); tests de widgets e integración (Flutter) | Probar contra PostgreSQL real |
| Despliegue | Docker; CI/CD con GitHub Actions; entornos dev, staging y producción | Entregas repetibles desde la Fase 0 |
| Observabilidad | Spring Actuator, logs estructurados, Sentry o similar | Detectar fallos de entrega y pagos |

Todo el sistema maneja dinero: montos con `BigDecimal`, operaciones idempotentes (una clave por solicitud) y auditoría de cada cambio desde la Fase 1.

## Roadmap por fases

Cinco fases: la Fase 1 ya entrega valor real (remesas registradas por Ventas y entregadas por Delivery) y cada fase siguiente suma un actor o una capacidad. Ninguna fase empieza sin pasar el hito de control de la anterior.

| Fase | Contenido | Hito de salida |
| --- | --- | --- |
| **0 · Fundamentos** | Repos y CI/CD · Auth y roles · Modelo de datos · Entornos y Docker | **Base lista** — API con login por rol |
| **1 · MVP de remesas** | Tasas y comisiones · Clientes y beneficiarios · Entregas y recogidas · Asignar y atrasar · Flujo de efectivo | **Primera remesa real** — flujo de punta a punta |
| **2 · App Cliente** | Cotizar y enviar · Pago en línea · Estado de la remesa · Notificaciones push · Historial | **Clientes autónomos** — app en tiendas |
| **3 · Caja y control** | Cierre de caja · Conciliación · Prueba de entrega con foto o firma · Verificar identidad · Devoluciones | **Caja cuadrada** — conciliación correcta |
| **4 · Consolidación** | Reportes y KPIs · Auditoría · Seguridad y carga · Lanzamiento | — |

| Fase | Objetivo | Actores que reciben valor | Duración estimada |
| --- | --- | --- | --- |
| 0 · Fundamentos | Esqueleto del sistema desplegable, con seguridad por rol | Equipo de desarrollo | Por definir |
| 1 · MVP de remesas | Registrar remesas pagadas y recogidas, asignarlas, atrasarlas y entregarlas; llevar el efectivo del negocio y de cada mensajero | Admin, Ventas, Delivery | En curso |
| 2 · App Cliente | El remitente cotiza, paga y sigue sus remesas sin pasar por Ventas | Cliente | Por definir |
| 3 · Caja y control | Cerrar y conciliar la caja, probar la entrega con foto o firma, verificar identidad, gestionar devoluciones | Admin, Ventas, Delivery | Por definir |
| 4 · Consolidación | Medir, auditar, reforzar seguridad y lanzar al público | Admin, todos | Por definir |

Fuera del roadmap (futuro): todo lo relacionado con mapas — ubicación del repartidor en tiempo real, rutas optimizadas y seguimiento de la entrega sobre un mapa. Mientras tanto, la asignación se hace por zona o municipio, sin geolocalización.

## Versionado

Versionado semántico (`MAYOR.MENOR.PARCHE`) con etiquetas de pre-release. La versión vive en `build.gradle`, se sube en
la rama `release/` o `hotfix/` de Git Flow y cada versión publicada lleva su tag en `main`, sin prefijo
(`1.0.0-beta.1`). Detalles del flujo en [STANDARDS.md](STANDARDS.md).

| Etapa | Formato | Significa | Qué entra |
| --- | --- | --- | --- |
| Beta | `1.0.0-beta.N` | Fase 1 en construcción; se prueba en local o staging, sin dinero real | Funcionalidad nueva, refactors y arreglos |
| Release candidate | `1.0.0-rc.N` | Fase 1 completa según su hito; candidata a producción | Solo arreglos. Revisión de arquitectura y refactor antes de la primera RC |
| Estable | `1.0.0` | Primera remesa real en producción (hito de la Fase 1) | — |
| Parche | `1.0.X` | Arreglo urgente en producción (rama `hotfix/`) | Solo el arreglo |
| Menor | `1.X.0` | Cada fase siguiente, con su propio ciclo beta → rc → estable | Funcionalidad nueva compatible con la API |
| Mayor | `X.0.0` | Cambio incompatible del contrato de la API (rutas, campos, códigos de error) | Solo con migración planificada para las apps |

Correspondencia prevista con las fases:

| Versión | Fase |
| --- | --- |
| `1.0.0` | 1 · MVP de remesas |
| `1.1.0` | 2 · App Cliente (o 3, según el orden que se confirme) |
| `1.2.0` | 3 · Caja y control |
| `1.3.0` | 4 · Consolidación y lanzamiento público |

**Versión actual: `1.0.0-beta.1`** (Fase 1 en curso).

### Alcance de la versión 1.0

Decisiones tomadas para llegar antes a la primera remesa real; lo que queda fuera vuelve a evaluarse después de `1.0.0`.

| Tema | Decisión para 1.0 | Por qué |
| --- | --- | --- |
| Asignación por zona | Fuera de 1.0. Ventas asigna a mano desde la lista de repartidores (`GET /couriers`) | Una sola ciudad y pocos repartidores; el módulo `dispatch` queda para después |
| Sucursales | Una sola sucursal en 1.0; no hay gestión de sucursales | La caja del negocio es única; las cajas por sucursal se añaden cuando haya más de una |
| Vencimiento de la cotización | Fuera de 1.0 | El cliente no usa el sistema en 1.0: Ventas cotiza y registra en el momento, y la remesa ya cobrada guarda la tasa y la comisión con que se registró. Se retoma con la App Cliente (Fase 2) |
| Límites de monto | Solo un monto mínimo global en USD, configurable | Mantener la aplicación simple; los acumulados por cliente esperan a la revisión legal |

## Plantilla de fase

Copia este bloque una vez por fase y rellénalo antes de empezarla; se cierra solo cuando se cumplen sus criterios de salida.

```markdown
# Fase N — <Nombre>

Objetivo: <una frase con el resultado que se podrá usar al terminar>
Fechas: <inicio> → <fin>      Responsable: <nombre>

## Alcance
- Incluye: <funcionalidades, por actor>
- No incluye: <lo que queda para fases siguientes>

## Historias por actor
| Actor | Historia (Como… quiero… para…) | Prioridad | Estimación |
| --- | --- | --- | --- |
| Admin | | | |
| Ventas | | | |
| Delivery | | | |
| Cliente | | | |

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
- [ ] Documentación de API actualizada
- [ ] Demo validada por <quien decide>
```

Definición de terminado para cada historia, en todas las fases:

- [ ] Código revisado por otra persona
- [ ] Pruebas unitarias y de integración
- [ ] Endpoint documentado en Swagger
- [ ] Permisos por rol verificados
- [ ] Desplegado en staging

## Riesgos y decisiones pendientes

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| Requisitos legales de remesas (licencias, conocer al cliente, antilavado) | Puede bloquear la salida a producción del MVP | Consultar con un asesor legal durante la Fase 0 |
| Efectivo en manos de los repartidores | Pérdidas o descuadres | Límite de efectivo por ruta, entrega con firma o código, cierre diario por repartidor |
| Cambios del tipo de cambio entre cotización y pago | Pérdida en la comisión | Cotización con vencimiento y tasa guardada en cada remesa |
| Conectividad débil de los repartidores | Entregas sin confirmar | Modo sin conexión con cola de sincronización en la App Repartidor |
| Alcance que crece dentro de una fase | Retrasos en cadena | Respetar el "No incluye" de la plantilla |
| Permisos mal aplicados entre roles | Fugas de datos | Pruebas de seguridad por rol en la definición de terminado |

## Próximos pasos

- [ ] Confirmar el orden de las fases 2 y 3 (¿App Cliente o Caja y control primero?)
- [ ] Confirmar tres apps Flutter o una sola
- [ ] Definir tamaño del equipo y fechas de la Fase 0
- [x] Modelo de datos inicial (remesa, cliente, beneficiario, usuario, tasas, caja)
- [x] Repositorio (github.com/jjhurtado/ottos) y pipeline de CI
