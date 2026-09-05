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

**Inventario en pago en línea cerrado** (2026-09-03): `AplicadorDeResultadoDePago`
(compartido entre el webhook y la conciliación) ahora confirma la reserva
de cada línea cuando el pago se aprueba (`Inventario.confirmar`, la
convierte en salida real) y la libera cuando se rechaza o falla
(`Inventario.liberar`) — antes ninguno de los dos casos tocaba el
inventario, `PAGADO` nunca confirmaba su reserva ni `PAGO_FALLIDO` la
liberaba.

Un evento aprobado sobre una reserva que ya venció o se resolvió antes
(webhook tardío, o la conciliación llegando después de los 30 minutos de la
reserva) no se confirma a ciegas: la unidad pudo haberse vendido a otro
comprador ya. El pago y el pedido igual se actualizan —la plata ya
entró, eso no se revierte— pero el resultado se marca con
`APLICADO_SIN_CONFIRMAR_INVENTARIO` (log de error, riesgo de sobreventa
para revisión manual) en vez de arriesgar una sobreventa silenciosa.

`ReintentarPago` (más abajo) pasó a re-reservar inventario de verdad en vez
de solo cambiar el estado: la reserva original ya no existe una vez
liberada por el pago fallido. `Pedido.actualizarReservas` reemplaza el
`idReserva` de cada línea sin tocar precio, nombre ni cantidad. Si el stock
ya no alcanza, el reintento falla con `ExistenciaInsuficienteException` —
un caso de negocio real ("se vendió mientras tanto"), no un error.

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

**Backend de la Fase 3 cerrado** (2026-09-04). `npm run verificar` pasa
completo: lint, 67 pruebas y build del frontend; `gradlew.bat build` con las
cinco capas del backend y ArchUnit, sin excepciones. **La fase en sí no
estaba cerrada todavía en este punto**: `npm run verificar` en verde
verifica que el frontend existente compila y pasa sus pruebas, no que exista
frontend de checkout — no había ni `features/checkout` ni ningún componente
de pago o envío en `apps/web`. Bajo la regla agregada en "Cómo conversar con
Claude Code en este proyecto" (una fase cierra solo con frontend y backend
integrados), esto se corrige aquí en vez de dejarlo como un cierre formal
inexacto.

Quedaron documentadas, al cerrar, cuatro decisiones que se tomaron durante la
fase sin registrar en su momento: `Envio` como agregado propio, sin `estado`
propio (`ADR-0013`); el ciclo de vida de la reserva de inventario en pago en
línea — libera al fallar, se re-reserva al reintentar, y un pago aprobado
tarde sobre una reserva vencida se confirma como pagado igual pero sin
confirmar inventario, señalado para revisión manual (`ADR-0014`); la
excepción de `Idempotency-Key` para las acciones administrativas de un solo
actor, que la propia máquina de estados de `Pedido` ya hace idempotentes
(`docs/03-api.md`); y tres gotchas de Spring Boot 4.1 sin anotar en
`apps/api/CLAUDE.md` (Jackson 3, `@AuthenticationPrincipal` sin
`@EnableWebSecurity`, la nueva ubicación de
`UserDetailsServiceAutoConfiguration`).

**Pendientes explícitos para lo que sigue**, ninguno bloquea la Fase 4 pero
tampoco se puede dar por resuelto:

- **Contraentrega está deshabilitada en la práctica hoy**: `CONTRAENTREGA_HABILITADA`
  arranca en falso y la tabla de cobertura arranca vacía. Hace falta una
  decisión y una carga manual de negocio antes de que exista un solo pedido
  contraentrega real — `CONTRAENTREGA_MONTO_MAXIMO` sigue en placeholder de
  desarrollo también.
- **Los datos de `Envio` (transportadora, guía, costo, comisión) se pueden
  escribir pero ningún endpoint los devuelve todavía.** Un admin no puede
  verificar qué guía quedó registrada sin consultar la base de datos
  directo — pendiente para cuando se retome el panel.
- **`docs/03-api.md` documentaba `GET/PATCH /api/v1/admin/pedidos`**; el
  `PATCH` genérico nunca se construyó, cada transición tiene su propio
  endpoint de acción con nombre. Ya corregido en el propio documento.
