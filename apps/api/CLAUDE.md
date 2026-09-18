# apps/api — reglas

Java 21 · Spring Boot 4.1.0 · Gradle multi-módulo (Kotlin DSL) · PostgreSQL 16 ·
Flyway · springdoc-openapi.

Lee `docs/01-arquitectura.md` antes de crear un módulo o mover una clase.

## Qué va en cada capa

**domain** — Entidades y agregados con su comportamiento, objetos de valor
(`Dinero`, `Sku`, `Imei`, `CorreoElectronico`), excepciones de negocio,
invariantes. Sin anotaciones, sin `@Entity`, sin Lombok. Las reglas viven aquí:
un `Pedido` no se confirma sin líneas, un `Inventario` no baja de cero.

**application** — Un caso de uso, una clase, un método público:
`ConfirmarPedido.ejecutar(ConfirmarPedidoComando)`. Aquí se declara el **puerto**
de todo lo externo: `RepositorioPedidos`, `PasarelaDePagos`,
`RecaudoContraentrega`, `EmisorFacturaElectronica`, `AlmacenDeImagenes`,
`EnviadorDeCorreo`, `Reloj`.

**La transacción NO se abre aquí, y esta línea decía que sí.** La abre quien
llama, con un `TransactionTemplate` en el controlador —`PedidoControlador` para
crear el pedido, `AdminPedidosControlador` para las acciones del panel—, porque
`application` es framework-free y `@Transactional` es Spring. Corregido el 18 de
septiembre de 2026, cuando una revisión adversarial cruzó esta frase con el
javadoc de `CrearPedido`, que decía lo contrario.

Con una excepción, y tiene nombre: `EnTransaccionPropia`. La usa la emisión de la
guía, que escribe una fila, llama a un tercero **que cobra**, y escribe otra vez
(`ADR-0033`). Ahí la atomicidad ya no existe —ninguna transacción de base de datos
revierte un cobro de Skydropx—, así que agrupar las dos escrituras solo consigue que
la primera no esté confirmada cuando el dinero se va. Si un caso de uso quiere esto
sin tener un tercero cobrando en la mitad, lo que quiere es otra cosa.

**infrastructure** — Las implementaciones de esos puertos. Entidades JPA
**separadas** de las del dominio, con mapeador explícito. Cliente de Wompi.
Adaptador de Cloud Storage. Migraciones. Configuración de seguridad.

**presentation** — Controladores REST, DTO de entrada y salida,
`@RestControllerAdvice`. Un DTO nunca es una entidad de dominio.
**Ojo: aquí no hay Bean Validation.** Este documento decía que sí y era falso —no
hay proveedor en el classpath ni un solo `@NotNull` en la capa—, así que lo que
valida un DTO es su propio constructor compacto. Comprobado el 18 de septiembre
de 2026.

**Y quitar un `@NotNull` cambia el contrato publicado, aunque no valide nada.**
springdoc deduce de él qué propiedades marca como `required` en el OpenAPI, así
que al quitarlo el campo pasó a opcional y el cliente TypeScript generado dejó de
exigirlo en tiempo de compilación — mientras el servidor seguía rechazando con 422
el cuerpo que lo omitía. Lo atrapó el trabajo de contratos de la CI, que compara el
OpenAPI vivo contra `packages/contratos/src/tipos.ts`. Un campo obligatorio de tipo
referencia necesita, entonces, **dos cosas distintas**: el `Objects.requireNonNull`
del constructor compacto, que es quien de verdad protege, y un
`@Schema(requiredMode = REQUIRED)`, que no valida nada y solo hace que el contrato
diga lo que el servidor exige.

`presentation` no depende de `infrastructure`. Si un controlador necesita algo de
infraestructura, falta un caso de uso.

## Reglas concretas

- **Dinero:** `BigDecimal` de escala 0 dentro del objeto de valor `Dinero` con
  moneda `COP`. El peso colombiano no se fracciona. En base de datos
  `numeric(14,2)` por seguridad, pero el dominio redondea a entero, una sola vez,
  al final, con `HALF_UP`. Prohibido `double` y `float`.
