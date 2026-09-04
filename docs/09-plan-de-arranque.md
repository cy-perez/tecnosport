# Plan de arranque y prompts

El orden importa. Cada fase termina con algo que funciona y está probado, no con
una capa a medias. No pases a la siguiente sin cerrar la anterior.

Antes de nada: `git init`, primer commit con `docs/`, los `CLAUDE.md` y los
`README.md`. La documentación se versiona antes que el código.

## Fase 0. Monorepo y esqueleto

Objetivo: los dos proyectos compilan, corren, se prueban y sirven una página en
blanco. Sin lógica de negocio.

```
Lee CLAUDE.md y docs/01-arquitectura.md.

Crea la base del monorepo: package.json raíz con workspaces para apps/web,
packages/marca y packages/contratos; .gitignore; .env.example con las variables
de docs/07-infra-gcp.md; docker-compose.yml con PostgreSQL 16, Mailpit y Adminer;
y los scripts npm de la tabla del README raíz.

No crees todavía el contenido de apps/api ni de apps/web. Solo el andamiaje del
monorepo.
```

Backend, en una conversación aparte:

```
Lee CLAUDE.md, docs/01-arquitectura.md y apps/api/CLAUDE.md.

Crea el esqueleto del backend en apps/api: proyecto Gradle multi-módulo con
Kotlin DSL y los cinco módulos domain, application, infrastructure, presentation
y bootstrap, con las dependencias entre ellos exactamente como dice la tabla del
documento de arquitectura.

Incluye: Spring Boot 4.1.0 con Java 21, un endpoint /api/v1/salud que responda
200, la prueba de ArchUnit que verifica las flechas de dependencia, Spotless y
Flyway con una migración vacía.

Nada de JPA, ni seguridad, ni nada más todavía.

Muéstrame el plan de archivos y espera mi aprobación antes de escribir. Si no
estás seguro de la sintaxis del plugin de Spring Boot 4.1 en Gradle, dímelo en
vez de suponerla.
```

Frontend, en otra conversación:

```
Lee CLAUDE.md, apps/web/CLAUDE.md y docs/04-ui-marca.md.

Crea el esqueleto del frontend en apps/web: Angular 22.5 con npm, SSR con
hidratación, zoneless, standalone, SCSS, Vitest configurado y corriendo,
Transloco con es y en, TanStack Query, Angular CDK y PWA.

Copia el kit de packages/marca a src/assets/marca como paso del build y enlázalo
en angular.json en el orden del documento de interfaz.

Deja una portada mínima que solo demuestre: el logo cambiando entre positivo y
negativo según el tema, el selector de idioma, el selector de tema con las tres
opciones, y un botón con la clase .chaflan con su anillo de foco visible.

Una prueba de Vitest que pase. Nada más.
```

Cierre de fase: ambos arrancan en Windows, `gradlew.bat build` y `npm test`
pasan, y `docker compose up -d` levanta la base.

## Fase 1. Catálogo de solo lectura

```
Lee docs/02-modelo-datos.md.

Implementa el catálogo de lectura, capa por capa, empezando por domain.

Dominio: Producto, Variante, Categoria, Marca, Atributo, ImagenProducto,
SetRotacion, y los objetos de valor Dinero y Sku, con sus invariantes y sus
pruebas unitarias. Incluye la regla de que un set de rotación incompleto no se
publica.

Después application con BuscarProductos y VerFichaDeProducto, y el puerto
RepositorioProductos.

Después infrastructure: entidades JPA separadas del dominio con su mapeador,
migración Flyway con el esquema del documento, y datos de siembra con productos
de las tres líneas y sus variantes según la tabla de variantes por categoría.

Al final presentation con los endpoints de docs/03-api.md y springdoc.

Un commit por capa. Para después de domain y muéstrame el resultado.
```

**Backend cerrado** (2026-09-02): `domain` (`ec73c94`), `application`
(`ac68640`), `infrastructure` (`ab747b8`), `presentation` (`22f199d`).
`gradlew.bat build` pasa completo (Testcontainers incluido) y `bootRun` sirve
`GET /api/v1/productos` y `GET /api/v1/productos/{slug}` contra PostgreSQL
real con datos de siembra (`docs/adr/0009`, `docs/adr/0010`). `GET
/api/v1/categorias` y `GET /api/v1/marcas` se agregaron después, como caso de
uso propio, para poblar los filtros de la vitrina.