- ~~**Límite de intentos por IP/cuenta** en login, registro, recuperación y
  creación de pedidos, prometido en `docs/08-seguridad-legal.md`: sigue sin
  construirse.~~ Cerrado en la Fase 4, Track A — ver más abajo.
- **Rotación de clave de un ADMIN ya creado**: sigue sin construirse (ya
  estaba anotado).
- **El historial de rechazos en la entrega compara solo por correo**, sin
  teléfono en el dominio. Un mismo comprador con otro correo, o un rechazo
  reportado solo por teléfono, no se detecta — riesgo de negocio real,
  aceptado implícitamente al no haber campo de teléfono, nunca discutido
  como una decisión consciente hasta este cierre.
- **Sin tope al número de reintentos de un pago fallido** — cada uno
  re-reserva inventario. No se decidió si debería tener un límite.
- **Sin pruebas contra Wompi sandbox real** (ya estaba anotado, faltan
  llaves).

**Backend de la Fase 3 completo, fase todavía abierta.** El backend cobra
por Wompi (tarjeta, PSE, Nequi, Bancolombia, Addi), por transferencia manual
con conciliación en el panel, o contraentrega de punta a punta —
disponibilidad decidida por el servidor, verificación antes de despachar,
despacho, entrega o rechazo con liberación de inventario, y recaudo
pendiente visible y conciliable—, y un pago fallido se puede reintentar sin
perder el pedido. Todo verificado con Testcontainers y `@WebMvcTest` en cada
capa; sin recorrido de punta a punta contra Wompi real todavía.

**Frontend de la Fase 3 cerrado** (2026-09-04). `features/checkout` completo,
por capas, un caso de uso a la vez, mismo patrón que `features/carrito`:
`ResumenPage` (dirección + resumen, reconcilia `CarritoStore`),
`MetodoPagoPage` (`ts-selector-metodo-pago`, nuevo en `shared/`),
`ConfirmarPage` (crea el pedido — reutiliza el ya creado en un reintento en
vez de duplicarlo — y bifurca por método), `RetornoWompiPage` (registra el
id de transacción, best-effort), `TransferenciaPage` y `EstadoPage` (con
reintento de pago fallido, incluida la vuelta a Wompi cuando aplica). Las
tres últimas comparten un mismo patrón: `CheckoutStore.pedido` si el
comprador nunca salió del sitio (contraentrega, transferencia), o
`GET /pedidos/{id}/seguimiento` con `pedidoId`/`correo` de la URL si la SPA
se recargó entera (retorno de Wompi, o un refresh en cualquiera de las dos).

**Hueco encontrado y cerrado al construir la pantalla de retorno:**
`GET /pedidos/{id}/seguimiento` estaba documentado en `docs/03-api.md` pero
nunca se había construido en el backend — sin él, no había forma de
consultar el estado de un pedido sin sesión. Se agregó
`ConsultarSeguimientoPedido` (dominio, aplicación, presentación, con
pruebas en las tres capas): el correo hace de token, comparado contra
`Pedido.correo` — no hay verificación de correo ni infraestructura de envío
transaccional todavía (eso sigue en la Fase 4), así que es lo único
construible hoy. Un correo equivocado da el mismo 404 que un id
inexistente, para no filtrar si el id existe a quien no conoce el correo
real.

**Otro hueco cerrado en el camino:** `PedidoHttpRepositorio.crear` (paso de
infraestructura) no mandaba la cabecera `Idempotency-Key` que
`docs/03-api.md` exige para `POST /pedidos` y `POST /pagos/intentos` — sin
ella, un reintento de red habría duplicado el pedido y su reserva de
inventario. Corregido antes de que el checkout llegara a usarse de verdad.

