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
de todo lo externo: `RepositorioPedidos`, `PasarelaDePagos`, `CotizadorEnvio`,
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
