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
`EnviadorDeCorreo`, `Reloj`. La transacción se abre aquí.

**infrastructure** — Las implementaciones de esos puertos. Entidades JPA
**separadas** de las del dominio, con mapeador explícito. Cliente de Wompi.
Adaptador de Cloud Storage. Migraciones. Configuración de seguridad.

**presentation** — Controladores REST, DTO de entrada y salida, Bean Validation,
`@RestControllerAdvice`. Un DTO nunca es una entidad de dominio.

`presentation` no depende de `infrastructure`. Si un controlador necesita algo de
infraestructura, falta un caso de uso.

## Reglas concretas

- **Dinero:** `BigDecimal` de escala 0 dentro del objeto de valor `Dinero` con
  moneda `COP`. El peso colombiano no se fracciona. En base de datos
  `numeric(14,2)` por seguridad, pero el dominio redondea a entero, una sola vez,
  al final, con `HALF_UP`. Prohibido `double` y `float`.
- **Los precios almacenados y mostrados incluyen IVA.** Cada producto guarda su
  `tasa_iva` para poder desglosar al facturar.
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