- **Los precios almacenados y mostrados son el valor final, y hoy no llevan IVA
  dentro.** El negocio es **no responsable** del impuesto sobre las ventas
  (parágrafo 3 del art. 437 del Estatuto Tributario), así que `tasa_iva` vale
  `0.00` en todas las variantes y `AgregarVariante` rechaza cualquier otra cosa
  mientras `NEGOCIO_RESPONSABLE_IVA` siga en `false` — adicionar IVA al precio sin
  ser responsable obliga a cumplir íntegramente el régimen de los responsables
  (Decreto 1625 de 2016, art. 1.3.1.15.2, literal a). La columna se queda porque la
  calidad de no responsable se pierde al cruzar los topes del parágrafo 3. Ver
  `adr/0041`.
- **Fechas:** `Instant` en persistencia y en la API, UTC, ISO-8601.
  `America/Bogota` solo al formatear para el usuario. Nunca `Date`.
- **Identificadores:** UUID v7 generado en el dominio, no por la base. Los
  pedidos llevan además uno legible: `TS-2026-000123`.
- **Nada de `@Autowired` en campos.** Constructor, siempre.
- **Excepciones:** de negocio en `domain`, traducidas a HTTP en `presentation`.
  Ninguna excepción de JPA sale de `infrastructure`.
- **Idempotencia obligatoria** en todo lo que mueva dinero o inventario: crear
  pedido, crear intento de pago, recibir webhook. Llave persistida 24 horas.
- **Bloqueo pesimista** al reservar inventario. Dos compradores por la última
  unidad es un caso real, no teórico.
- **Nada de lógica de negocio en un servicio de infraestructura.** Si aparece un
  cálculo ahí, va al dominio.

## Pruebas

- `domain`: unitarias puras, sin Spring. Son la mayoría.
- `application`: caso de uso con puertos falsos escritos a mano. Sin Mockito
  cuando un doble de diez líneas hace el trabajo.
- `infrastructure`: Testcontainers con PostgreSQL real.
- `presentation`: `@WebMvcTest` con la capa de aplicación simulada.
- `bootstrap`: ArchUnit y las pruebas de extremo a extremo.

Detalle en `docs/06-testing.md`.

## Notas de Spring Boot 4.1 / Testcontainers 2.x

