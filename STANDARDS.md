# Estándares del proyecto

Cómo trabajamos en Ottos: el flujo de Git y la arquitectura del backend. Toda contribución, de personas o de agentes,
sigue este documento. Si algo no encaja, se cambia aquí primero y después en el código.

## 1. Git

### Flujo de ramas: Git Flow (configuración de GitKraken)

Usamos Git Flow tal como lo inicializa GitKraken (Preferences → Gitflow), con esta configuración guardada en el repo
(`git config --get-regexp gitflow`):

| Rama o prefijo | Valor | Para qué |
| --- | --- | --- |
| Rama de producción | `main` | Solo versiones publicadas; cada merge a `main` lleva un tag de versión |
| Rama de desarrollo | `develop` | Integración del trabajo terminado; de aquí salen las releases |
| Features | `feature/` | Funcionalidad o refactor nuevo; sale de `develop` y vuelve a `develop` |
| Releases | `release/` | Preparar una versión (subir número, últimos arreglos); sale de `develop`, se fusiona en `main` y en `develop` |
| Hotfixes | `hotfix/` | Arreglo urgente de producción; sale de `main`, se fusiona en `main` y en `develop` |
| Prefijo de tag | *(vacío)* | Los tags son la versión tal cual: `1.0.0-beta.1`, `1.0.0` |

**`main` solo recibe ramas `release/` y `hotfix/`**, cada una con su tag (regla desde 2026-10-11, al existir
producción). Cada entorno despliega una rama:

| Rama | Entorno | Qué llega |
| --- | --- | --- |
| `develop` | Staging (despliegue automático tras el CI) | Cada feature o arreglo al fusionarse |
| `main` | Producción (despliegue automático tras el CI) | Solo versiones: una `release/` probada en staging o un `hotfix/` |

Así, todo lo que llega a producción pasó antes por staging. Las features y los arreglos nunca se llevan a `main`
directamente. Hasta esa fecha, durante las betas, `develop` y `main` iban juntas (fast-forward tras cada feature).

Reglas:

- Nunca se hace commit directo en `main` ni en `develop`: todo entra por una rama `feature/`, `release/` o `hotfix/`.
- Una rama, un propósito. Si aparece otro tema, va en otra rama.
- Antes de fusionar: build y tests en verde (`./gradlew build`) y la rama actualizada con su rama base.
- Al cerrar una release o un hotfix se crea el tag de la versión en `main` (GitKraken lo hace al terminar el flujo).
- Las ramas terminadas se borran.

### Nombres de rama

`<prefijo>/<descripcion-corta>`, en minúsculas y con guiones; si hay ticket, va delante de la descripción.

- `feature/couriers-endpoint`, `feature/ottos-42-quote-expiry`
- `release/1.0.0-beta.2`, `release/1.0.0`
- `hotfix/1.0.1`, `hotfix/pin-check-timeout`

Sin espacios, mayúsculas, guiones bajos ni nombres de personas.

### Mensajes de commit