**Vitrina cerrada** (2026-09-02): rejilla con paginación por cursor
(`32d5a86`), filtros (`667c5cc`, con `GET /api/v1/categorias` y
`GET /api/v1/marcas` en `b2043bd`), ficha de producto (`695fc14`), y galería
más selector de variante (`34b7cee`, con la corrección de `fix (api/catalogo):
excluir variantes inactivas`, `4b86c40`). El visor 360 todavía no — es la
Fase 5, a propósito.

**Fase 1 completa.** `/es/` y `/en/` navegan de la rejilla a la ficha de
cada producto sembrado, con filtros, imagen de galería y selector de
variante funcionando contra el backend real, verificado en SSR en los dos
idiomas.

## Fase 2. Carrito e inventario

Inventario por movimientos, reserva con bloqueo pesimista, vencimiento con
`Reloj` inyectado, carrito persistente reconciliado en el servidor. Aquí van las
pruebas de concurrencia: dos compradores por la última unidad.

**Backend cerrado** (2026-09-02): dominio de inventario por movimientos
(`ffb17eb`), dominio de carrito (`c3e40e5`), casos de uso y puerto de
inventario (`19532e3`), persistencia JPA de inventario con bloqueo pesimista
y prueba de concurrencia (`a81da62`, con la corrección de transacción
explícita en la siembra, `3a47e05`), persistencia JPA de carrito
(`ac8321b`), endpoints REST de carrito (`79bf114`), y la corrección de
exponer `id` de la variante en `VarianteRespuesta` (`057d533`) — necesaria
para que el cliente pueda referenciar una variante al agregarla al carrito.
`gradlew.bat build` pasa completo, incluida la prueba de concurrencia real
con dos hilos por la última unidad (`RepositorioInventarioJpaTest`).

**`RepositorioInventario` no tiene todavía ningún caso de uso que lo
consuma** — el puerto existe (dominio y JPA), pero nada reserva inventario
al agregar una línea al carrito. Es a propósito: `Carrito.java` documenta
que la reserva ocurre "al iniciar el pago" (`docs/00-producto.md`), fuera
de este agregado — queda para la Fase 3, cuando exista el caso de uso de
creación de pedido que sí necesita bloquear existencias.

**El carrito no vence todavía.** `docs/02-modelo-datos.md` dice que vive 30
días, pero no hay columna de expiración ni tarea programada que lo borre —
un carrito anónimo queda en la base indefinidamente. No bloquea la Fase 3;
queda como `TODO` para cuando haya un mecanismo de tareas programadas en el
backend (la reconciliación de transferencias de la Fase 3 va a necesitar
uno igual, buen momento para resolver los dos juntos).

**Vitrina cerrada** (2026-09-03, `b4ece08`): agregar al carrito desde la
ficha de producto, badge de cantidad en el encabezado, página `/carrito`
con edición de cantidad y eliminación de líneas. `CarritoStore` es un
servicio singleton (no una función de fábrica como las de catalogo) para
compartir una sola señal de `carritoId` entre encabezado, ficha y la
página del carrito — el único caso de la Fase 2 con ese patrón, documentado
en `apps/web/CLAUDE.md`. Es también la única vitrina que no sigue la regla
de "siempre precargar en el resolver" de `ADR-0011`: el carrito vive en
`localStorage`, anónimo, y el servidor no tiene forma de saber cuál es el
carrito de un visitante — el SSR de `/carrito` sirve siempre "carrito
vacío", verificado con `curl` en dos corridas.

**Fase 2 completa.** Un producto se agrega al carrito desde la ficha, el
badge del encabezado refleja la cantidad, `/es/carrito` permite cambiar
cantidades y eliminar líneas, y el carrito persiste entre visitas por el id
guardado en `localStorage` — todo verificado contra el backend real
(`bootRun` + PostgreSQL). Sin autenticación ni reserva de inventario
todavía: el carrito de la Fase 2 es una intención del cliente, no un
compromiso — eso llega con el pedido en la Fase 3.

## Fase 3. Checkout, envío y pago

El envío no se cotiza: es un costo estándar ya incluido en el precio publicado
de cada producto, igual en todo el país, según `adr/0012`. Creación de pedido
con revalidación de precios y existencias. Wompi con firma de integridad,
webhook firmado, idempotencia y conciliación programada. Transferencia manual.
Correos transaccionales.

