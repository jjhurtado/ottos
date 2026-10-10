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

**Hasta la versión `1.0.0`, `develop` y `main` van juntas:** cada feature terminada se fusiona en `develop` y, a
continuación, `develop` se lleva a `main` (fast-forward), de modo que las dos ramas apuntan siempre al mismo commit.
Desde `1.0.0`, `main` solo recibe ramas `release/` y `hotfix/` como se describe abajo.

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

Ver [ROADMAP.md › Versionado](ROADMAP.md#versionado). La versión vive en `build.gradle` (`version = …`) y se sube en la
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

Mientras no exista la versión `1.0.0`, `CHANGELOG.md` se mantiene vacío (solo `## [Unreleased]`); las reglas de arriba
empiezan a aplicarse con el trabajo que vaya a `1.0.0`.

## 2. Arquitectura del backend

### Monolito modular

Un único despliegue Spring Boot dividido en módulos (Spring Modulith). Cada paquete directo bajo `org.jobits.ottos` es un
módulo: `identity`, `branches`, `customers`, `beneficiaries`, `configuration`, `rates`, `remittances`, `dispatch`, `payments`,
`notifications`, `reporting`. `ModularityTest` falla si un módulo usa clases internas de otro.

Lo compartido por todos vive en el paquete raíz `org.jobits.ottos` y se mantiene mínimo: `ApiException`, `ApiErrors`,
`Phones`, `TimeConfig`.

### Capas y paquetes dentro de un módulo

```
org.jobits.ottos.<modulo>
├── <Algo>Service.java          interfaz pública del módulo (lo único que otros módulos pueden usar)
├── <Algo>ServiceImpl.java      su implementación (package-private)
├── domain/                     entidades JPA y repositorios Spring Data
├── application/ | management/ | security/
│                               servicios internos del módulo: interfaz + Impl
└── web/                        controllers REST y sus DTO de petición
```

El flujo de una petición siempre es:

```
Controller  →  Service (interfaz)  →  ServiceImpl  →  Repository  →  Base de datos
```

### Controllers (`web/`)

- Solo traducen HTTP: validan la entrada (`@Valid`, Bean Validation), comprueban el permiso (`@PreAuthorize`) y llaman a
  un servicio. Sin lógica de negocio, sin repositorios, sin transacciones.
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
- Los métodos de la interfaz no exponen entidades JPA salvo que sea inevitable dentro del propio módulo; hacia fuera se
  devuelven `record` de vista.
- Las piezas auxiliares que no son servicios (`@Component` como `Workflow`, `RemittanceViews`, handlers de seguridad,
  bootstrap) no llevan interfaz y no se usan desde controllers.

### Configuración

Todo valor que el negocio deba poder cambiar sin desplegar (montos mínimos, plazos, tasas, comisiones…) vive en el
módulo `configuration` y se gestiona desde un solo sitio (`GET /api/v1/configuration`). Los demás módulos lo leen a
través de `ConfigurationService`; nunca guardan sus propios ajustes. Un ajuste nuevo es: una fila en `settings` (por
migración), una constante en `Setting`, un método tipado en `ConfigurationService` y su campo en `SettingsView` y en
`PUT /configuration/settings`.

### Comunicación entre módulos

- Solo a través de la interfaz de servicio pública del otro módulo (paquete raíz del módulo) o de eventos de dominio
  (`RemittanceEvents`, escuchados con `@EventListener`).
- Nunca se accede a repositorios, entidades o tablas de otro módulo.

### Dominio y persistencia (`domain/`)

- Entidades JPA con constructor protegido para JPA, sin setters públicos: el estado cambia con métodos con nombre
  (`assignTo`, `complete`, `postpone`).
- El esquema solo cambia con migraciones Flyway (`V<n>__<descripcion>.sql`); `ddl-auto: validate`.
- Dinero siempre en `BigDecimal`; las tasas y comisiones nunca se editan, se inserta una fila nueva.
- Concurrencia: `@Version` en entidades que se modifican desde varias peticiones.

### Errores

- Todo error de negocio se lanza como `ApiException.badRequest|conflict|notFound|forbidden|unauthorized("CODIGO", "detail en inglés")`.
- Los códigos (`AMOUNT_TOO_SMALL`, `REMITTANCE_NOT_FOUND`…) son contrato con el frontend: no se renombran; se añaden.
- El catálogo de códigos está en el README (sección *Errors*).

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

- Tests de API con `ApiTestSupport` (MockMvc + H2 en modo PostgreSQL con las mismas migraciones).
- Cada funcionalidad o corrección lleva su test; los tests existentes no se cambian para que pase un cambio de
  comportamiento sin acordarlo.
- `./gradlew build` en verde antes de fusionar.

### Revisión

Esta arquitectura se revisa de nuevo al llegar a la primera RC (`1.0.0-rc.1`), antes de seguir refactorizando.