> **Pendiente de confirmar.** El estándar de referencia es el artículo
> [Git Branch Naming and Commit Best Practices Cheatsheet](https://medium.com/@mandolkarmakarand94/git-branch-naming-and-commit-best-practices-cheatsheet-875316b9ca20),
> que no se pudo leer desde el entorno de Claude (Medium lo bloquea). Lo de abajo es la convención habitual de ese tipo
> de guías (Conventional Commits); hay que contrastarlo con el artículo y ajustar lo que difiera.

```
<tipo>(<ámbito opcional>): <resumen en imperativo, minúsculas, sin punto, ≤ 72 caracteres>

<cuerpo opcional: qué cambia y por qué, líneas de ≤ 72 caracteres>

<pie opcional: BREAKING CHANGE: …, Refs: #42, Co-Authored-By: …>
```

| Tipo | Uso |
| --- | --- |
| `feat` | Funcionalidad nueva |
| `fix` | Corrección de un error |
| `refactor` | Cambio de código que no altera el comportamiento |
| `perf` | Mejora de rendimiento |
| `test` | Tests nuevos o corregidos |
| `docs` | Solo documentación |
| `style` | Formato, sin cambio de lógica |
| `build` | Gradle, dependencias, Docker |
| `ci` | Pipelines |
| `chore` | Mantenimiento que no encaja en lo anterior |

El ámbito es el módulo afectado: `identity`, `customers`, `beneficiaries`, `configuration`, `rates`, `remittances`, `payments`,
`branches`… Ejemplos:

- `feat(remittances): list assignable couriers`
- `fix(rates): reject amounts that round down to nothing`
- `refactor: split services into interface and implementation`
- `docs: add project standards`

Un commit, un cambio lógico; el build debe pasar en cada commit. Los mensajes se escriben en inglés, como el código.

### Versionado

Ver [ROADMAP.md › Versionado](ROADMAP.md#3-versionado). La versión vive en `build.gradle` (`version = …`) y se sube en la
rama `release/` o `hotfix/` correspondiente.

### Changelog

`CHANGELOG.md` sigue el formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). Está escrito para quien usa
el backend (el frontend, operaciones, quien revisa una versión), no repite los commits: cuenta qué cambia para ellos.

Categorías, en este orden y solo las que tengan entradas:

| Categoría | Qué va |
| --- | --- |
| `Añadido` | Funcionalidad o endpoint nuevo |
| `Cambiado` | Cambio en algo existente (comportamiento, respuesta, refactor visible) |
| `Obsoleto` | Lo que se va a quitar en una versión próxima |
| `Eliminado` | Lo que se quitó |
| `Corregido` | Errores arreglados |
| `Seguridad` | Arreglos o cambios de seguridad |

Cada entrada es una línea en español que empieza por lo afectado (endpoint, módulo, regla), dice qué cambia y, si
rompe algo, empieza por **Incompatible:** y explica qué hay que hacer. Ejemplo:

```markdown
### Añadido
- `GET /api/v1/couriers`: lista los repartidores asignables con sus remesas abiertas (permiso `remittances:assign`).

### Corregido
- `POST /api/v1/remittances`: un monto que se redondea a 0 CUP devuelve 400 `AMOUNT_TOO_SMALL` en vez de registrarse.
```

**Al fusionar una `feature/` en `develop`:** la propia rama, antes del merge, añade sus entradas bajo `## [Unreleased]`
en la categoría que corresponda. Si la feature no cambia nada visible para quien usa el backend (tests, documentación
interna, refactor sin efecto), no añade entrada. Quien revisa el merge comprueba que la entrada existe y se entiende.

**Al fusionar `develop` en `main` (rama `release/`):** en la rama `release/<versión>`:

1. Se renombra `## [Unreleased]` a `## [<versión>] - <AAAA-MM-DD>` (fecha del merge a `main`) y se revisan y
   ordenan las entradas acumuladas.
2. Se añade encima un `## [Unreleased]` nuevo y vacío.
3. Se sube la versión en `build.gradle` a la misma `<versión>`.
4. Al terminar la release, `main` recibe el tag `<versión>` y `develop` recibe el merge de vuelta.

**Al fusionar un `hotfix/` en `main`:** el hotfix añade directamente su sección `## [<versión de parche>] - <fecha>`
(por ejemplo `1.0.1`) con sus entradas en `Corregido` o `Seguridad`, debajo de `[Unreleased]`; al volver a `develop`
la sección queda también allí.

Nunca se editan las secciones de versiones ya publicadas.

El changelog se escribe desde la primera versión en producción (`1.0.0-rc.1`, 2026-10-11), cuya sección resume todo lo
que contenía. Antes, durante las betas, se mantenía vacío.

## 2. Arquitectura del backend

### Monolito modular

Un único despliegue Spring Boot dividido en módulos (Spring Modulith). Cada paquete directo bajo `org.jobits.ottos` es un
módulo: `identity`, `branches`, `customers`, `beneficiaries`, `configuration`, `rates`, `remittances`, `dispatch`, `payments`,
`notifications`, `reporting`. `ModularityTest` falla si un módulo usa clases internas de otro.

Lo compartido por todos vive en el paquete raíz `org.jobits.ottos` y se mantiene mínimo: `ApiException` y `ApiErrors`
(errores), `AuditEvent` (auditoría), `Phones` y `Texts` (normalizar teléfonos y textos opcionales) y `TimeConfig`
(reloj y zona horaria del negocio). Antes de copiar un helper en un segundo módulo, va aquí.

### Capas y paquetes dentro de un módulo

```
org.jobits.ottos.<modulo>
├── <Algo>Service.java          interfaz pública del módulo, con sus vistas y comandos (lo único que otros módulos usan)
├── <Algo>ServiceImpl.java      su implementación (package-private)
├── <Valor>.java                enums y records de valor del contrato (RemittanceType, ClientType, MovementType…)
├── domain/                     entidades JPA y repositorios Spring Data
├── management/ | security/     servicios solo internos del módulo (interfaz + Impl), si hacen falta
└── web/                        controllers REST y sus DTO de petición
```

La API pública de un módulo es su paquete raíz: Spring Modulith solo deja usar desde fuera lo que está ahí. Por eso
la interfaz de servicio que usan otros módulos, sus tipos de contrato y los enums que aparecen en ellos van en la raíz,
y las piezas internas en la raíz son package-private (`Workflow`, `RemittanceViews`). No se usan interfaces con nombre
(`@NamedInterface`) para abrir subpaquetes.

El flujo de una petición siempre es:

```
Controller  →  Service (interfaz)  →  ServiceImpl  →  Repository  →  Base de datos
```

### Clases grandes

Un `Impl` hace una sola cosa. Si mezcla responsabilidades distintas (operaciones online, cola offline, cálculos de un
informe, consultas con filtros), cada una va en su clase dentro del módulo, package-private, como en `remittances`:
`RemittanceServiceImpl`, `RemittanceSyncServiceImpl`, `RemittanceOperations` (lo que comparten), `SalesSummaries`,
`RemittanceSpecifications`. Como referencia, más de ~400 líneas es señal de que hay que partir.

### Rendimiento

- Al montar una lista de vistas, los datos de otros módulos (nombres de clientes, de repartidores…) se piden en lote,
  una consulta por tipo (`CustomerService.findAll(ids)`, `StaffDirectoryService.names(ids)`), nunca uno por fila.
- El pool de conexiones de producción es pequeño (5): una petición no debería hacer decenas de consultas.

### Controllers (`web/`)

- Solo traducen HTTP: validan la entrada (`@Valid`, Bean Validation), comprueban el permiso (`@PreAuthorize`) y llaman a
  un servicio. Sin lógica de negocio, sin repositorios, sin transacciones, sin capturar excepciones de persistencia
  (la carrera de dos peticiones con la misma clave de idempotencia la resuelve el servicio).
- Dependen únicamente de **interfaces de servicio**, nunca de un `Impl`, un repositorio o un componente interno.
- Los DTO de petición son `record` anidados en el controller (`LoginRequest`, `AssignRequest`…).
- Permisos, nunca roles: `@PreAuthorize("hasAuthority('remittances:assign')")`.

### Servicios: interfaz + `Impl`

Todo `@Service` se compone de dos tipos en el mismo paquete:

| Tipo | Contenido | Visibilidad |
| --- | --- | --- |
| `XService` (interfaz) | Los métodos públicos con su javadoc, y los tipos que forman su contrato: vistas de respuesta y comandos como `record` anidados (`CustomerInfo`, `NewRemittance`, `PageView`…) | `public` |
| `XServiceImpl` (clase) | Toda la lógica: `@Service`, `@Transactional`, constructor con dependencias, métodos con `@Override`, helpers privados, listeners de eventos | package-private |

Reglas:

- Nombre: sustantivo del dominio + `Service`: `CustomerService`, `QuoteService`, `CashLedgerService`. Implementación:
  el mismo nombre + `Impl`.
- Quien usa un servicio inyecta la interfaz (inyección por constructor, campos `final`).
- `@Transactional` va en los métodos del `Impl`, no en la interfaz.
- El javadoc del contrato va en la interfaz; el `Impl` solo documenta detalles de implementación.
- Los métodos de la interfaz no reciben ni devuelven entidades JPA ni sus tipos anidados: reciben records de comando
  (`CustomerData`, `BeneficiaryData`, `NewRemittance`) y devuelven records de vista. Los controllers tampoco importan
  nada de `domain/`.
- Las piezas auxiliares que no son servicios (`@Component` como `Workflow`, `RemittanceViews`, handlers de seguridad,
  bootstrap) no llevan interfaz y no se usan desde controllers.

### Configuración

Todo valor que el negocio deba poder cambiar sin desplegar (montos mínimos, plazos, tasas, comisiones…) vive en el
módulo `configuration` y se gestiona desde un solo sitio (`GET /api/v1/configuration`). Los demás módulos lo leen a
través de `ConfigurationService`; nunca guardan sus propios ajustes. Un ajuste nuevo es: una fila en `settings` (por
migración), una constante en `Setting`, un método tipado en `ConfigurationService` y su campo en `SettingsView` y en
`PUT /configuration/settings`.

### Documentación de cada módulo

Cada módulo tiene su documento en `docs/modules/<módulo>.md`: qué hace, su API pública, endpoints y permisos, datos,
reglas de negocio, decisiones y dónde tocar para cambiar algo. Quien cambia un módulo actualiza su documento en la misma
rama, para que el contexto no se pierda. Un módulo nuevo empieza con su documento y una fila en
`docs/modules/README.md`.

### Comunicación entre módulos

- Solo a través de la interfaz de servicio pública del otro módulo (paquete raíz del módulo) o de eventos de dominio
  (`RemittanceEvents`, escuchados con `@EventListener`).
- Nunca se accede a repositorios, entidades o tablas de otro módulo.
- **Los eventos son síncronos y dentro de la transacción** de quien los publica, a propósito: completar una remesa y
  mover la caja del repartidor (`payments`), o cambiar un usuario y guardar su auditoría (`reporting`), tienen que
  confirmarse o deshacerse juntos. Por eso se usa `@EventListener` y no `@ApplicationModuleListener` (asíncrono,
  después del commit), que solo tendría sentido para efectos que pueden llegar tarde, como una notificación.
- Una regla de negocio que usan varios módulos se define una sola vez en el módulo dueño y los demás la consultan: qué
  es un repartidor (`StaffDirectoryService.couriers()` / `isCourier`), la moneda del negocio
  (`ConfigurationService.BUSINESS_CURRENCY`).

### Dominio y persistencia (`domain/`)

- Entidades JPA con constructor protegido para JPA, sin setters públicos: el estado cambia con métodos con nombre
  (`assignTo`, `complete`, `postpone`).
- El esquema solo cambia con migraciones Flyway (`V<n>__<descripcion>.sql`); `ddl-auto: validate`.
- **Un esquema por módulo**, con el nombre del módulo (`identity`, `branches`, `customers`, `beneficiaries`,
  `configuration`, `remittances`, `payments`, `reporting`, `apps`); `public` solo guarda el historial de Flyway
  (`SchemaLayoutTest`). Cada entidad declara su esquema (`@Table(name = …, schema = "<módulo>")`) y todo SQL escrito a
  mano (consultas nativas, migraciones, scripts de datos, tests) pone el esquema delante: `payments.cash_movements`.
  Las claves foráneas entre esquemas se mantienen: son las dependencias entre módulos.
- `V1__baseline.sql` es toda la estructura de la base. Se rehízo el 2026-10-13, ya con los esquemas por módulo, a
  partir de todas las migraciones estructurales hasta la 1.0.0-rc.3, y staging y producción se recrearon desde cero
  (aún sin clientes reales). **Desde entonces `V1` está congelada para siempre:** una migración aplicada no se edita
  nunca y cada cambio estructural va en una migración nueva (`V2__…`), que staging y producción aplican solos.
- **Ningún dato de un lugar, una moneda o un negocio concreto en la V1 ni en el código.** La V1 solo lleva la
  estructura y el catálogo que el código necesita (permisos, rol ADMIN, flujo de estados, ajustes neutros). Lo propio
  de cada instalación (moneda del negocio, regla de teléfonos, zonas, corredores, tasas y comisiones iniciales, roles
  Ventas y Repartidor) va en su script de alta, `scripts/data/seed/<instalación>.sql` (hoy `cuba-la-habana.sql`), que
  infraestructura ejecuta una vez tras crear cada entorno. La zona horaria es `OTTOS_TIMEZONE`, sin valor por defecto.
  El código lee esos valores de la configuración (`ConfigurationService.businessCurrency()`, `phoneMatchDigits()`,
  `currencies()`); no escribe "USD", "CUP" ni nada de un país.
- **Las migraciones son solo para cambios estructurales** (tablas, columnas, restricciones, índices y los permisos que
  el código necesita). Las operaciones sobre datos (registrar una versión de la app, corregir filas) van en un script
  de `scripts/data/`, idempotente, que infraestructura ejecuta en cada entorno con una copia antes (regla del usuario,
  2026-10-13).
- Dinero siempre en `BigDecimal`; las tasas y comisiones nunca se editan, se inserta una fila nueva.
- Concurrencia: `@Version` en entidades que se modifican desde varias peticiones.

### Errores

- Todo error de negocio se lanza como `ApiException.badRequest|conflict|notFound|forbidden|unauthorized("CODIGO", "detail en inglés")`.
- Los códigos (`AMOUNT_TOO_SMALL`, `REMITTANCE_NOT_FOUND`…) son contrato con el frontend: no se renombran; se añaden.
- El catálogo de códigos está en el README (sección *Errors*).

### Auditoría

- Todo cambio de datos de administración (usuarios, roles, y lo que se añada) publica un `org.jobits.ottos.AuditEvent`
  dentro de la transacción del cambio; `reporting` lo guarda en `audit_log` y lo sirve en `GET /audit`.
- `details` dice qué cambió (antes/después, añadido/quitado) y nunca lleva secretos.
- Las `action` son códigos estables, como los de error: no se renombran.

### Idioma y traducción

- El backend no traduce: todo texto que devuelve (`detail` de los errores, descripciones de permisos, nombres y
  descripciones de los roles semilla) está en inglés.
- El frontend traduce por **código**, nunca por el texto: códigos de error (`AMOUNT_BELOW_MINIMUM`), de rol (`ADMIN`,
  `SALES`, `DELIVERY`), de permiso (`remittances:assign`), de motivo de incidencia (`NOT_HOME`) y de estado de remesa
  (`PAID`, `ASSIGNED`, `DELIVERED`). Si llega un código sin traducción, muestra el texto del backend como respaldo.
- Los estados de remesa se traducen por código **y tipo**: `DELIVERED` es "Entregada" en una entrega (`DELIVERY`) y
  "Recogida" en una recogida (`PICKUP`). Excepción a la regla del inglés: los nombres de estado (`statusName`) están
  guardados en español en `remittance_statuses`; son solo el respaldo para estados personalizados sin traducción.
- Los datos que crean los usuarios (roles personalizados, nombres de clientes, notas…) se muestran tal cual.
- Por eso los códigos son contrato: no se renombran. Al añadir un código nuevo (error, permiso o rol semilla) se avisa
  al frontend para que añada su traducción.
- No se crean migraciones para traducir textos a un idioma.

### Seguridad

- RBAC dinámico: los permisos se crean solo por migración y se conceden a ADMIN en la misma migración;
  `PermissionCatalogTest` lo verifica.

### Tests

- Tests de API con `ApiTestSupport`: MockMvc contra un **PostgreSQL real** (16, el mismo que `docker-compose`) que
  Testcontainers arranca en Docker, con las mismas migraciones. No se usan bases en memoria: el SQL, las migraciones y
  la concurrencia se prueban como en producción. Hace falta Docker para ejecutar los tests.
- Lo que dependa de peticiones simultáneas (idempotencia, bloqueos, saldos) lleva un test en `ConcurrencyTest` que las
  lance a la vez de verdad.
- Cada funcionalidad o corrección lleva su test; los tests existentes no se cambian para que pase un cambio de
  comportamiento sin acordarlo.
- `./gradlew build` en verde antes de fusionar.

### Revisión

Esta arquitectura se revisó en la primera RC (`1.0.0-rc.1`, 2026-10-11): API de los módulos en su paquete raíz, sin
entidades en los contratos, una responsabilidad por clase, reglas compartidas definidas una vez y listas sin consultas
N+1. Se vuelve a revisar antes de cada versión menor.