**Creación de pedido e idempotencia en curso** (2026-09-03, todavía sin
commitear): dominio de `Pedido` (líneas congeladas, `EstadoPedido` con el grafo
de transiciones completo del diagrama de `02-modelo-datos.md`, `Direccion`,
`TipoEntrega`, `MetodoPago`, `HistorialPedido`, y el valor de objeto
`CorreoElectronico` en `domain/compartido`). Caso de uso `CrearPedido`: revalida
precio, nombre, SKU e imagen contra el catálogo real y reserva cada línea en
`Inventario` con bloqueo pesimista — la vigencia de la reserva depende del
método de pago (30 min pago en línea, 24 h transferencia, sin vencer
contraentrega), inyectada por constructor y configurable por
`MINUTOS_RESERVA_INVENTARIO`/`TRANSFERENCIA_HORAS_VENCIMIENTO` (ya estaban en
`docs/07-infra-gcp.md`, nunca se habían conectado). `RepositorioProductos`
ganó `buscarPorVarianteId`, necesario porque el carrito solo conoce el id de
variante, nunca el slug del producto.

Infraestructura y presentación cerradas también: `V4__pedido.sql`,
`RepositorioPedidosJpa` (sin transacción propia, comparte la de
`CrearPedido` — un pedido guardado sin su reserva de inventario, o viceversa,
no puede pasar), `POST /api/v1/pedidos` (el controlador abre esa transacción,
con `TransactionTemplate`), y dos códigos de error nuevos en
`ManejadorDeErrores` (`VARIANTE_NO_ENCONTRADA` 404, `EXISTENCIA_INSUFICIENTE`
409, el ejemplo textual de `docs/03-api.md`).

**Idempotencia por `Idempotency-Key`** (docs/03-api.md) resuelta como mecanismo
genérico, no atada a pedidos: puerto `RepositorioIdempotencia` en
`application/compartido` (técnico, no de negocio — mismo criterio que
`Reloj`), `V5__idempotencia.sql`, y `FiltroIdempotencia` (`OncePerRequestFilter`,
sin `@Component`, registrado a mano en `bootstrap` solo para
`/api/v1/pedidos`). Reclama la llave en su propia transacción *antes* de
ejecutar el caso de uso — si solo se cacheara la respuesta al final, un
proceso que muere después de comprometer el pedido pero antes de guardar la
respuesta dejaría la puerta abierta a que un reintento lo duplicara. Un 500
genuino libera la llave en vez de cachearla (desviación consciente del texto
literal de la doc: cachear un error de infraestructura por 24 horas dejaría a
un cliente que reintenta de buena fe sin salida). Verificado a mano contra
`bootRun` + PostgreSQL real, no solo con Testcontainers: dos peticiones con la
misma llave devuelven el mismo pedido, y un error de negocio (409) también se
repite en vez de reevaluarse. El mecanismo queda listo para
`POST /api/v1/pagos/intentos` cuando llegue Wompi — solo hace falta agregar
esa ruta a `ConfiguracionIdempotencia`.

**Número legible del pedido cerrado** (2026-09-03): `NumeroPedido`
(`TS-2026-000123`, `apps/api/CLAUDE.md`) en el dominio, recibido por quien
llame a `Pedido.crear` — el dominio no lo genera, porque el secuencial exige
una atomicidad que solo da la base de datos. `RepositorioPedidos` ganó
`siguienteNumero(anio)`; `CrearPedido` lo pide después de reservar todas las
líneas (para no quemar un número si la reserva falla), con el año calculado
en `America/Bogota`. `RepositorioPedidosJpa` lo resuelve con un único
`insert ... on conflict ... returning` (`V6__secuencia_pedido.sql`), sin
bloqueo pesimista explícito — verificado con una prueba de concurrencia real
(20 hilos, sin duplicados ni saltos) y a mano contra `bootRun` + PostgreSQL
real: dos pedidos consecutivos devolvieron `TS-2026-000001` y
`TS-2026-000002`, y repetir la `Idempotency-Key` del primero no quemó un
número nuevo.