La URL del Web Checkout de Wompi (`checkout.wompi.co/p/`, parámetros
`public-key`/`currency`/`amount-in-cents`/`reference`/`signature:integrity`/
`redirect-url`) se verificó contra la documentación oficial de Wompi, no de
memoria — ver la nota de la sección de Wompi más arriba sobre el vector
fabricado que ya causó un problema real en este proyecto. `amount-in-cents`
multiplica por 100 incluso en COP (ejemplo textual de Wompi: "10000 = $100
COP").

**Fase 3 completa**, backend y frontend integrados: el checkout cobra por
Wompi, por transferencia manual o por contraentrega, de punta a punta en el
navegador, verificado con 137 pruebas de Vitest y `npm run verificar`
completo (raíz). Sin recorrido de punta a punta contra Wompi sandbox real
todavía (faltan llaves) ni verificación visual en navegador de esta sesión
(el sandbox de la herramienta de automatización no llega a `localhost`).

## Fase 4. Cuentas y panel administrativo

Autenticación completa según `docs/08-seguridad-legal.md`: registro de cliente,
verificación de correo, recuperación de contraseña y la cuenta opcional que se
ofrece al final del checkout. El login de `ADMIN` ya existe desde la Fase 3;
aquí se completa con roles y autorización por recurso.

Panel completo: productos, variantes, existencias, imágenes con URL firmada.
La vista de operación de pedidos y conciliación de recaudo de la Fase 3 pasa a
tener aquí el diseño y los componentes definitivos.

**Sesión compartida del frontend, login de `ADMIN`, `EnviadorDeCorreo`, y
registro de cliente con verificación de correo obligatoria cerrados de punta a
punta** (2026-09-04). `Usuario.correoVerificadoEn` (nulo = sin verificar) y el
nuevo agregado `TokenVerificacionCorreo` (un solo uso, sin rotación —
`V14__verificacion_correo.sql`). `POST /api/v1/auth/registro` crea siempre
`CLIENTE`, nunca abre sesión, y envía el enlace de verificación por
`EnviadorDeCorreo`; `POST /api/v1/auth/verificacion` lo consume.
`IniciarSesion` rechaza con `CorreoSinVerificarException` (403) cualquier
cuenta sin verificar. `SembradorAdmin` verifica al `ADMIN` que siembra, ya que
nunca pasa por registro; `V15__verificar_admin_existente.sql` hace lo mismo
con un `ADMIN` ya sembrado antes de esta migración, para no dejarlo sin
acceso. Sin política de complejidad de contraseña (no había ninguna definida
antes tampoco) ni límite de intentos (pendiente ya anotado). Frontend en
`features/cuenta/` (registro, verificación).

Verificado a mano contra `bootRun` + PostgreSQL + Mailpit reales, no solo con
pruebas: registrar, ver el correo real en Mailpit, login rechazado antes de
verificar (403), abrir el enlace, verificarlo (204), reintentar el mismo
token (422, ya usado), login exitoso después. De paso, dos gotchas reales
encontrados así (ninguno de los dos lo hubiera atrapado la batería de
pruebas, que no ejercita `bootRun` real):

- **`spring.mail.properties.mail.smtp.auth: true` a secas rompía en seco**
  contra Mailpit local (`jakarta.mail.AuthenticationFailedException: failed
  to connect, no password specified?`) — Mailpit no exige autenticación, y
  forzar `AUTH` con usuario/clave vacíos falla. Ahora es
  `${SMTP_AUTH:false}`, con producción activándolo por variable de entorno.
- **Un `Exception.class` genérico en `ManejadorDeErrores` no dejaba ningún
  rastro en el log del error real** detrás de un 500 — se agregó
  `log.error(...)` antes de construir la respuesta genérica; sin eso, el
  gotcha de arriba habría sido mucho más lento de encontrar.

**Login de cliente cerrado** (2026-09-04). Sin cambios de backend — `POST
/api/v1/auth/sesion` ya servía cualquier rol. `features/cuenta/presentation/iniciar-sesion/`,
simétrica a `IniciarSesionAdminPage`: una cuenta `ADMIN` que entra por aquí
cierra la sesión y muestra el error correspondiente. Nueva
`CorreoSinVerificarError` en `core/autenticacion/` para que el frontend
distinga "credenciales incorrectas" (401) de "correo sin verificar" (403) —
antes `SesionHttpRepositorio` los trataba igual. En éxito navega a la
portada (`/{lang}`): todavía no existe un panel de cuenta a dónde ir.

**Recuperación de contraseña cerrada de punta a punta** (2026-09-04). Nuevo
agregado `TokenRecuperacionClave` (`V16__recuperacion_clave.sql`), separado a
propósito de `TokenVerificacionCorreo` — uno confirma un buzón, el otro
cambia la clave, dos niveles de sensibilidad distintos. `POST
/api/v1/auth/recuperacion` (pedir el enlace) responde 204 siempre, exista o
no una cuenta con ese correo (OWASP, mismo criterio que
`CredencialesInvalidasException`); `POST /api/v1/auth/recuperacion/confirmar`
(token + clave nueva) además revoca **todas** las sesiones de refresco del
usuario, no solo una familia — nuevo `RepositorioSesiones.revocarTodasDeUsuario`,
necesario porque perder el control de la clave puede significar una sesión
abierta en un dispositivo ajeno. `docs/03-api.md` corregido: la línea
abreviada `verificacion | recuperacion` escondía que son dos pasos, no uno,
mismo patrón que ya había pasado con `registro`.

Verificado a mano contra `bootRun` real, no solo con pruebas — y con razón:
así se encontró un bug real que ninguna prueba automatizada atrapó.
`RepositorioSesiones.revocarTodasDeUsuario` usa
`@Modifying(clearAutomatically = true)`, mismo patrón que
`revocarFamilia` — pero llamado *después* de guardar el token consumido y la
clave nueva del usuario en la misma transacción, `clearAutomatically` sin
`flushAutomatically = true` limpiaba el contexto de persistencia sin volcar
esas dos escrituras antes, y las descartaba en silencio: la petición
respondía 204 igual, sin ninguna excepción, pero la clave nunca cambiaba ni
el token quedaba usado. Los dobles de prueba de la capa de aplicación no
reproducen el flush/clear real de Hibernate, así que no lo atrapaban; se
agregó `ConfirmarRecuperacionIntegracionTest` (infraestructura, Postgres
real, los tres repositorios JPA de verdad en una transacción) que sí lo
atrapa, y quedó anotado en `apps/api/CLAUDE.md` para no repetirlo. Con el
fix (`flushAutomatically = true`), el recorrido completo quedó confirmado:
solicitar con correo existente e inexistente (204 en ambos), confirmar
(204), reintentar el mismo token (422), login con la clave vieja (401) y con
la nueva (200), y la sesión de refresco de antes de recuperar ya no sirve
(401).

**Límite de intentos por IP y por cuenta cerrado** (2026-09-04) — último
punto del Track A, pendiente desde la Fase 3. Dos mecanismos separados, en
capas distintas: por IP en un filtro de servlet nuevo, `FiltroLimiteIntentos`
(mismo patrón que `FiltroIdempotencia`), atado a `/auth/sesion`,
`/auth/registro`, `/auth/recuperacion` y `/pedidos`; por cuenta (correo)
dentro de cada caso de uso mismo (`IniciarSesion`, `RegistrarUsuario`,
`SolicitarRecuperacion`, `CrearPedido`), que ya recibe el correo en su
comando — sin necesidad de espiar el cuerpo de la petición desde el filtro.
Puerto nuevo `LimitadorDeIntentos` (`application/compartido/`, sin agregado
de dominio detrás, mismo criterio que `RepositorioIdempotencia`), tabla
`limite_intentos` (`V17__limite_intentos.sql`), contador de ventana fija sin
bloqueo pesimista a propósito. Ocho variables de entorno nuevas (`LIMITE_*`),
agrupadas en dos perfiles de riesgo — "auth" (sesión/registro/recuperación) y
"pedidos", más generoso — en vez de una por endpoint. IP real detrás del
balanceador: el filtro lee `X-Forwarded-For` (docs/07-infra-gcp.md: Cloud
Load Balancing delante de Cloud Run, con varias instancias), cae a
`getRemoteAddr()` solo en desarrollo local.

Con esto, **el Track A completo (cuenta de cliente) queda cerrado**; solo
falta el Track B (panel administrativo) para cerrar la Fase 4 entera.

Verificado a mano contra `bootRun` real — otra vez con razón: apareció un bug
serio que ninguna prueba automatizada atrapó, porque los dobles de prueba de
aplicación no reproducen el comportamiento real de transacciones anidadas de
Spring. `LimitadorDeIntentosJpa.permitir()` estaba anotado `@Transactional`
a secas: como el límite por cuenta se llama *dentro* de la transacción que ya
abrió el controlador, el contador compartía esa misma transacción — y cada
intento que termina en una excepción de negocio (clave incorrecta, correo ya
registrado, que es el caso típico de un intento de abuso) revertía la
transacción entera, incluido el incremento del contador. El límite por
cuenta, en la práctica, no contaba nada: reproducido a mano contra `bootRun`
(seis registros repetidos con el mismo correo, seis 409, nunca un 429).
Mismo razonamiento que ya llevó a `RepositorioIdempotenciaJpa` a abrir su
propia transacción aparte — aquí hacía falta explícito
`@Transactional(propagation = Propagation.REQUIRES_NEW)`, no solo
`@Transactional`. Se agregó `LimitadorDeIntentosJpaTest` (infraestructura,
Postgres real) con un caso que reproduce exactamente esto: llama
`permitir()` dentro de una transacción que después revierte, y confirma que
el contador quedó igual comprometido. Confirmado también a mano: seis
registros repetidos ahora dan 201, 409, 409, 409, 409, 429.

Un segundo bug, más chico, de la misma sesión de verificación manual: el 429
que escribe `FiltroLimiteIntentos` a mano (fuera del `@RestControllerAdvice`,
los filtros corren antes del `DispatcherServlet`) salía con tildes
corruptas — `getWriter()` usa ISO-8859-1 por defecto (spec de servlets) si
nadie fija el charset, y ningún cliente HTTP moderno asume esa codificación
por defecto. Corregido con `response.setCharacterEncoding("UTF-8")` antes de
escribir, con una prueba que verifica el charset de la respuesta, no solo su
contenido (una prueba que solo compara texto no lo habría atrapado: el doble
de `MockHttpServletResponse` es codifica-y-decodifica consistente consigo
mismo aunque la codificación real esté mal).

**Track B, primer caso de uso (listar productos) más el segundo (crear
producto) cerrados de punta a punta** (2026-09-05). `GET /api/v1/admin/productos`
ya existía; se agregó `POST /api/v1/admin/productos`: alta de un producto
"pelado" (nombre, descripción, marca, categoría) en `BORRADOR` — sin
variantes ni imágenes, que quedan como sus propios casos de uso. El slug no
lo escribe el admin: nuevo `Slug.generarDesde(nombre)` en el dominio
(minúsculas, sin tildes, símbolos colapsados a un guión) y, si choca con
uno existente, `CrearProducto` le agrega un sufijo numérico hasta encontrar
uno libre. Puertos que ganaron un método sin volverse puertos nuevos:
`RepositorioProductos.guardar`, `RepositorioMarcas.buscarPorId`,
`RepositorioCategorias.buscarPorId`. Frontend en
`features/admin/productos/presentation/crear/`, formulario reactivo con
selects de marca/categoría poblados reutilizando `usarOpcionesFiltro` de
`catalogo/` (no se duplicó un endpoint admin aparte).

De paso, `CategoriaRespuesta` (pública, `GET /api/v1/categorias`) ganó
`id` — no lo tenía, a diferencia de `MarcaRespuesta`, y el select de
categoría del formulario lo necesitaba. El id de una categoría no es
información sensible; se prefirió este campo nuevo en el endpoint público
ya existente a levantar un endpoint admin aparte solo para exponerlo.

Verificado a mano contra `bootRun` real, no solo con pruebas — y por eso se
encontraron dos cosas antes de comitear:

- **Un commit anterior (`4bf469b`) había agregado por error el bytecode
  compilado de `fuentes.py`** (`packages/marca/generador/__pycache__/`) sin
  ningún cambio de código real. Ya estaba publicado en `origin/main`, así
  que no se reescribió el commit (`--force` sobre `main` está prohibido):
  se agregó `__pycache__/` y `*.pyc` al `.gitignore` y se retiró el archivo
  del tracking en un commit nuevo.
- **La navegación tras crear el producto usaba un path absoluto sin el
  prefijo de idioma** (`/admin/productos`) — las rutas de `admin` viven
  bajo `/:lang/admin/**` (`app.routes.ts`), no en la raíz, así que esa
  llamada habría quedado rota en cuanto alguien la disparara. Encontrado
  al verificar contra `bootRun` real (ninguna prueba de Vitest lo atrapaba
  porque el `Router` de prueba no valida que la ruta exista). Corregido
  siguiendo el mismo patrón que ya usa `IniciarSesionAdminPage`:
  `this.router.navigate(['/' + this.transloco.activeLang(), 'admin', 'productos'])`.

La verificación manual del backend se hizo por API directa (login real,
crear producto, slug duplicado, marca inexistente, nombre vacío), no
haciendo clic en el navegador: la extensión de automatización de Chrome no
lograba conectar a `localhost:4200` en esta máquina (ni la portada),
mientras `curl` sí respondía 200 — limitación de la herramienta, no de la
app. Queda pendiente que alguien confirme el formulario con clics reales
la próxima vez que se pueda.

**Track B, tercer caso de uso (editar producto) cerrado de punta a punta**
(2026-09-05). `GET /api/v1/admin/productos/{id}` (detalle) y `PATCH
/api/v1/admin/productos/{id}` (edita nombre, descripción, marca y
categoría). El **slug no cambia al editar** — es el identificador de URL
estable del producto, `Producto.actualizarDatosBasicos` no lo toca.
Reutilizado `ProductoAdminRespuesta` para lista y detalle (ganó
`descripcion`) en vez de un DTO de detalle aparte. Puertos que ganaron un
método: `RepositorioProductos.buscarPorId` (sin filtrar por estado, para
el admin) y `.actualizar` (UPDATE, distinto de `guardar` que es INSERT).
Nueva `ProductoNoEncontradoPorIdException`, separada de
`ProductoNoEncontradoException` porque esta última mezcla "no existe" con
"está en borrador" a propósito para el visitante público — el admin ve
todo, así que aquí un 404 sí significa que genuinamente no existe.
Frontend en `features/admin/productos/presentation/editar/`, mismo
formulario que crear, prellenado con `usarVerProductoAdmin`.

`RepositorioProductosJpa.actualizar` lee la entidad existente solo para
conservar su `creado_en` — si hubiera reutilizado `guardar` tal cual, cada
edición habría reseteado la fecha de creación (el mismo tipo de bug real
que ya apareció una vez en esta fase, esta vez evitado antes de comitear).
Confirmado a mano contra Postgres real: `creado_en` igual antes y después
de editar, `actualizado_en` sí cambia.

De paso, un bug de aislamiento entre pruebas: `RepositorioProductosDobleDePrueba`
es un bean singleton que Spring reutiliza entre los métodos de
`AdminProductoControladorTest`, así que un producto sembrado por una
prueba (`conProductos(...)`) quedaba visible en la siguiente — encontrado
al agregar las pruebas de detalle/edición (las anteriores no sembraban un
slug que otra prueba reutilizara). Corregido con `repositorio.limpiar()` +
`@BeforeEach`.

Verificado a mano contra `bootRun` real y la base de datos real (API
directa, no clics — misma limitación de la extensión de Chrome con
`localhost` ya anotada arriba): detalle (200/404), editar (200 con los
campos reflejados y el slug intacto, 404 por producto/marca/categoría
inexistente, 422 por nombre vacío), y `select` directo en Postgres
confirmando `creado_en`/`actualizado_en`.

Queda pendiente, cada uno como su propio caso de uso: variantes y
existencias, e imágenes con URL firmada — con eso cierra el Track B
completo y, con él, la Fase 4.

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

**Una fase se cierra solo cuando el frontend y el backend correspondientes
están integrados.** "Backend cerrado" o "vitrina cerrada" documentan un
avance, no el cierre de la fase — la fase completa exige los dos lados
funcionando juntos, verificado a mano contra el backend real. Documentar el
backend de una fase sin su frontend dejó, en su momento, la Fase 3 marcada
como completa sin checkout en el navegador: no se repite.

**Cuando algo salga mal, no pidas un parche encima.** Vuelve al plan, corrige la
premisa y regenera. Los parches encadenados sobre un diseño equivocado son la
forma más rápida de terminar con código que nadie entiende.

**Al terminar cada fase, pide una revisión adversarial** con `/revisar`.

**Actualiza los documentos cuando la realidad cambie.** Un `CLAUDE.md` que
describe un proyecto que ya no existe envenena cada respuesta que venga después.