Versiones muy recientes. Esto ya costó varias vueltas de diagnóstico en Fase 1
(`infrastructure` y `presentation`) — que quede escrito para no repetirlo.
Antes de agregar una dependencia nueva en este backend, asume que su versión
"estable" puede no conocer Boot 4.1 todavía y verifica contra el POM real en
`~/.gradle/caches` o Maven Central, no de memoria (regla dura #9).

- **`spring-boot-autoconfigure` se partió en módulos por función.** Tener
  `flyway-database-postgresql` en el classpath ya no basta: hace falta
  `spring-boot-starter-flyway` explícito, o Flyway nunca migra y Hibernate
  falla validando contra un esquema vacío, en silencio (sin ningún log de
  Flyway).
- **`@EnableJpaRepositories` y `@EntityScan` no siguen `scanBasePackages`.**
  Cada uno por defecto solo mira el paquete de la clase
  `@SpringBootApplication` (`co.tecnosport.api.bootstrap`), nunca
  `infrastructure`. Se declaran aparte, en una `@Configuration` que vive en
  el módulo dueño de las entidades (`infrastructure/ConfiguracionJpa.java`),
  no en `bootstrap` — así `bootstrap` no necesita saber que existe JPA.
  `@EntityScan` además cambió de paquete:
  `org.springframework.boot.persistence.autoconfigure`.
- **`application` es framework-free a propósito** (sin `@Component` en sus
  casos de uso), así que alguien tiene que registrar cada caso de uso como
  bean. Eso es trabajo de `bootstrap` (`bootstrap/.../ConfiguracionCatalogo.java`
  es el ejemplo): una `@Configuration` con un `@Bean` por caso de uso,
  inyectando el puerto que ya resolvió el `@ComponentScan`.
- **`@WebMvcTest` se movió** a
  `org.springframework.boot.webmvc.test.autoconfigure`.
- **`HttpStatus.UNPROCESSABLE_ENTITY` quedó obsoleto**: usar
  `UNPROCESSABLE_CONTENT` (IANA renombró el estado 422 a "Unprocessable
  Content"). Lo mismo aplica al método equivalente en `StatusResultMatchers`
  de las pruebas (`isUnprocessableContent()`).
- **Falta el flag `-parameters` de javac por defecto en Gradle.** Sin él,
  Spring MVC no resuelve el nombre de un `@PathVariable`/`@RequestParam` por
  reflexión salvo que se anote explícito (`@PathVariable("slug")`). Ya está
  puesto en el `build.gradle.kts` raíz, para todos los módulos — no hace
  falta repetirlo ni anotar cada parámetro a mano.
- **Testcontainers 2.x renombró los artefactos de Gradle** con el prefijo
  `testcontainers-` (`org.testcontainers:testcontainers-postgresql`, no
  `org.testcontainers:postgresql`; igual con `testcontainers-junit-jupiter`)
  y movió `PostgreSQLContainer` a `org.testcontainers.postgresql`, sin
  parámetro de tipo genérico.
- **El BOM de `spring-boot-dependencies` importa `testcontainers-bom` de
  forma anidada** y el plugin `io.spring.dependency-management` no sigue esa
  cadena. Se importa aparte en el `build.gradle.kts` raíz
  (`mavenBom("org.testcontainers:testcontainers-bom:2.0.5")`).
- **`springdoc-openapi-starter-webmvc-ui:2.8.6`** (la última en Maven
  Central; no hay todavía una línea propia para Boot 4.1) sí funciona:
  `/api/docs` y `/api/openapi.json` responden bien contra `bootRun` real.
- **Un método de repositorio con `@Lock` no envuelve su propia transacción.**
  A diferencia de un `findBy...` normal, Spring Data no le da una
  transacción implícita a una consulta con bloqueo pesimista — con razón:
  un lock que se suelta apenas termina esa única consulta no serviría para
  nada. Llamarlo fuera de una transacción explícita revienta en seco con
  `jakarta.persistence.TransactionRequiredException: No active transaction`.
  Encontrado en Fase 2 en `bootRun` real (no en las pruebas, que ya
  envolvían todo en `TransactionTemplate`): `SembradorInventario` llamaba
  `RepositorioInventario.buscarPorVarianteId` (con `@Lock`) a pelo. Quien
  llame a un puerto con bloqueo pesimista —caso de uso o sembrador— tiene
  que abrir la transacción él mismo, con `@Transactional` o
  `TransactionTemplate`.
- **`@Modifying(clearAutomatically = true)` sin `flushAutomatically = true`
  puede descartar en silencio, sin ninguna excepción, escrituras de otras
  entidades hechas antes en la misma transacción.** `clearAutomatically`
  limpia el contexto de persistencia después de correr el `UPDATE` masivo,
  pero no vuelca antes los cambios pendientes de otros `guardar()` — si esos
  cambios seguían solo en memoria (nunca forzados a flush), `clear()` los
  descarta sin avisar nada: la transacción igual confirma. Encontrado en
  Fase 4 en `bootRun` real (no en las pruebas de aplicación, que usan dobles
  de prueba y no reproducen el flush/clear real de Hibernate — sí lo atrapa
  una prueba de infraestructura contra Postgres real, con los tres
  repositorios JPA de verdad en la misma transacción):
  `ConfirmarRecuperacion` guardaba el token consumido y la clave nueva del
  usuario, y luego llamaba `RepositorioSesiones.revocarTodasDeUsuario`
  (`@Modifying(clearAutomatically = true)`) — las dos escrituras anteriores
  se perdían. Cualquier `@Modifying` con `clearAutomatically = true` que se
  llame después de un `guardar()` en la misma transacción necesita también
  `flushAutomatically = true`.
- **Jackson 3 (`tools.jackson.*`) es el stack JSON por defecto de Boot
  4.1.0, no `com.fasterxml.jackson.*`.** Un import del paquete viejo (por
  ejemplo `com.fasterxml.jackson.databind.JsonNode` en vez de
  `tools.jackson.databind.JsonNode`) compila sin avisar nada raro, pero
  revienta en tiempo de ejecución con `HttpMessageConversionException` al
  deserializar — encontrado en Fase 3 en el controlador del webhook de
  Wompi, sin ninguna pista del porqué en el mensaje de error.
- **Jackson 3 no rellena los componentes que falten de un `record`.** Un cuerpo
  JSON al que le falta un campo declarado en el record no cae en el valor por
  omisión del tipo (`false` para un `boolean`, `0` para un `int`): revienta la
  deserialización entera y la petición muere en 422 con
  `HTTP_MESSAGE_NOT_READABLE`. Encontrado en Fase 6 al añadir `autorizaDatos` a
  `RegistrarUsuarioRequest` dando por hecho lo contrario. No es un problema
  —el resultado es el seguro— pero **no se puede razonar sobre "el primitivo
  protege por omisión"**: si un campo tiene que ser opcional, hay que declararlo
  como envoltorio (`Boolean`) y decidir el valor a mano.
  **Matizado el 18 de septiembre de 2026, y el matiz importa porque invierte la
  conclusión para la mitad de los casos: eso vale para un primitivo.** Un
  componente de **tipo referencia** que falte —un enum, un `String`, un
  `Boolean`— **no revienta nada: llega en nulo**. Medido mandando un cuerpo sin
  `modalidadRecaudo` a `POST /admin/pedidos/{id}/recaudo`: la petición pasó de
  largo y murió más adelante por otra razón. O sea que "el record protege por
  omisión" es falso justo donde más se usa, y un DTO con un campo obligatorio de
  tipo referencia necesita su propia guarda —un `Objects.requireNonNull` en el
  constructor compacto, que Jackson envuelve y sale como 422
  `HTTP_MESSAGE_NOT_READABLE`— porque **aquí no hay Bean Validation**: no hay
  proveedor en el classpath (lo dice `OptionalValidatorFactoryBean` al arrancar),
  así que un `@NotNull` no haría nada.
- **`@AuthenticationPrincipal` solo se resuelve cuando `@EnableWebSecurity`
  está activo en el contexto** (lo registra `WebMvcSecurityConfiguration`,
  que `@EnableWebSecurity` importa). Como eso vive en `bootstrap`
  (`ConfiguracionSeguridad`) y `presentation` nunca depende de `bootstrap`
  —ni en producción ni en sus pruebas `@WebMvcTest`—, un controlador de
  `presentation` no puede usar esa anotación para el actor autenticado: se
  lee directo de `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`,
  el mismo `UUID` que `FiltroAutenticacionJwt` ya deja puesto ahí. Encontrado
  en Fase 3 al construir los endpoints admin de pedidos.
- **`UserDetailsServiceAutoConfiguration` vive en
  `org.springframework.boot.security.autoconfigure`**, no en
  `org.springframework.boot.autoconfigure.security.servlet` como en
  versiones anteriores de Boot — `spring-boot-security` quedó como módulo
  separado de `spring-boot-autoconfigure`.
- **`HttpServletResponse.getWriter()` escribe en ISO-8859-1 por defecto**
  (spec de servlets, no algo específico de Boot 4.1) si nadie fija el
  charset antes de escribir — un filtro que arma su propio cuerpo a mano
  (fuera del `@RestControllerAdvice`, porque corre antes del
  `DispatcherServlet`) corrompe cualquier tilde de la regla dura #4 en
  silencio, sin ninguna excepción. Encontrado en Fase 4 en
  `FiltroLimiteIntentos`. Llamar siempre
  `response.setCharacterEncoding("UTF-8")` antes de `getWriter()` en
  cualquier filtro que escriba JSON a mano.
- **Después de que un `flush` falle, la sesión de Hibernate no sirve para nada
  más.** Cualquier consulta sobre ella vuelve a reventar, así que un `catch
  (DataIntegrityViolationException)` que intente averiguar *cuál* fila chocó
  —para dar un error decente en vez de un 500— falla en el `catch`. Encontrado
  el 17 de septiembre de 2026 traduciendo la violación del índice único de
  `emision_de_guia`: dos pruebas de Testcontainers fallaban con la excepción
  correcta lanzada desde el sitio equivocado. La forma que funciona es al revés:
  **consultar antes** (que además atrapa el caso normal) y, en el `catch`, traducir
  con lo que ya se tenga en la mano, sin tocar la base. La carrera de verdad —dos
  peticiones que leen "no hay ninguna" a la vez— no necesita saber cuál ganó: lo
  que importa es que la segunda no entró.