**Wompi cerrado, sin la conciliación programada** (2026-09-03): Web Checkout
hospedado, no tokenización propia — decisión consciente para un solo
desarrollador: Wompi resuelve por su cuenta PSE, el push de Nequi, el 3-D
Secure de tarjeta y el crédito de Addi, a costa de que el cliente salga del
sitio unos segundos durante el pago (esa página no es nuestra, no rompe la
regla de "nada de píxel suelto"). Si el volumen lo justifica más adelante,
migrar a tokenización con Wompi.js queda localizado a `WompiClient` y al
puerto `PasarelaDePagos`.

Agregado `Pago` en el dominio (referencia, método, estado, eventos
recibidos), idempotente por `referencia` y por el `idEvento` de cada
`EventoPago`. Caso de uso `CrearIntentoDePago`: valida que el pedido esté en
`PAGO_PENDIENTE` y que su método de pago vaya por Wompi, numera la
referencia por intento (`TS-2026-000123-1`, `-2`, ... para el reintento tras
`PAGO_FALLIDO`), y pide a `PasarelaDePagos` la firma de integridad
(`SHA256(referencia + montoEnCentavos + moneda + secreto)`, documentada por
Wompi). `POST /api/v1/pagos/intentos` expone eso; el monto sale siempre de
`Pedido.total()`, nunca del cliente.

`POST /api/v1/pagos/webhook` (caso de uso `ProcesarEventoDePago`) verifica
la firma del evento antes de aplicar nada — `WompiClient.verificarFirmaEvento`
implementa el checksum documentado por Wompi (SHA256 de los valores de
`signature.properties`, en orden, más el timestamp y el secreto de eventos).
Wompi no manda un identificador de evento propio, así que el checksum hace
de `idEvento` para la idempotencia: un reintento exacto del webhook produce
el mismo checksum. Un evento aplicado transiciona el pedido
(`PAGO_PENDIENTE → PAGADO`/`PAGO_FALLIDO`); el webhook siempre responde 200,
incluso cuando el evento se descarta (firma inválida, referencia
desconocida), para no entrar en el ciclo de reintentos de Wompi por algo que
un reintento no puede arreglar. `VOIDED` (una transacción aprobada que luego
se anula) queda fuera a propósito: anulaciones y reembolsos son un caso de
negocio aparte, no contemplado en el grafo de `EstadoPago` de este alcance.

**Conciliación programada cerrada** (2026-09-03): la API de Wompi consulta
una transacción por su propio id (`GET /transactions/{id}`), no por la
referencia que genera este backend — verificado por búsqueda, no supuesto
(regla dura #9; un intento anterior de traer un vector de ejemplo de la
documentación resultó fabricado por la herramienta que la resumió, atrapado
calculando el SHA-256 aparte). Sin ese id no hay cómo consultar un pago que
nunca recibió webhook, así que `Pago` ganó `idTransaccionWompi`, registrado
por `PATCH /api/v1/pagos/intentos/{referencia}` cuando el frontend vuelve
del Web Checkout con el id en la URL de retorno. Un pago que nunca llega a
tener ese id (el cliente cerró la pestaña antes de volver, y tampoco llegó
el webhook) queda fuera del mecanismo, para seguimiento manual en el panel
— la lista de pedidos de la vista de operación mínima, más abajo.

`ConciliarPagosPendientes` revisa los `PENDIENTE` con id registrado y más
viejos que un umbral configurable (`WOMPI_CONCILIACION_ANTIGUEDAD_MINIMA_MINUTOS`),
consulta cada uno y aplica el resultado con la misma lógica que el webhook
(`AplicadorDeResultadoDePago`, compartida entre los dos para no duplicarla).
`TareaConciliacionWompi` (`@Scheduled`, primer uso de tareas programadas en
el proyecto — el vencimiento del carrito es candidato para el mismo
mecanismo más adelante) corre el lote entero en una sola transacción: un
fallo a mitad de camino se revierte completo en vez de dejar un `Pago`
actualizado sin su `Pedido`, y se reintenta solo, sin duplicar nada, en la
siguiente corrida — el mecanismo ya es idempotente por diseño.

Verificado con Testcontainers y `@WebMvcTest` en las cuatro capas, no
todavía a mano contra `bootRun` + Wompi real (falta llaves de sandbox).

**Reintento de pago fallido cerrado** (2026-09-03): `ReintentarPago`
regresa un pedido `PAGO_FALLIDO` a `PAGO_PENDIENTE` (`docs/02-modelo-datos.md`
ya contemplaba la transición) — `POST /api/v1/pedidos/{id}/reintentar-pago`,
público, mismo modelo de confianza que crear pedido/intento. `CrearIntentoDePago`
sigue sin aceptar `PAGO_FALLIDO` directo a propósito: el frontend llama
primero el reintento y después `POST /api/v1/pagos/intentos` de siempre, sin
duplicar esa lógica. Solo un pedido procesado por Wompi llega a
`PAGO_FALLIDO` (transferencia manual y contraentrega nunca pasan por
`AplicadorDeResultadoDePago`), así que no hizo falta revalidar el método de
pago aparte.

**Transferencia manual, lado cliente cerrado** (2026-09-03): la reserva de
24 horas ya existía desde `CrearPedido` (Fase 3, primer commit de esta
sección). Lo que faltaba era mostrar a dónde transferir: `POST
/api/v1/pedidos` devuelve `datosTransferencia` (banco, tipo de cuenta,
número, titular, y la referencia — el número legible del pedido, no una
referencia aparte que inventar) cuando `metodoPago == TRANSFERENCIA_MANUAL`.
Los datos de la cuenta salen de variables de entorno
(`TRANSFERENCIA_BANCO`, etc., docs/07-infra-gcp.md), con placeholders que
nunca sirven para transferir de verdad — dato de negocio real que no le
tocaba inventar a la sesión.

**Conciliar el comprobante cerrado** (2026-09-03), una vez existió el rol
`ADMIN` para protegerlo — ver la vista de operación mínima, más abajo.

**Disponibilidad de contraentrega cerrada** (2026-09-03): hasta ahora
`CrearPedido` aceptaba `CONTRAENTREGA` sin validar nada — hueco real frente a
la regla dura "el servidor no confía en el cliente para decidir métodos de
pago disponibles" (docs/03-api.md). `PoliticaContraentrega` (dominio, pura)
aplica las cuatro reglas de docs/11-pagos-y-envios.md: cobertura de ciudad
(`cobertura_contraentrega`, tabla propia con el código DANE como clave
primaria, cargada a mano por el administrador vía `POST`/`DELETE
/api/v1/admin/cobertura-contraentrega`, sin UI ni integración con la
transportadora todavía), monto máximo y categorías excluidas
(`CONTRAENTREGA_HABILITADA`/`MONTO_MAXIMO`/`CATEGORIAS_EXCLUIDAS`, arranca
deshabilitada por defecto), y rechazo previo del comprador por correo (sin
teléfono en el dominio todavía). `MetodosDePagoDisponibles` la aplica tanto
en `POST /api/v1/pedidos/metodos-de-pago-disponibles` (el checkout consulta
antes de mostrar las opciones) como dentro de `CrearPedido` — mismo código,
para que la respuesta de la consulta y la validación real nunca diverjan.

**Verificación y despacho cerrados** (2026-09-03): `VerificarContraentrega`
transiciona `CONFIRMADO_CONTRAENTREGA → EN_PREPARACION` con el motivo del
contacto (WhatsApp o llamada) que registra el administrador — sin campo
nuevo en `Pedido`, reutiliza `transicionar` (actor + motivo en el
historial). Como ese estado solo lo alcanza un pedido contraentrega, no
hace falta revalidar el método de pago aparte. `DespacharPedido` transiciona
`EN_PREPARACION → DESPACHADO` y crea el agregado `Envio` (transportadora,
guía, costo real — tabla propia por `docs/02-modelo-datos.md`, sin columnas
de recaudo todavía). La transición se aplica antes de crear el `Envio`: un
despacho que en realidad falla (pedido sin verificar, o ya despachado)
nunca deja un envío huérfano. `POST
/api/v1/admin/pedidos/{id}/verificar-contraentrega` y `POST
/api/v1/admin/pedidos/{id}/despacho`, protegidos por rol `ADMIN`. Sin
excepción nueva: `TransicionDeEstadoInvalidaException` ya cae en el 422
genérico.

**Hueco encontrado y cerrado al construir el rechazo en la entrega**
(2026-09-03): `LineaPedido` no guardaba el id de su movimiento `RESERVA` —
`CrearPedido.congelarLinea` lo descartaba. Sin ese id no hay forma segura de
liberar la reserva correcta: dos pedidos distintos pueden tener reservas
pendientes de la misma variante al mismo tiempo, no se puede adivinar por
variante y cantidad. Ahora `LineaPedido.idReserva` lo guarda (migración
`linea_pedido.id_reserva`, columna nueva).

**Pendiente real, todavía sin cerrar:** `Inventario.confirmar`/`liberar`
nunca se llaman para pago en línea — `AplicadorDeResultadoDePago` solo
transiciona el `Pedido`, no toca el inventario. Un pedido `PAGADO` no
convierte su reserva en salida real, y uno `PAGO_FALLIDO` no la libera; con
`ReintentarPago` (más abajo), la reserva original de 30 minutos también
podría haber vencido para cuando el cliente reintenta, sin que nada la
renueve. No estaba en el alcance pedido esta sesión — queda anotado para
la próxima vez que se toque el ciclo de vida del inventario en pago en
línea.

**Entrega y rechazo cerrados** (2026-09-03): `MarcarEntregado` transiciona
`DESPACHADO → ENTREGADO` y, si el pedido es `CONTRAENTREGA`, encadena
`ENTREGADO → RECAUDO_PENDIENTE` en la misma llamada (su dinero nunca entró
antes del despacho; un pedido pagado en línea se queda en `ENTREGADO`).
`RechazarEnEntrega` transiciona a `RECHAZADO_EN_ENTREGA` y libera cada línea
reservada vía `LineaPedido.idReserva`. `POST /api/v1/admin/pedidos/{id}/entrega`
y `POST /api/v1/admin/pedidos/{id}/rechazo-entrega`, protegidos por rol
`ADMIN`.

**Recaudo pendiente cerrado** (2026-09-03): `ConciliarRecaudo` transiciona
`RECAUDO_PENDIENTE → RECAUDO_CONCILIADO` y registra la comisión de la
transportadora en el `Envio` del despacho (`Envio.conciliarRecaudo`, costo
real separado del flete, docs/11-pagos-y-envios.md). `POST
/api/v1/admin/pedidos/{id}/recaudo`. La visibilidad ("un pedido entregado
hace veinte días sin conciliar es plata en la calle, tiene que ser
visible") es un filtro de estado en el listado ya existente —
`GET /api/v1/admin/pedidos?estado=RECAUDO_PENDIENTE` — en vez de un
endpoint aparte: reutiliza `ListarPedidosAdmin`/`PedidosPaginados`, y
ordena por más antiguo primero cuando hay filtro, en vez de por más
reciente (lo más urgente arriba).

**Contraentrega queda cerrada de punta a punta** (2026-09-03): disponibilidad,
verificación, despacho, entrega/rechazo, recaudo pendiente y su
conciliación. Es el tramo con más riesgo operativo de la fase, y se
construyó su propio ciclo de revisión, capa por capa, con pruebas contra
Postgres real en cada una — no solo dobles de prueba.

**Contraentrega y transferencia manual necesitaban una acción humana que
todavía no tenía dónde vivir:** marcar un contraentrega como verificado
antes de despachar, conciliar el comprobante de una transferencia, y ver
el recaudo pendiente. Antes de tocar contraentrega, se añadió lo mínimo
para eso:

- **Autenticación con rol `ADMIN` cerrada** (2026-09-03): login, refresco con
  rotación y detección de reutilización, cierre de sesión
  (`docs/08-seguridad-legal.md`), sin registro de cliente ni cuenta opcional —
  eso sigue siendo de la Fase 4. `Usuario` y `SesionRefresco` en el dominio
  (agregados separados: cada eslabón de la rotación del refresco es su propia
  fila, agrupada por `familiaId`). BCrypt costo 12, no Argon2id — evita agregar
  Bouncy Castle como dependencia nueva, permitido explícitamente por el
  documento de seguridad. JWT de acceso HS256 con el módulo JOSE de Spring
  Security (`NimbusJwtEncoder`/`NimbusJwtDecoder`), sin librería de JWT de
  terceros. `SembradorAdmin` crea el primer `ADMIN` desde `ADMIN_CORREO`/
  `ADMIN_CLAVE` si no existe ninguno, nunca lo actualiza — rotar la clave de un
  admin ya creado queda como mecanismo aparte, no construido. `/api/v1/admin/**`
  ya exige el rol en `SecurityFilterChain`, listo para cuando existan rutas ahí.
  Verificado a mano contra `bootRun` + PostgreSQL real (no solo con ArchUnit,
  que no levanta el contexto completo): los cinco recorridos de la lista de
  arriba, más que la semilla no duplica el admin en un segundo arranque.
- **Lista de pedidos y conciliar transferencia cerrados** (2026-09-03), sin
  diseño de marca todavía: `GET /api/v1/admin/pedidos` (paginado por página,
  no por cursor — distinción de docs/03-api.md) y `POST
  /api/v1/admin/pedidos/{id}/conciliar-transferencia`. Este último solo acepta
  pedidos `TRANSFERENCIA_MANUAL`: uno de Wompi nunca se marca pagado por una
  acción manual del panel, su verdad sigue siendo el webhook firmado o la
  conciliación programada. La guarda de transición de `Pedido` ya rechaza un
  segundo intento, así que no hizo falta `Idempotency-Key` aparte. El actor de
  auditoría (`"admin:" + usuarioId`) sale de `SecurityContextHolder` directo,
  no de `@AuthenticationPrincipal`: ese resolver solo se registra con
  `@EnableWebSecurity` activo, que `presentation` no importa.
- **Verificar, despachar, entregar, rechazar en la entrega y conciliar el
  recaudo cerrados** (2026-09-03) — ver el detalle más arriba. Contraentrega
  queda completa de punta a punta.

El resto del panel (productos, variantes, existencias, imágenes, cuenta de
cliente) sigue en la Fase 4. Esto es la vista de operación mínima para que el
dinero no quede colgado, no el panel completo.

Esta fase va despacio, con pruebas primero en todo lo que toca dinero, y con la
pasarela en pruebas hasta que los recorridos pasen.

## Fase 4. Cuentas y panel administrativo

Autenticación completa según `docs/08-seguridad-legal.md`: registro de cliente,
verificación de correo, recuperación de contraseña y la cuenta opcional que se
ofrece al final del checkout. El login de `ADMIN` ya existe desde la Fase 3;
aquí se completa con roles y autorización por recurso.

Panel completo: productos, variantes, existencias, imágenes con URL firmada.
La vista de operación de pedidos y conciliación de recaudo de la Fase 3 pasa a
tener aquí el diseño y los componentes definitivos.

## Fase 5. Sistema 360

Se hace al final a propósito: necesita el panel, la autenticación, el
almacenamiento y las URL firmadas ya funcionando.

```
Lee docs/10-captura-360.md completo.

Empieza por el visor, que es lo más simple y no depende de nada: el componente
ts-visor-360 en shared/, que recibe un arreglo ordenado de imágenes.

Implementa primero las funciones puras con sus pruebas: mapeo de desplazamiento
a índice, con vueltas circulares y valores negativos. Después el componente con
PointerEvent, teclado, botones visibles y la estrategia de carga.

No toques todavía el asistente de captura.
```

Después el asistente, y dentro de él, en este orden: funciones puras de recorte y
escala con sus pruebas, luego el nivelador con sus lecturas simuladas, luego la
cámara y la superposición, y al final la carga con URL firmadas.

El recorte y la escala común a todo el set son la parte que decide si el
resultado se ve bien o se ve casero. Van primero y van probadas.

## Fase 6. Cierre para publicar

Textos definitivos en los dos idiomas, políticas legales revisadas por abogado,
SEO con sitemap, hreflang y datos estructurados de producto, auditoría de
accesibilidad, Lighthouse con el visor 360 activo, respaldo restaurado de prueba,
y el DNS movido con el cuidado de `docs/07-infra-gcp.md`.

## Cómo conversar con Claude Code en este proyecto

**Un contexto limpio por tarea.** Cierra la conversación al terminar una fase. Un
contexto largo y sucio produce código que contradice lo que se decidió antes.

**Empieza cada sesión nombrando los documentos** que hay que leer. No asumas que
se acuerda.

**Pide el plan antes del código** en todo lo que toque más de tres archivos.
Léelo, corrígelo. Ahí se ahorra el tiempo, no revisando seiscientas líneas ya
escritas.

**Una capa por vez, un commit por capa.** Revertir un commit pequeño no duele.

**Cuando algo salga mal, no pidas un parche encima.** Vuelve al plan, corrige la
premisa y regenera. Los parches encadenados sobre un diseño equivocado son la
forma más rápida de terminar con código que nadie entiende.

**Al terminar cada fase, pide una revisión adversarial** con `/revisar`.

**Actualiza los documentos cuando la realidad cambie.** Un `CLAUDE.md` que
describe un proyecto que ya no existe envenena cada respuesta que venga después.
