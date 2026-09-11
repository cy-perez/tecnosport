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
de cada producto, igual en todo el país, según `adr/0012`. **Esto se construyó
así y dejó de ser cierto el 8 de septiembre de 2026** — `adr/0021` volvió a la
cotización por destino; ver la Fase 7. El párrafo se conserva porque describe lo
que esta fase hizo, no lo que el sistema hace hoy. Creación de pedido
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

**Track B, cuarto caso de uso (agregar variante con atributos e inventario
inicial) cerrado de punta a punta** (2026-09-05). `GET /api/v1/atributos`
(público, mismo criterio que `/marcas` y `/categorias`: dato de catálogo
no sensible que el panel admin reutiliza) y `POST /api/v1/admin/variantes`
(crea la variante de un producto existente, con sus atributos, y su
`Inventario` inicial). Este es **el primer caso de uso que consume el
puerto `RepositorioInventario`** desde el panel admin — existía desde la
Fase 2 (`docs/09-plan-de-arranque.md`, Fase 2: *"sin caso de uso propio
todavía"*) pero nada lo llamaba hasta ahora.

Encontrado al investigar antes de planear (no al codificar): el modelo de
`Atributo` es completamente global, sin ninguna columna que lo asocie a
categoría en el esquema — el comentario de dominio *"tipado por
categoría"* es documental, no una restricción real (`SembradorCatalogo`
solo *sabe*, por convención de negocio hardcodeada en el propio sembrador,
qué atributo usar en qué categoría). El backend no valida ni filtra
atributos por categoría; queda como pendiente explícito si algún día hace
falta.

Decisiones de diseño de esta pasada:
- **`variante.existencia` (columna) y `Inventario` (histórico de
  movimientos) quedan en sync, no unificados.** La ficha pública sigue
  leyendo la columna directo (`MapeadorCatalogo`), así que se escribe con
  la existencia inicial al crear la variante; en paralelo se crea el
  `Inventario` con su primer movimiento `ENTRADA` para que las reservas
  del checkout (que sí usan `Inventario.saldoDisponible`) funcionen desde
  ya. Unificar el camino de lectura público para que deje de depender de
  la columna es un cambio aparte, más grande, y queda pendiente.
- **Sin atributos por categoría, sin lista de variantes existentes de un
  producto en esta pantalla** — ambos declarados fuera de alcance en el
  plan para no seguir creciendo el paso.
- Puertos que ganaron un método (no puertos nuevos):
  `RepositorioProductos.agregarVariante`, `.existeVarianteConSku` (chequeo
  global — `Producto.agregarVariante` en dominio solo ve las variantes que
  el agregado ya tiene cargadas en memoria, no todo el catálogo).
- `SkuYaEnUsoException` nueva, separada de `SkuDuplicadoException` del
  dominio por la misma razón que `ProductoNoEncontradoPorIdException` se
  separó de `ProductoNoEncontradoException`: mensajes correctos para
  audiencias con visión distinta del catálogo.
- `AgregarVariante.ejecutar` toca dos agregados (`Producto`/`Variante` e
  `Inventario`) en una sola llamada — la transacción la abre
  `AdminVarianteControlador` con `TransactionTemplate`, mismo patrón que
  `PedidoControlador`/`CrearPedido`.

Verificado a mano contra `bootRun` real y la base de datos real (API
directa — misma limitación de la extensión de Chrome con `localhost` ya
anotada arriba): variante creada (201) con su atributo de color,
`totalVariantes` del producto actualizado, y confirmado en Postgres que
`variante.existencia` y el movimiento `ENTRADA` del `Inventario` quedan en
sync. Los tres errores (SKU ya en uso → 409, atributo inexistente → 404,
producto inexistente → 404) responden como se diseñó.

De paso, un error real de tipos que `npm test` (Vitest/esbuild, sin chequeo
de tipos completo) no atrapó pero `ng build` sí: el cliente HTTP generado
tipa los campos opcionales como `string | undefined`, nunca `string |
null` — pasar `null` directo (como venía del dominio del frontend, que sí
usa `null` para "sin valor") fallaba la compilación de producción.
Corregido normalizando `null` a `undefined` al armar el cuerpo de la
petición, en el único punto donde el dominio del frontend cruza hacia el
cliente HTTP.

Con esto, Track B tiene listar, crear, editar y agregar variante cerrados.
Queda pendiente, como su propio caso de uso: imágenes con URL firmada —
con eso cierra el Track B completo y, con él, la Fase 4.

**Track B, quinto y último caso de uso (imagen principal con URL firmada)
cerrado de punta a punta** (2026-09-05). Alcance recortado a propósito, en
conversación previa a codificar: solo la **imagen principal** de un
producto, no la galería ni el sistema de captura/rotación 360° — eso es
Fase 5 completa. `POST
/api/v1/admin/productos/{id}/imagen-principal/url-subida` (pide una URL
firmada V4 de subida a Cloud Storage) y `POST
/api/v1/admin/productos/{id}/imagen-principal` (confirma: verifica contra
el almacén real que el objeto llegó, arma la `ImagenProducto` y reemplaza
la principal existente). El navegador sube los bytes con un `PUT` directo
a Cloud Storage — el backend nunca los toca.

Primera integración real del proyecto con un servicio de GCP más allá de
Postgres/Mailpit locales: bucket `tecnosport-dev-imagenes` en el tier
gratuito (`docs/07-infra-gcp.md`), cuenta de servicio con
`roles/storage.objectAdmin`, CORS para `localhost:4200`, credenciales
resueltas por el propio SDK vía `GOOGLE_APPLICATION_CREDENTIALS` (nunca
leído a mano en código propio). Dependencia nueva agregada con permiso
explícito: `com.google.cloud:google-cloud-storage:2.71.0`.

Decisiones de diseño de esta pasada, todas explícitas en el plan antes de
codificar:
- **Sin conversión dual WebP/JPEG.** `urlWebp` apunta al mismo objeto que
  `url` — la conversión real de formato es del asistente de captura de
  Fase 5.
- **Ancho y alto se confían al cliente** (metadato presentacional, no
  dinero ni inventario); lo único que se verifica contra el almacén real
  es que el objeto existe y su tamaño en bytes, antes de confirmar.
- **Sin borrado del objeto anterior en Cloud Storage al reemplazar la
  principal** — el bucket tiene versionado, así que no hace falta.
- **Sin límite de tamaño de subida propio**, solo una lista blanca de
  content-type (`image/jpeg`, `image/png`, `image/webp`); un máximo real
  de peso queda pendiente si el negocio lo pide.

Encontrado al escribir la prueba de infraestructura contra Postgres real
(no en las pruebas de aplicación, que usan dobles de prueba y no
reproducen el orden real de flush de Hibernate): `RepositorioProductosJpa
.guardarImagenPrincipal` borra la fila `PRINCIPAL` existente e inserta la
nueva, pero Hibernate ejecuta los `EntityInsertAction` del `ActionQueue`
antes que los `EntityDeleteAction` dentro de un mismo flush — sin forzar
el flush del borrado por separado, el insert llegaba primero y violaba el
índice único parcial (`producto_id) where tipo = 'PRINCIPAL'`). Corregido
forzando `imagenProductoJpaRepository.flush()` justo después del borrado.

Verificado con Testcontainers (Postgres real, orden de flush incluido),
`@WebMvcTest` de los dos endpoints nuevos, y Vitest del flujo completo del
frontend (selección de archivo, lectura de dimensiones, subida, error de
tipo no soportado, error del servidor). El wiring de `bootRun` real se
confirmó hasta el arranque del servidor web (todos los beans nuevos se
construyen sin error); la subida real de bytes contra el bucket de GCP
queda para verificación manual con las credenciales del entorno local,
fuera del alcance de lo que este entorno de trabajo puede probar por sí
mismo.

Con esto, Track B (listar, crear, editar, agregar variante, imagen
principal) y la Fase 4 completa quedan cerrados.

Quedaron documentadas, al cerrar, tres decisiones de la fase sin registrar en
su momento: la separación de `TokenVerificacionCorreo` y
`TokenRecuperacionClave` por nivel de sensibilidad, con revocación total de
sesiones al recuperar clave (`ADR-0015`); el riesgo aceptado de la subida de
imagen principal sin verificar el contenido real ni el tamaño (`ADR-0016`);
y la convivencia sin unificar de `variante.existencia` con `Inventario`
(`ADR-0017`). De paso, se corrigió `docs/02-modelo-datos.md`, que decía
"atributos tipados por categoría" cuando el esquema real es un catálogo
global sin esa asociación, y `docs/08-seguridad-legal.md`, que prometía
verificación de imágenes por contenido y tamaño máximo antes de que existiera
el primer caso de uso de subida real.

**Pendientes explícitos para lo que sigue**, ninguno bloquea la Fase 5 pero
tampoco se puede dar por resuelto:

- **Atributos sin filtrar ni validar por categoría** — el backend acepta
  cualquier atributo en cualquier categoría; la asociación es solo una
  convención de negocio en los datos de siembra (`ADR` no abierto, anotado en
  `docs/02-modelo-datos.md`).
- **`variante.existencia` e `Inventario` sin unificar** — la ficha pública
  sigue sin leer `Inventario.saldoDisponible`; ambos se mantienen en sync a
  mano solo en el punto donde se crea una variante (`ADR-0017`).
- **`GET/POST /api/v1/admin/variantes/{id}/inventario`** (reabastecimiento o
  ajuste sobre una variante ya creada) sigue sin construirse — anotado ya en
  `docs/03-api.md` como pendiente.
- **La pantalla de agregar variante no muestra las variantes existentes de un
  producto** — declarado fuera de alcance en el plan para no crecer el paso,
  sigue sin construirse.
- **Imagen principal:** sin conversión dual WebP/JPEG (llega con el asistente
  de la Fase 5), ancho/alto confiados al cliente sin verificar contra el
  archivo real, sin borrado del objeto anterior en Cloud Storage al
  reemplazar (mitigado por el versionado del bucket), sin verificación de
  contenido real ni tamaño máximo propio (`ADR-0016`).
- **La subida real de bytes contra el bucket de GCP no se verificó en esta
  sesión** — el wiring de `bootRun` se confirmó hasta el arranque del
  servidor, pero probar la subida real queda para verificación manual con las
  credenciales del entorno local, ya anotado al cerrar el paso 5.
- **Confirmar el formulario de crear/editar producto con clics reales en el
  navegador sigue pendiente** — limitación de la extensión de automatización
  de Chrome para llegar a `localhost` en esta máquina, no de la aplicación
  (ya anotado en los pasos 2 y 3).
- **Los umbrales de `LIMITE_*` y los vencimientos de verificación/recuperación
  de correo son criterio técnico razonable, no un dato de negocio
  confirmado** — nadie del negocio los pidió con esos números exactos
  (5 intentos por cuenta en 15 minutos, 24 horas para verificar correo, etc.);
  revisar si en producción resultan demasiado laxos o demasiado estrictos.
- **Rotación de clave de un `ADMIN` ya creado**: sigue sin construirse (ya
  estaba anotado desde la Fase 3).

## Paso previo a la Fase 5. Navegación, portada y vitrina

No estaba en el plan. Salió de una observación del dueño del proyecto: *"el
frontend no muestra mayor diferencia desde la Fase 2 y ya vamos para la Fase
5"*. Al validarla resultó cierta en lo que se ve, y falsa en lo que se
construyó: desde el cierre de la vitrina de la Fase 2 (`b4ece08`) el frontend
ganó **dieciocho pantallas** —seis de checkout, cinco de cuenta y siete de
panel administrativo— pero **ninguna estaba enlazada**. El encabezado no
había cambiado desde la Fase 2, `/{lang}` era un `redirect` al catálogo, y no
existía un solo enlace hacia `/cuenta` ni hacia `/admin` en toda la
aplicación: se llegaba tecleando la ruta. De ahí salió el tercer punto de la
regla de cierre de fase, al final de este documento.

**Traducciones con scope perezoso.** Los filtros del catálogo mostraban
`catalogo.filtros.orden.relevancia` en la lista de "Ordenar por", con siete
*Missing translation* en la consola. Las claves existían y estaban completas en
los dos idiomas — la causa era `transloco.translate()` llamado dentro de un
`computed()`: se evalúa antes de que llegue el JSON del scope y no se recalcula
nunca. Corregido con `translateObjectSignal` y con `usarTraductor()`
(`core/i18n/traductor.ts`) en los otros seis componentes con el mismo patrón
(checkout y panel administrativo, que nadie había visto porque no eran
alcanzables), más `precargarScopeI18n` en el `resolve` de las cuatro rutas con
scope propio — misma regla de ADR-0011, ahora también para i18n. Detalle y
trampas en `apps/web/CLAUDE.md`.

Verificado contra `ng serve` real, no solo con Vitest — y con razón: **el
entorno de pruebas no reproduce este bug**. Con `TranslocoTestingModule` las
traducciones llegan sincrónicas, y aun forzando un loader retardado, Testing
Library repinta lo suficiente para que el `computed` viejo se recalcule, cosa
que en el navegador hidratado no ocurre. La señal fiable son los *Missing
translation* del log del servidor: siete antes del cambio, cero después, en
español y en inglés.

**Lo demás de esa misma pasada**, todo reportado mirando la pantalla real:

- **El pie flotaba a media página.** `app.scss` tenía `display: block` con
  `min-height: 100vh`: el contenedor medía la pantalla completa, pero sus hijos
  solo su altura natural, así que el sobrante quedaba *debajo* del pie. Ahora es
  una rejilla de tres filas (`auto 1fr auto`). De paso apareció que la
  aplicación **no tenía un solo `<main>`** ni el enlace de salto al contenido que
  `docs/04-ui-marca.md` exige desde el principio; los dos existen ahora.
- **Los recuadros de los filtros medían distinto.** Un ítem de grid arranca con
  `min-width: auto` y un `<select>` mide por su opción más larga: las claves
  crudas del bug de arriba ensanchaban su columna y comprimían las demás.
  Corregido en la raíz (las traducciones) y blindado en `ts-select`/`ts-campo`
  con `min-inline-size: 0`, `inline-size: 100%` y `box-sizing: border-box` — el
  proyecto no tiene reset global, así que el relleno sumaba por fuera del 100%.
- **El logo no era un enlace.** Ahora lleva a la portada del idioma activo.
- **El carrito pasó de texto a icono**, con su `aria-label` intacto. Primer
  icono de interfaz del proyecto: el kit solo trae logos, así que la decisión
  (SVG propio en línea, 24x24, `currentColor`, sin librería) quedó escrita en
  `docs/04-ui-marca.md`.

**Navegación y portada.** El encabezado ganó el catálogo, el acceso a cuenta
según sesión (entrar/crear cuenta, o cerrar sesión) y la entrada a `/admin`
solo cuando el rol es `ADMIN`; el pie ganó su propia navegación. `/{lang}` dejó
de ser un `redirect` y sirve una portada real — hero, accesos a las tres líneas
que llevan al catálogo ya filtrado, y una franja de novedades
(`FILTRO_NOVEDADES`, orden `MAS_RECIENTES`). Vive en `features/catalogo`, no en
una funcionalidad nueva: reutiliza sus puertos, su scope de i18n y su patrón de
precarga. `ts-tarjeta-producto` pasó a armar un enlace absoluto
(`/{lang}/productos/{slug}`) porque el relativo apuntaba a otro sitio según
quién la renderizara.

**Hueco grave encontrado al recorrer las rutas, no al programar: el checkout
entero estaba muerto.** `CheckoutStore` es `providedIn: 'root'` — lo comparten
resumen, retorno de Wompi, transferencia y estado, sin relación padre-hijo—,
pero `REPOSITORIO_PEDIDOS` y `REPOSITORIO_PAGOS` se declaraban solo en el
`providers` de `checkout.routes.ts`. **Un servicio de raíz no ve los
proveedores de una ruta**: cada pantalla del checkout moría con `NG0201: No
provider found for InjectionToken RepositorioPedidos` antes de pintar nada. El
SSR respondía `404` en `/{lang}/checkout/**`.

Es de la Fase 3 y estuvo ahí desde entonces. Ninguna prueba lo atrapaba porque
cada spec de checkout provee sus propios dobles, y `npm run verificar` en verde
solo dice que compila y que las pruebas pasan. La fase se cerró anotando que no
hubo verificación en navegador — esto es exactamente lo que esa verificación
habría encontrado, y la razón de fondo por la que la regla de cierre ahora
exige recorrer el sitio, no solo abrir cada pantalla por su URL.

Corregido moviendo los dos puertos a `app.config.ts`, mismo criterio que
`REPOSITORIO_CARRITO` y `REPOSITORIO_SESION`, con `app.config.spec.ts`
verificando la invariante: todo puerto que consuma un store de raíz se declara
en la raíz. `/{lang}/checkout/resumen` y `/{lang}/checkout/estado` responden
200 en SSR desde el arreglo.

**Pendientes explícitos de este paso:**

- **La copia del hero y su imagen 4:3 de 1200x900 son `TODO` de negocio.** Los
  textos actuales son descriptivos, sacados de `docs/00-producto.md`; nadie los
  aprobó como mensaje de marca.
- **Los legales del pie (términos, política de datos) siguen sin construirse** y
  por eso no se enlazaron: un enlace roto o un texto de relleno es peor que
  ninguno. Queda en la Fase 6, donde ya estaba.
- **No hay panel de cuenta de cliente**, así que un `CLIENTE` con sesión solo ve
  "cerrar sesión" en el encabezado — no hay a dónde llevarlo todavía.
- ~~**`ts-selector-idioma`, `ts-selector-tema`, `ts-migas` y `ts-paginador` siguen
  sin existir**~~ — cerrados, ver más abajo.
- ~~**La rejilla del catálogo no tiene estado vacío**~~ — cerrado, ver más abajo.
- **Verificación con clics reales sigue pendiente** — misma limitación de la
  extensión de Chrome con `localhost` ya anotada en la Fase 4. Lo verificado
  aquí es el HTML de SSR con `curl` en los dos idiomas, el log del servidor y
  226 pruebas de Vitest.

### Deuda del sistema visual y estado vacío del catálogo

Los dos pendientes de arriba, saldados antes de arrancar la Fase 5
(2026-09-06). Un commit por componente.

**`ts-selector-idioma` y `ts-selector-tema`** (`243aec0`, `c102389`). El
encabezado usaba dos `<select>` crudos con la navegación por idioma, la cookie
de tema, el `matchMedia` y el `data-tema` metidos dentro de `Encabezado`, que
ahora queda reducido a navegación, sesión y carrito. Los dos se apoyan en
`ts-select` en vez de dibujar su propio `<select>`: ahí ya estaban resueltos el
`<label>` real, el anillo de foco, el `box-sizing` y el `min-inline-size: 0`
que costaron encontrar en el paso anterior. **Cambio visible:** los controles
del encabezado pasan a medir los 44 px de alto del objetivo táctil mínimo de
`docs/04-ui-marca.md`, que antes no cumplían.

El selector de idioma **sigue** al idioma activo con un `effect`, no solo lo
empuja: un enlace con otro prefijo o el botón de atrás también lo mueven, y el
control tiene que seguir a la URL. Ese `setValue` va con `emitEvent: false`,
porque si no, seguir a la URL dispararía otra navegación en ciclo.

Comportamiento del tema idéntico al anterior, guardia de SSR incluido; el
script en línea de `index.html` y la resolución por cookie de `server.ts` no se
tocaron. De paso quedó anotado en la prueba que **el entorno de Vitest no trae
`window.matchMedia`**: no se puede espiar con `vi.spyOn`, hay que definirla con
`Object.defineProperty`.

**`ts-paginador`** (`b223591`). El bloque de paginación estaba copiado y
pegado, idéntico, en la lista de productos y en la de pedidos del panel: mismo
markup, mismo SCSS y dos juegos de claves de i18n con el mismo texto. Los
textos pasan a claves raíz (`paginador.*`) en vez de duplicarse por
funcionalidad, y se borraron `admin.productos.paginacion` y
`admin.pedidos.paginacion`. El componente encapsula la conversión 0-based
(filtro y API) a 1-based (lo que se muestra), que antes hacía cada plantilla a
mano; en los dos `.ts`, `paginaAnterior`/`paginaSiguiente` colapsan en un
`irAPagina`. El texto de posición ganó `aria-live`: al cambiar de página el
foco se queda en el botón, que no cambia de texto, así que sin eso el cambio no
se anuncia.

**`ts-migas`** (`2cdacaf`). No había migas en ninguna parte, y **las tres
subpáginas de productos del panel (crear, editar, agregar variante) no tenían
ninguna forma de volver** salvo el botón del navegador: se llegaba a ellas y no
se salía. Es una `<ol>` de verdad, no una fila de enlaces sueltos —el orden es
la información—, la página actual no es enlace y lleva `aria-current="page"`, y
el separador va en la plantilla y no en un `content:` de CSS, que sería texto
visible fuera de Transloco. Los enlaces se reciben absolutos, con el prefijo de
idioma, por el mismo motivo que ya llevó a `ts-tarjeta-producto` a armarlos
así: se usan a profundidades distintas del árbol de rutas.

En la ficha reemplazan el enlace suelto "Volver al catálogo" (clave
`catalogo.ficha.volver` borrada): el eslabón "Catálogo" cumple lo mismo y de
paso deja la ruta lista para los datos estructurados de la Fase 6. Las cinco
pantallas del panel comparten `usarMigasAdmin` (`features/admin/migas-admin.ts`),
porque todas cuelgan de "Panel".

**Estado vacío de la rejilla** (`8358952`). Son dos situaciones distintas y
solo una la puede resolver quien mira: "no encontramos productos con estos
filtros" es accionable (quitar uno), "todavía no hay productos publicados" no.
Las distingue `hayFiltrosActivos`, función pura en `domain` con sus pruebas,
que **ignora `orden` y `tamano` a propósito**: ordenar no es filtrar y el
tamaño es paginación, ninguno de los dos cambia el conjunto de resultados. Sin
botón propio de limpiar: `app-filtros-productos` ya muestra el suyo justo
encima.

**Verificado:** `npm run verificar` completo en verde (lint, 254 pruebas de
Vitest, `ng build` y `gradlew.bat build`), y contra `ng serve` + `bootRun` +
PostgreSQL reales: `/es/productos` y `/en/productos` sirven la rejilla con
productos; con un filtro imposible sirven el mensaje de "sin resultados" en el
idioma correcto; la ficha sirve las migas con `aria-current` en los dos
idiomas y ya no tiene el enlace de volver; y el log del servidor SSR muestra
**cero *Missing translation* y cero errores** en todo el recorrido.

**Lo que no se verificó, y por qué:**

- **La rama "todavía no hay productos publicados"** solo se puede ver a mano
  despublicando la siembra; está cubierta por Vitest, no por el navegador.
- **El paginador con más de una página**: la siembra tiene cuatro productos y
  cero pedidos, o sea una sola página. Se verificó en pantalla el extremo
  "primera = última" (los dos botones deshabilitados); las transiciones entre
  páginas siguen cubiertas solo por Vitest.

### El recorrido en el navegador, por fin

El recorrido con clics reales estaba pendiente desde la Fase 4, tres cierres
seguidos, porque la extensión de automatización de Chrome no conectaba en esta
máquina. Se hizo (2026-09-06) y **encontró siete defectos que ni Vitest ni
`ng build` podían atrapar**. Vale la pena la lista completa, porque el patrón se
repite: ninguno produce un error, todos son cosas que *no pasan*.

**Encontrados en la vitrina:**

1. **El tema elegido no se aplicaba en inglés** (`7421768`). `server.ts`
   inyectaba `data-tema` reemplazando la cadena literal `<html lang="es">`; bajo
   el prefijo inglés Angular sirve `lang="en"`, no coincidía, y el atributo
   nunca se escribía. Recargar cualquier página en inglés volvía a claro con el
   selector diciendo "Dark". `server.ts` no tenía ninguna prueba; sus dos
   funciones puras se extrajeron a `tema-ssr.ts` con una que verifica inglés
   explícitamente.
2. **El correo de contacto del pie era `contact@`, no `contacto@`**
   (`7e38749`) — el `mailto:` apuntaba a un buzón que no existe. La prueba pasó
   a leer la dirección del JSON en vez de repetirla.

**Encontrados en el panel administrativo:**

3. **Recargar cualquier página de `/admin` expulsaba al administrador**
   (`9b227d7`), el más grave. `SesionStore` resuelve siempre "sin sesión" en
   SSR, a propósito y ya documentado. `adminGuard` decidía con esa información,
   así que durante el SSR redirigía *siempre* al login; el navegador seguía ese
   302 e hidrataba ya en la pantalla de login, con la sesión viva —el encabezado
   mostraba "Cerrar sesión"— pero la navegación perdida. El guardia deja de
   decidir en el servidor: se abstiene, y el cliente lo reevalúa al hidratar. No
   abre nada: lo que se sirve sin sesión es el armazón sin datos, verificado con
   `curl` sin cookies.
4. **El enlace "Agregar variante" estaba roto** (`642c683`). La ruta de la
   pantalla es `:id/editar`, dos segmentos, así que el `..` del `routerLink`
   relativo subía uno solo: generaba `productos/{id}/{id}/variantes/crear`, que
   no existe, y al hacer clic la aplicación caía en la portada. Agregar una
   variante solo era alcanzable tecleando la URL. Misma familia que el error del
   paso 2 de Track B: **las rutas de este proyecto no toleran el atajo
   relativo**, van absolutas con el prefijo de idioma.
5. **`ts-select` perdía el valor si las opciones llegaban después**
   (`679db0e`). Marcaba la opción activa con `[value]` en el `<select>`, y
   Angular fija esa propiedad una sola vez. Cuando el valor y las opciones
   vienen de consultas distintas y el valor llega primero, ninguna `<option>`
   engancha y el control se queda en el placeholder para siempre. Se veía en
   editar producto: Marca y Categoría en "Selecciona una opción" con el producto
   ya cargado y, como son obligatorias, editar solo el nombre obligaba a
   reelegirlas. Ahora cada `<option>` lleva su `[selected]`, que de paso arregla
   el SSR (`[value]` no serializa a HTML).
6. **`ts-paginador` decía "Página 1 de 0"** con la lista vacía (`038e8a4`) — el
   backend devuelve `totalPaginas: 0` sin resultados, y el `?? 1` de los
   consumidores solo cubre "aún no respondió", no "respondió cero".
7. **Enlace ilegible en tema oscuro y enlaces del panel pegados** (`8d607a2`).
   El "Editar" de la tabla era el único `<a>` del panel sin clase: caía en el
   azul del agente de usuario sobre fondo casi negro. Y los dos enlaces del
   panel salían como "PedidosProductos". Los dos ganaron también su anillo de
   foco.

**La lección, para la Fase 5:** de siete defectos, cinco eran invisibles para la
batería de pruebas porque no producen un error — un `href` que no coincide, una
propiedad que no se reasigna, un color heredado del navegador. La regla de
cierre que exige recorrer el sitio de verdad no es burocracia: es el único
mecanismo que los encuentra.

**Pendientes que salieron del recorrido**, los tres cerrados después — ver la
sección siguiente:

- ~~**"Ordenar por" sale en blanco en la primera pintada** cuando la URL no trae
  `orden`. Los otros cinco filtros muestran su placeholder ("Todas"); ese no
  tiene, y su `FormControl` arranca en `''`, que no corresponde a ninguna
  opción. Tras hidratar, o al navegar dentro de la SPA, sí muestra
  "Relevancia". Tiene tres arreglos con semánticas distintas y ninguno es
  obviamente el correcto: no se eligió en silencio.~~
- ~~**La tabla de pedidos vacía no dice que está vacía** — mismo hueco que se
  cerró en la rejilla del catálogo, pero en el panel.~~
- ~~**Avisos de `NgOptimizedImage` en la consola**: `NG02952` (la relación de
  aspecto pintada no coincide con la intrínseca) en las tarjetas de producto, y
  `NG02955` (la imagen del LCP sin `priority`) en la portada.~~

### Los tres pendientes del recorrido, cerrados

2026-09-06, un commit por pendiente más dos correcciones que salieron de volver
al navegador. Otra vez el patrón: **las dos correcciones las encontró la
pantalla, no la batería de pruebas**, y las dos eran supuestos míos escritos con
confianza.

**1. "Ordenar por" en blanco** (`f276bdd`). El `FormControl` arrancaba en `''` y
ese select es el único de los seis sin `placeholder` que cubriera el vacío,
mientras el backend sí ordenaba: `ProductoControlador` declara
`@RequestParam(defaultValue = "RELEVANCIA")`. De las tres salidas posibles se
eligió, en conversación previa, que el control arranque en `ORDEN_POR_DEFECTO`
—constante nueva del dominio, con el comentario que apunta al controlador— en
vez de inventar un placeholder o normalizar la URL con una navegación extra por
visita. El valor inicial va en el propio `FormControl` y no solo en el
`patchValue`: `limpiar()` hace `form.reset()`, que vuelve al valor inicial, y
reproducía el mismo blanco. **Efecto lateral aceptado:** en cuanto se toca
cualquier filtro, `orden=RELEVANCIA` viaja en la URL.

**2. Las tablas vacías del panel** (`b25f07c`, corregido en `06a942b`). Pedidos
distingue las dos situaciones igual que la rejilla del catálogo: con filtro de
estado, quien mira lo puede resolver; sin filtro, no hay nada que hacer todavía.
En la rama vacía no se pinta ni la tabla ni el paginador. **Productos tenía el
mismo defecto y el recorrido no lo había reportado**: la siembra trae cuatro
productos y cero pedidos, así que solo uno de los dos se veía.

Al mirarlo en pantalla apareció que la primera versión mentía: con cuatro
productos, `?pagina=5` decía "Todavía no hay productos", porque una página
fuera de rango tampoco trae filas. `Page.getTotalPages()` distingue los dos
casos (0 si no hay ninguna fila, 1 si las hay pero la página se pasó del final),
así que la rama vacía exige también `totalPaginas === 0`.

**3. Los avisos de `NgOptimizedImage`** (`1bdb99e`, corregido en `982de0c`).
`NG02952` era real: `ts-tarjeta-producto` y `ts-galeria` declaraban el ancho y
el alto del archivo mientras el SCSS recortaba a un cuadrado con
`object-fit: cover`. Ahora van en modo `fill` dentro de un marco con
`position: relative` y `aspect-ratio`, que es el mecanismo que Angular documenta
para una imagen cuyo tamaño decide el CSS. **`ts-galeria` tenía el mismo defecto
sin que nadie lo hubiera reportado**: las imágenes de la siembra que llegan a la
ficha son cuadradas, así que ahí no se disparaba.

`NG02955` costó dos intentos. El primero marcó `priority` en la primera tarjeta,
dando por hecho que el LCP era esa. Medido en el navegador, no lo es: las cuatro
imágenes se pintan de 225x225 al mismo `top`, el ganador lo decide el orden de
decodificado, y resultó ser **la segunda en la portada y la cuarta en la
rejilla**. Va prioritaria toda la franja de novedades y la primera fila de la
rejilla. El corte de la fila es un compromiso: un monitor muy ancho puede meter
una quinta tarjeta sobre el pliegue y volver a disparar el aviso.

**Verificado** con `npm run verificar` completo (lint, 274 pruebas de Vitest,
`ng build` y `gradlew.bat build`) y con clics reales contra `ng serve` +
`bootRun` + PostgreSQL: "Ordenar por" mostrando "Relevancia" en español y
"Relevance" en inglés, con el desplegable abierto confirmando que la opción está
seleccionada de verdad y no es solo la primera pintada; ordenar por precio
descendente ordenando de verdad y dejando `?orden=PRECIO_DESC`; "Limpiar
filtros" devolviendo a "Relevancia" y no al blanco; las dos ramas del estado
vacío de pedidos —con y sin filtro de estado— en pantalla; y la consola sin
`NG02952` ni `NG02955` en portada, rejilla y ficha.

**Pendientes nuevos, todos vistos en esta pasada y ninguno cerrado:**

- **`NG02956`: no hay `preconnect` al host de las imágenes.** Lo destapó tener
  por fin imágenes con `priority`. No se arregló porque el arreglo es un
  `<link rel="preconnect" href="...">` con una URL literal en el `<head>`, y la
  regla dura #5 prohíbe URL literales en el código; además el host que aparece
  hoy (`picsum.photos`) es el de los datos de siembra, no el de producción.
  Cuando exista el host real de imágenes hay que resolverlo por configuración.
- **`[selected]` no se serializa en el HTML de SSR.** El comentario de
  `ts-select` dice que marcar cada `<option>` con `[selected]` "de paso arregla
  el SSR"; comprobado con `curl`, **no lo hace**: ningún `<option>` sale con el
  atributo, ni con `?orden=PRECIO_DESC` ni con `?linea=BOLSOS`. Antes de hidratar
  el navegador muestra siempre la primera opción. Hoy no se nota —en los cinco
  filtros con placeholder la primera es el placeholder, y en "Ordenar por" es
  justamente "Relevancia"—, pero con un filtro puesto en la URL la primera
  pintada miente hasta que hidrata.
- **El pie de `ts-tarjeta-producto` se desborda.** Medido en la portada: las
  cuatro tarjetas desbordan su caja entre 10 y 39 px, la peor la del producto más
  caro ("Desde $ 1.299.900" más la etiqueta "Disponible" en 193 px). Es un flex
  con `space-between` sin `min-inline-size: 0` ni envoltura — la misma familia del
  problema que ya se corrigió en `ts-select`/`ts-campo`. Es anterior a esta
  pasada.
- **El paginador dice "Página 5 de 1"** en una página fuera de rango. Anterior a
  esta pasada; el "Anterior" funciona, así que hay salida.

### `.env.local` no llegaba al backend

Encontrado al preparar el recorrido (2026-09-06, `c0ccd64`). `README.md` y
`apps/api/README.md` dicen desde el principio que las variables de desarrollo
viven en `.env.local`, pero **nada leía ese archivo**: Spring Boot lee variables
de entorno del proceso, no archivos `.env`, y `bootRun` solo fijaba
`spring.profiles.active`. Toda la configuración local salía en realidad de los
valores por defecto de `application.yml` — la documentación describía un
mecanismo que no existía, y no se notaba porque cada valor por defecto tapaba el
hueco.

`bootRun` ahora lee el archivo y lo pasa como entorno del proceso, sin
dependencia nueva. Dos decisiones, las dos encontradas arrancando de verdad: una
variable ya exportada en la terminal gana sobre el archivo (contrato habitual de
dotenv), y **una clave con valor vacío se omite** en vez de pasarse como cadena
vacía — `.env.example` trae varias así a propósito
(`CONTRAENTREGA_MONTO_MAXIMO=`, `SMTP_USUARIO=`) queriendo decir "usa el valor
por defecto", y al pasarlas vacías la aplicación dejaba de arrancar:
`monto-maximo` es un `long` primitivo y recibía null. De paso, `.env.example`
ganó `ADMIN_CORREO` y `ADMIN_CLAVE`, que estaban en `application.yml` pero
faltaban en la plantilla.

Con eso se sembró el `ADMIN` real del proyecto (`contacto@tecnosport.co`, el
mismo buzón del pie), borrando antes el `admin@tecnosport.co` que había creado
el valor por defecto. **Rotar la clave de un `ADMIN` ya creado sigue sin
construirse**, y ahora se siente más: la única salida es borrar la fila y volver
a arrancar. Queda anotado en el propio `.env.example`.

**Pendientes que este paso no toca:** `ts-checkbox`, `ts-radio`, `ts-dialogo`
y `ts-notificacion` siguen sin construirse, a propósito — nada los necesita
todavía, y se construyen cuando aparezca el primer consumidor real. Los
legales del pie siguen en la Fase 6, y sigue sin haber panel de cuenta de
cliente.

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

**Visor cerrado de punta a punta** (2026-09-06), en los tres pasos del prompt y
un commit por paso.

Las **funciones puras primero** (`shared/ts-visor-360/rotacion-360.ts`,
`ac1b38d`): `indiceCircular` (el `%` de JavaScript conserva el signo del
dividendo, así que un desplazamiento negativo necesita la segunda vuelta),
`indiceDesdeDesplazamiento`, `indiceOpuesto` y `ordenDePrecarga`. Un giro
completo es aproximadamente un ancho de arrastre, así que la sensibilidad es
relativa al contenedor y se siente igual en teléfono y en escritorio.
**`Math.round` no sirve tal cual**: rompe los empates hacia +∞ (`-0,5` da `-0`
pero `0,5` da `1`), y el visor giraría antes hacia un lado que hacia el otro; el
redondeo va sobre el valor absoluto. 26 pruebas, con los casos de borde que el
componente no puede cubrir cómodo: ancho sin medir todavía, un solo fotograma y
cero fotogramas.

**El componente** (`3a95f01`) recibe un arreglo ordenado de URL y nada más.
`PointerEvent` único para ratón, dedo y lápiz; solo el eje horizontal gira y el
vertical se lo queda el navegador por `touch-action: pan-y`. Teclado con flechas,
Inicio al frontal y Fin al opuesto, consumiendo solo la tecla que el visor usa.
Botones visibles con `etiquetaAccesible` y contador de posición con `aria-live`,
mismo motivo que `ts-paginador`. La pista "arrastra para girar" se va con la
primera interacción y, si nadie toca nada, sola a los cuatro segundos: un texto
permanente encima de la imagen es justo lo que `docs/10-captura-360.md` no
quiere.

Dos decisiones que hubo que verificar en vez de suponer (regla dura #9):
- **`ngSrc` sí se puede cambiar en caliente**; el resto de entradas de
  `NgOptimizedImage` (`priority`, `fill`, `width`, `height`...) están congeladas
  tras inicializar. Comprobado en la fuente de `@angular/common`
  (`assertNoPostInitInputChange`), no de memoria. Por eso el visor es un solo
  `<img>` que cambia de `ngSrc` y no ocho apilados.
- **Esa misma congelación decide la forma de la plantilla de la ficha.**
  `ts-galeria` ganó la entrada `prioritaria` (mismo patrón que
  `ts-tarjeta-producto`: quién es la candidata a LCP lo sabe la pantalla), y la
  ficha repite `<ts-galeria>` en las dos ramas del `@if` a propósito, con un
  literal en cada una — ligar `prioritaria` a una expresión reventaría al
  navegar de una ficha con rotación a otra sin ella.

**Carga**: el fotograma 0 lo sirve el SSR y es el único con `priority`; el resto
se precarga en cadena después del evento de carga, en orden de cercanía, y no se
precarga nada con ahorro de datos o conexión lenta. Mientras tanto el arrastre no
se bloquea: se pinta el fotograma disponible más cercano al deseado y el que
falte se pide bajo demanda.

**La siembra no tenía ningún set de rotación** (`0f544d5`), y sin eso el visor no
se podía ver en el navegador: el esquema soporta `set_rotacion` desde `V1` y la
ficha ya lo exponía desde la Fase 1, pero ninguno de los cuatro productos de
desarrollo tenía uno. Ahora el tenis trae ocho fotogramas de 1000x1000 en un set
`PUBLICADO` —el único estado que la ficha pública expone—, con imágenes de
`picsum.photos`, una distinta por fotograma: sirven para ejercitar el visor, no
para juzgar cómo se ve una rotación de verdad.

**Verificado** con `npm run verificar` completo (lint, 320 pruebas de Vitest,
`ng build` y `gradlew.bat build`) y con clics reales contra `ng serve` +
`bootRun` + PostgreSQL, llegando a la ficha desde la portada: los botones
girando en los dos sentidos, el arrastre de 160 px avanzando exactamente tres
fotogramas —la aritmética que dicen las pruebas—, Inicio volviendo al frontal y
Fin al opuesto **sin desplazar la página**, la pista yéndose sola a los cuatro
segundos, el chip legible sobre la foto en tema claro y oscuro, y la consola sin
`NG02952`, sin `NG02954` (ni siquiera navegando de la ficha con rotación a una
sin ella, que es el caso que la plantilla de dos ramas evita) y sin `NG02955`.
El HTML del SSR trae **solo el fotograma 0**, con `fetchpriority="high"`,
mientras la imagen de la galería queda en `loading="lazy"` — la prioridad se
movió de verdad. Cero *Missing translation* en el log del servidor, en los dos
idiomas.

**Lo que no se verificó, y por qué:**

- **El gesto táctil real.** `touch-action: pan-y` está puesto y el arrastre
  vertical no gira, comprobado con ratón, pero que el desplazamiento de la
  página siga funcionando con el dedo encima del visor solo lo dice un teléfono
  de verdad.
- **La rama de conexión lenta / ahorro de datos.** `navigator.connection` no se
  puede simular desde la automatización; está cubierta por Vitest, no por el
  navegador.

**Pendiente nuevo:** `NG02956` (sin `preconnect` al host de las imágenes) ahora
también lo dispara el fotograma 0 del visor. Es el mismo pendiente ya anotado en
la Fase 4 y sigue sin resolverse por el mismo motivo: el arreglo es una URL
literal en el `<head>`, que la regla dura #5 prohíbe, y el host de hoy es el de
la siembra. Cuando exista el host real de imágenes hay que resolverlo por
configuración.

**Aviso de presupuesto de bundle, que no es de esta fase:** `ng build` avisa que
el bundle inicial se pasa del presupuesto de 600 kB. **No lo causó el visor** —
comprobado construyendo `main` antes de esta rama: 603,52 kB allí contra 603,54
kB aquí, dos centésimas de diferencia. Todo el código del visor va en el trozo
perezoso de la ficha. Queda anotado porque nadie lo había anotado.

### La revisión adversarial del visor

Trece hallazgos; se cerraron los tres primeros (2026-09-06). Los cinco puntos más
graves del guion —flechas de dependencia, lógica en la capa equivocada, `double`
para dinero, confianza en el cliente, idempotencia— salieron limpios, pero por un
motivo que conviene decir: **este cambio es un visor de solo lectura**, no toca
dinero ni inventario ni escribe nada. No probó nada sobre esas reglas.

**1. Cuatro archivos quedaron comiteados en CRLF** (`62e8599`) mientras el resto
del repositorio es LF. Lo grave no es el carácter: el diff deja de mostrar el
cambio. La edición real de `ficha.page.ts` son diecisiete líneas y aparecían
doscientas cincuenta y una; la de este documento, noventa y una contra dos mil
novecientas tres. Un PR así no se revisa, se aprueba a ciegas. Los escribió un
script en modo texto sobre Windows, que traduce al escribir. Con
`core.autocrlf=false` y sin `.gitattributes` gana la herramienta que escribió
último, así que no bastaba con convertir los cuatro: el repositorio ahora fija LF
para todo, con CRLF solo en `.bat`/`.cmd` (que es `gradlew.bat`) y los binarios
marcados.

**2. "Fotograma 1 de 0"** (`838ecb0`). Con cero o un fotograma el visor pintaba
igual los dos botones y un contador que mentía — **el mismo defecto que ya se
había corregido en `ts-paginador`** (`038e8a4`, Fase 4), reintroducido en un
componente nuevo. No se veía porque el backend solo expone sets `PUBLICADO`, que
exigen cuatro fotogramas; se iba a ver en cuanto el asistente de captura, que usa
este mismo visor en su paso 6, le pasara un set a medio armar.

**3. Las instrucciones de teclado no estaban asociadas al foco** (`838ecb0`).
Estaban en un `<p>` hermano: visibles para quien ve y para nadie más. Quien usa un
lector de pantalla enfocaba el visor, oía "Vista 360 del producto, grupo" y no se
enteraba de que las flechas giran. Ahora van con `aria-describedby`, con el id de
un contador de módulo. Se había omitido a propósito, para no inventar ids únicos
con SSR de por medio; ese razonamiento se quedó en la conversación y no en el
código, y el que pagaba era el usuario del lector.

Las cuatro pruebas de los arreglos 2 y 3 **se comprobaron mutando la
implementación**: fallan si se deshace el arreglo. Y se volvió al navegador,
porque el `@if` nuevo envuelve el elemento del `viewChild`: el arrastre sigue
girando y la hidratación no se queja del id.

**4. Ordenar y elegir formato vivían en la página**, no en el mapeador. Cerrado
después, junto con el 8 — ver la sección siguiente.

**Los seis hallazgos restantes quedan abiertos**, por orden de lo que costaría
que muerdan:
- ~~**Un refetch en segundo plano devuelve el visor al frontal.**~~ Cerrado — ver
  la sección siguiente. Arrastraba también a la selección de variante, que tenía
  el defecto desde la Fase 1.
- ~~**`cargados` se lleva por índice y nada cancela la precarga en vuelo.**~~
  Cerrado — ver la sección siguiente.
- **Un fotograma roto se salta en silencio**: ni log ni señal, el contador sigue
  diciendo ocho y uno nunca aparece.
- **La pista sale en cada visita**, no "la primera vez" como pide
  `docs/10-captura-360.md`: nada recuerda que ya se vio.
- **Sin captura de puntero el arrastre se queda pegado**: si se suelta fuera del
  marco no llega `pointerup` y `arrastrando` se queda en verdadero. El comentario
  del código afirma que el camino degradado funciona; funciona a medias.
- **Dos pruebas del visor pasarían con la implementación borrada** (las dos
  negativas: que el arrastre vertical no gire y que mover sin arrastrar no gire).
  Valen porque las positivas están al lado; solas serían decorativas.
- **`guardarSetRotacion` del sembrador no tiene ninguna prueba**: lo único que lo
  ejercita es haber arrancado `bootRun` a mano.
- **Tercera copia del host literal de imágenes** en el sembrador, contra la regla
  dura #5. Perfil `local` y con precedente dos métodos más abajo, pero ya son tres.

### Dos hallazgos más, cerrados: el mapeador y la prioridad por omisión

2026-09-06, un commit.

**El orden de los fotogramas se garantiza en el mapeador** (`aRotacion`), que es
la frontera donde el DTO se vuelve modelo. La ficha ya no ordena: recibe el
arreglo ordenado y no tiene que saber que el orden importa. Antes, cualquier
pantalla nueva que consumiera `rotacion` tenía que acordarse.

**Y la elección de formato es una sola regla**, `urlPreferida` en el dominio del
catálogo: WebP con el original de respaldo, la regla de imágenes de
`apps/web/CLAUDE.md`. Antes el visor servía la WebP y la galería el original, sin
que nadie lo hubiera decidido: eran dos expresiones sueltas en dos plantillas.
`ts-tarjeta-producto` tenía la tercera y también pasa por la función — dejarla
fuera habría recreado la incoherencia el mismo día de arreglarla.

**`ts-galeria.prioritaria` pasa a `false` por omisión.** El valor por defecto
tiene que ser el que no hace daño: una pantalla nueva que se olvide de decidir se
lleva una imagen sin priorizar, no una segunda candidata a LCP compitiendo con la
de verdad. La ficha ahora lo declara explícito en sus dos ramas, aunque una
coincida con el defecto: quién es la candidata de esa pantalla se lee en la
pantalla.

**Una prueba nueva no servía y lo dijo el mutante.** La que cubre el valor por
omisión de `prioritaria` pasaba igual con el defecto cambiado a `true`, porque el
ayudante `renderGaleria` mandaba siempre el input y el valor por defecto no lo
ejercitaba nadie. Se arregló el ayudante para que solo lo pase cuando la prueba lo
pide, y entonces sí falla. **Los dos arreglos se comprobaron mutando la
implementación**, no viendo pasar las pruebas.

Verificado también en el navegador, porque el cambio de WebP toca la portada y la
rejilla y no solo la ficha: las cuatro tarjetas cargan, la ficha con visor da
`fetchpriority=high` en el fotograma frontal y `auto`/`lazy` en la galería, la
ficha sin visor devuelve el `high` a la galería, y en las tres pantallas la
consola solo trae el `NG02956` ya conocido — uno por pantalla, que es la señal de
que hay exactamente una imagen prioritaria en cada una.

### El reinicio por revalidación, cerrado — y la prueba que no probaba nada

2026-09-06, un commit. Es el hallazgo que más costó **confirmar**, no arreglar.

**La primera prueba pasaba con el defecto puesto.** Se escribió una que giraba el
visor, forzaba `refetchQueries()` y comprobaba que el fotograma seguía en su
sitio: pasaba en verde sin tocar el código. No porque no hubiera defecto, sino
porque la revalidación no estaba llegando al componente y la prueba no lo
comprobaba. Al añadirle que el **precio nuevo tiene que verse en pantalla** —la
señal de que el refetch sí llegó— la prueba falló, que era lo correcto. Sin esa
comprobación habría quedado un hallazgo "verificado como inexistente" y un
defecto vivo.

Con la prueba sirviendo, quedaron claras dos situaciones distintas:

- **Si los datos vuelven idénticos, no pasa nada.** TanStack hace *structural
  sharing* por omisión y conserva la referencia anterior, así que ningún efecto
  se dispara. Esa rama tiene su propia prueba, para que se sepa que es la
  librería quien lo evita y no el código de aquí.
- **Si cambia cualquier cosa del producto** —el precio, la existencia—, el objeto
  es nuevo, el `computed` de la ficha devuelve un arreglo nuevo, y ahí sí se
  reiniciaba el visor al frontal.

El arreglo, en los dos sitios, es dejar de usar la identidad como señal de "esto
cambió":

- **`ts-visor-360` depende de `claveDelSet`**, el contenido del arreglo unido en
  una cadena. Un `computed` que devuelve una cadena igual no propaga, así que el
  efecto de reinicio solo corre cuando el set de verdad es otro.
- **La ficha depende de `slugCargado`**, no de `producto()`, y lee el producto con
  `untracked`. La selección de variante se reinicia al cargar otro producto y no
  porque el mismo haya vuelto del servidor. **Ese defecto era de la Fase 1**, no
  del visor: elegir "Negro" y perderlo al volver a la pestaña es peor que perder
  un fotograma.

Con una prueba para cada lado de la moneda, porque "no reiniciar en un refetch"
no puede volverse "no reiniciar nunca": navegar a otro producto sí suelta la
variante elegida. Los dos arreglos se comprobaron mutándolos.

**Verificado también en el navegador, con TanStack de verdad**: pestaña abierta
en la ficha, visor girado al fotograma 4, cambio real de pestaña (comprobado que
`document.visibilityState` pasa a `hidden`), vuelta pasados los sesenta segundos
del `staleTime`, y en el panel de red **aparece la petición de revalidación** —
el visor siguió en el fotograma 4. La revalidación de esa corrida trajo los
mismos datos; el caso de datos distintos es el que cubre la prueba, con el
`QueryClient` real y no un doble.

### La precarga en vuelo, cerrada

2026-09-06, un commit. Es el hallazgo que más le importa al asistente de captura,
que va a cambiar de set continuamente mientras se capturan fotogramas.

**Lo cargado se lleva por URL, no por índice.** Con índices, el `onload` tardío
del fotograma 3 del set anterior marcaba disponible el 3 del set nuevo, que nadie
había pedido: el visor saltaba a una imagen sin cargar y parpadeaba, en vez de
quedarse en el fotograma disponible más cercano. Con URL, cada respuesta habla
solo de sí misma. De paso, ya no hace falta vaciar nada al cambiar de set: una URL
que cargó sigue en la caché del navegador, se esté mirando el set que se esté
mirando, así que volver al producto anterior lo encuentra listo.

**La cadena de precarga se corta por generación.** Cada vez que arranca una
cadena toma un número; en cada eslabón comprueba que sigue siendo la vigente, y si
no, se abandona. Antes, la cadena del set viejo seguía caminando mientras el
visitante ya miraba otro.

**Y ahora el set nuevo también se precarga.** Antes la cadena era de un solo
disparo, atada al evento de carga de la página: al cambiar de set no se lanzaba
ninguna, así que el segundo producto de una sesión se quedaba sin precarga y sin
que nadie lo notara. Ahora el mismo efecto que devuelve el visor al frontal lanza
la cadena del set nuevo, si la página ya terminó de cargar.

Dos pruebas nuevas, con un doble de `Image` que la prueba completa a mano — es la
única forma de reproducir "una imagen del set anterior que llega *después* del
cambio". Las dos se comprobaron mutando la implementación.

**Verificado en el navegador**: en el panel de rendimiento, los ocho fotogramas se
piden en el orden `0, 1, 7, 2, 6, 3, 5, 4` —el frontal primero y después los
vecinos alternando, en cadena y no en paralelo— y girar cinco veces con el teclado
pinta el fotograma 6 de verdad.

**El cambio de set se recorrió después**, cuando la siembra dejó de tener un solo
producto con rotación — ver la sección siguiente.

### Un segundo set en la siembra, y el recorrido que faltaba

2026-09-06. La siembra tenía un solo producto con rotación, y por eso el camino
donde vivían **los dos defectos más serios del visor** —la carrera de la precarga
y el reinicio del fotograma— era justo el que no se podía recorrer en el
navegador. Ahora el morral trae un set de **4 fotogramas**, el mínimo publicable
de la tabla de `docs/10-captura-360.md`, frente a los 8 del tenis: dos tamaños
distintos, y los otros dos productos siguen sin rotación. Los tres casos que hacen
falta en desarrollo.

**El recorrido, con clics reales y navegación de la SPA** (tenis → catálogo →
morral → atrás → tenis):

- El visor del tenis precarga sus ocho y queda en el fotograma 6.
- Al llegar al morral, el contador dice **"Fotograma 1 de 4"** y lo que se pinta
  es su frontal: el set nuevo reinicia, que es lo correcto, mientras que una
  revalidación del mismo producto no lo hace.
- **El set nuevo se precarga solo**, en su propio orden (`0, 1, 3, 2`), y las
  peticiones del tenis se quedaron en ocho: la cadena vieja no siguió caminando.
  Es la prueba en pantalla de los dos arreglos de la sección anterior.
- La tecla Fin en un set de 4 lleva al fotograma 3, que es el opuesto del frontal
  con número par de fotogramas.
- Al volver al tenis, el visor arranca otra vez en su frontal y vuelve a pedir sus
  fotogramas: salir de la ficha destruye el componente, así que su memoria de lo
  cargado se va con él. Las imágenes salen de la caché del navegador, no de la
  red; no es un defecto, pero conviene saberlo antes de leer el panel de
  rendimiento y asustarse.

Sin `NG02952`, `NG02954` ni `NG02955` en todo el recorrido, con dos fichas con
visor y una sin él.

### El asistente de captura: recorte y escala, las funciones puras

2026-09-06, primer paso del asistente y el que el propio documento manda hacer
primero: *"el recorte y la escala común a todo el set son la parte que decide si el
resultado se ve bien o se ve casero"*. `features/captura360/domain/recorte-360.ts`,
sin DOM, sin `canvas` y sin señales — recibe los píxeles ya leídos y devuelve
números. El tipo `DatosImagen` es propio aunque el `ImageData` del navegador encaje
tal cual: así se prueba sin DOM.

`colorDeFondo` estima el fondo con las cuatro esquinas y devuelve `null` cuando no
se parecen entre sí. `detectarRectanguloDelProducto` binariza por umbral de
luminancia contra ese fondo y devuelve la envolvente, o **por qué no pudo**
(`IMAGEN_INVALIDA`, `FONDO_NO_UNIFORME`, `SIN_PRODUCTO`, `PRODUCTO_CORTADO`): es lo
que le va a permitir al asistente ofrecer recorte manual en vez de subir un recorte
que sabe que salió mal. Un producto que toca el marco se rechaza — puede estar
cortado, y sobre todo no queda dónde poner el margen que el resto del set sí va a
tener. `encuadreDelSet` da un solo lado de recorte y una sola escala para los N
fotogramas, y `recorteDeFotograma` devuelve el origen y el destino que necesita
`drawImage`, con el caso del producto cerca de un borde resuelto: el origen se
recorta a lo que existe y el destino se corre en la misma proporción, en vez de
estirar lo que sí existe para llenar el cuadro — estirarlo cambiaría la escala de
ese fotograma, que es justo lo que este módulo evita.

**Dos decisiones de las que `docs/10-captura-360.md` no tenía todavía**, las dos ya
corregidas en ese documento:

- **La referencia de la escala no es "el fotograma más ancho"**, que es lo que decía
  la letra. Un fotograma más alto que el ancho del más ancho —el mismo tenis de
  perfil frente al tenis de frente— se saldría del cuadro 1:1 y quedaría cortado.
  Ahora es el **lado mayor de todos los rectángulos, en las dos dimensiones**: en el
  caso típico coincide, y nunca corta. Tiene su prueba con nombre propio.
- **El margen del set es el 8% del lado mayor**, a cada lado
  (`MARGEN_RELATIVO_DEL_SET`). El documento pedía "el mismo margen relativo" sin
  decir cuál; el número lo decidió el negocio, no la sesión.

**Las pruebas se comprobaron mutando la implementación**, no viéndolas pasar: siete
mutaciones —escalar por el más ancho, la envolvente con un off-by-one, el guardia
del polvo bajado a un píxel, la tolerancia entre esquinas anulada, el rechazo del
producto cortado desactivado, el destino sin correr en el borde y el margen por
omisión cambiado— cada una atrapada por exactamente los casos que le tocaban.
También hay una prueba del **límite honesto** que el documento ya declaraba: un
producto de la misma luminancia que el fondo (un ámbar claro sobre gris claro) no se
detecta, y eso pide recorte manual.

29 pruebas nuevas; `ng test` completo en verde (369) y `ng lint` limpio. Sin nada que
ver en el navegador todavía: no hay pantalla, a propósito.

### El nivelador, sobre lecturas simuladas

2026-09-06, segundo paso del asistente y el segundo que se puede construir sin cámara
ni backend. `features/captura360/domain/nivel-360.ts`: recibe una lectura de
orientación y devuelve qué mostrar y si el obturador se habilita. No sabe que existe
un sensor — sabe que a veces hay lectura y a veces no.

`normalizarAngulo` y `diferenciaAngular` resuelven el arco corto; sin eso, pasar de
179 a -179 se leería como un giro de 358 grados y el nivel diría "fuera de rango" con
el teléfono quieto. `suavizar` es una media exponencial sobre ese arco corto, con la
primera lectura pasando tal cual para que el indicador no arranque arrastrándose desde
un valor inventado. `evaluarNivel` da los tres estados del documento —fuera de rango,
cerca, en rango— más `SIN_SENSOR`, que no es un estado del nivel sino su ausencia, con
la desviación **con signo** de cada eje y cuál de los dos manda, que es lo que le
permite a la interfaz decir en texto hacia dónde corregir en vez de solo pintar un
color.

Tres decisiones que valen la pena:

- **Sin lectura, `puedeDisparar` es `true`.** Es el modo degradado que exige el
  documento: un flujo que se bloquea sin sensor no se puede usar en medio teléfono del
  mercado. Tiene su prueba.
- **Una lectura sin datos devuelve `null` y no arrastra la anterior.** Si el sensor se
  cae a mitad de sesión eso tiene que verse; quedarse con el último valor bueno
  dejaría el obturador habilitado con el teléfono torcido, que es peor que no tener
  nivel.
- **El objetivo se recibe, no se supone que sea cero en los dos ejes.** Un teléfono
  apuntando a un producto sobre una mesa no está plano, y con el cero fijo el nivel
  nunca se pondría verde en el montaje real. **Queda por decidir quién fija ese
  objetivo**: una calibración explícita en el paso de preparación, o la inclinación de
  la primera toma aceptada. Es del flujo, no de estas funciones, y no bloquea nada de
  lo que sigue.

25 pruebas, entre ellas la de los **datos ruidosos** que pide el documento: con una
secuencia de temblor de pulso normal alrededor del objetivo, el dato crudo hace saltar
el indicador de estado y el suavizado se queda quieto en rango. **Las pruebas se
comprobaron mutando la implementación**: seis mutaciones —el suavizado anulado, la
lectura vacía arrastrando la anterior, el obturador bloqueado sin sensor, el arco
corto quitado, el límite de la tolerancia vuelto estricto y el eje dominante
invertido— cada una atrapada por los casos que le tocaban. `ng test` completo en verde
(394) y `ng lint` limpio.

**Falta el resto del asistente**: la cámara y la superposición de guía, el dibujo en
`<canvas>` que consume las funciones de recorte, y la carga con URL firmadas. Del lado
del backend, **no existe nada para sets de rotación**: el `AlmacenDeImagenes` y
`SolicitarSubidaDeImagenPrincipal` de la Fase 4 solo cubren la imagen principal, así
que crear un set, pedir N URL firmadas y publicarlo está por construirse entero.

### El backend de los sets de rotación

2026-09-06. La otra mitad de la fase, la que el asistente va a llamar.
`docs/03-api.md` documentaba cuatro endpoints desde el principio y **no existía
ninguno**: `SetRotacion` estaba en el dominio desde la Fase 1 pero nadie lo
escribía — lo leía la ficha y lo sembraba el sembrador, y nada más.

Quedaron los cinco pasos completos, por capas y con un commit cada una: el
dominio escribible (`abrir` vacío, `agregarFotograma`, `completar` contra lo
prometido), el puerto `RepositorioSetsRotacion` con su adaptador JPA, los cinco
casos de uso, y `POST /api/v1/admin/sets-rotacion` con `/subidas`, `/completar`,
`/publicar` y el `DELETE`. Protegidos por el rol `ADMIN` que ya existía.

**Tres decisiones, todas registradas en `ADR-0018`:**

- **El set es agregado propio, con su propio repositorio**, en vez de cuatro
  métodos más en `RepositorioProductos`: tiene id, tabla y ciclo de vida propios,
  y quien lo mueve es el asistente, no la edición del producto. Mismo criterio que
  `Envio` en `ADR-0013`.
- **Faltaba el endpoint de publicar**, y sin él nada de esto se ve: la ficha
  pública solo expone la rotación cuando está `PUBLICADO`, así que los cuatro
  endpoints documentados terminaban en un set `COMPLETO` invisible. Se agregó
  `POST /{id}/publicar` como paso aparte a propósito — entre completar y publicar
  está la revisión del set entero del paso 6 del asistente.
- **Lo que `completar` verifica de verdad**, escrito en vez de prometido. Contra
  el almacén real: que cada objeto exista, que no esté vacío y que pertenezca al
  set, y que llegaran todos los prometidos. Contra lo declarado por el cliente:
  cuadrado y de 1000 px. Lo que **no** puede verificar —que los bytes sean una
  imagen de ese tamaño— exigiría descargar y decodificar en el backend, que es
  justo lo que la subida directa evita; mismo riesgo aceptado que `ADR-0016`.
  `docs/03-api.md` decía "el tamaño y la proporción esperados" a secas y ahora
  dice cuál de las dos cosas es real.

**Un set incompleto se queda en BORRADOR entero**, no a medias: medio set
publicado es un visor roto. Y **un producto no termina con dos sets publicados** —
publicar sobre uno que ya lo está responde 409 y hay que borrar el anterior
primero. No hay reemplazo en caliente, así que el producto queda unos segundos sin
visor; es el precio de no tener que decidir cuál de dos se ve.

**Dos huecos del modelo, cerrados de paso:**

- **`docs/02-modelo-datos.md` listaba una columna `fotogramas` que la tabla nunca
  tuvo** (migración `V18`). Ahora significa cuántos se prometieron al abrir el set,
  y es lo que hace que `completar` pueda distinguir un set de 4 de uno de 8 al que
  se le perdieron cuatro subidas. De ahí sale también cuántas URL firmadas se
  emiten: el cliente no elige ni cuántas ni dónde escribe.
- **`MapeadorCatalogo` elegía el set del producto con `findFirst()` sin mirar el
  estado.** No mordía porque cada producto tenía un solo set; con estos endpoints,
  recapturar crea un segundo set en `BORRADOR` conviviendo con el publicado, y si
  `findFirst()` agarraba el borrador, el filtro de presentación lo descartaba y la
  ficha se quedaba sin visor teniendo uno bueno. Corregido con su prueba contra
  Postgres real, que siembra los dos sets a la vez.

Verificado con `gradlew.bat build` completo —las cinco capas, ArchUnit,
Testcontainers con Postgres real y los `@WebMvcTest`—. **Sin recorrido a mano
contra `bootRun` todavía**: el almacén real es Cloud Storage y este backend no
tiene llaves de GCS en desarrollo, así que la subida firmada de punta a punta
solo está probada contra el doble del puerto. Es lo mismo que ya pasaba con la
imagen principal de la Fase 4.

**Falta el resto del asistente en el frontend**: la cámara y la superposición de
guía, el dibujo en `<canvas>` que consume las funciones de recorte, y la pantalla
que encadena estos cinco endpoints.

### La cámara y la superposición de guía

2026-09-06. Los pasos 1 a 4 del flujo del asistente —preparación, permisos, captura
secuencial y revisión por toma— en `features/captura360/`. El procesamiento en
`<canvas>` y la subida son lo que queda.

**Todo lo que toca el dispositivo entra por un puerto**: `CAMARA`,
`SENSOR_ORIENTACION` y `PANTALLA_DESPIERTA` en `domain/`, con sus adaptadores en
`infrastructure/`. No es ceremonia — es lo que permite probar la pantalla entera
sin cámara, y sobre todo lo que obliga a que el camino degradado exista de verdad
y no solo de palabra, porque una prueba lo puede simular. Las once pruebas de la
pantalla corren sin `getUserMedia` en ninguna parte.

Dos diferencias de plataforma quedaron encerradas en sus adaptadores, donde no
hacen ruido: en Safari de iOS `DeviceOrientationEvent` expone `requestPermission()`
y hay que llamarlo desde un gesto, mientras que en el resto de navegadores esa
función no existe; y la cámara se pide con `facingMode: { ideal: 'environment' }`
y no `exact`, porque en un portátil sin cámara trasera `exact` falla en seco en
vez de dar la que hay.

**Las tres decisiones que se tomaron antes de escribir:**

- **La referencia del nivel la fija la primera toma.** El fotograma 0 decide la
  inclinación y los demás tienen que igualarla dentro de los 3°. Es la pregunta
  que había quedado abierta al construir el nivelador: la alternativa era una
  calibración explícita en la preparación, que es pedirle al operador que adivine.
  Mientras no hay referencia, cualquier inclinación está bien y la pantalla lo
  dice.
- **Cuadrícula, no silueta.** El documento decía "silueta o cuadrícula"; dibujar
  un tenis, un morral y un celular es trabajo de diseño que no le tocaba inventar
  a esta sesión. Van marco 1:1, tercios, marca de centro y el **fantasma del
  fotograma anterior**, que el propio documento llama la ayuda más útil de las
  tres.
- **Lo capturado vive en memoria.** Ocho fotos de dos megas no caben en
  `localStorage` y meter IndexedDB por esto es desproporcionado. La protección
  real contra perder el trabajo es subir cada fotograma apenas se acepta —eso
  llega con el paso de subida— y además protege de que se muera el teléfono, no
  solo de que se cierre la pestaña. **Hasta entonces, salir de la pantalla pierde
  las tomas**, y conviene saberlo.

**El nombre de cada toma sale del documento, no de la geometría.** Los cuatro
puntos cardinales llevan los nombres que fija `docs/10-captura-360.md` —frontal,
lateral derecho, posterior, lateral izquierdo— y entre ellos va el ángulo.
Deducir "derecho" o "izquierdo" del sentido de giro es de esas cosas que se
equivocan calladas y confunden a quien está capturando con el teléfono en la
mano.

El indicador de nivel **no distingue los tres estados por color**: cada uno lleva
su texto y su glifo, y el mensaje dice qué hacer —hacia dónde inclinar y cuántos
grados— en vez de limitarse a decir que algo está mal. Sobre una vista de cámara
el color no es fiable ni siquiera para quien lo ve.

La pantalla se alcanza con clics desde la lista de productos del panel, con su
scope de i18n propio (es/en) precargado en el `resolve` como cualquier otro dato
de la primera pantalla.

**Verificado** con `npm run verificar` completo: lint, 416 pruebas de Vitest,
`ng build` y `gradlew.bat build`. Las pruebas nuevas **se comprobaron mutando la
implementación**: el bloqueo del obturador, el fantasma de la toma anterior, la
referencia que fija la primera toma y la liberación de la imagen al repetir —cada
mutación la atrapó exactamente la prueba que le tocaba.

**Lo que no se verificó, y por qué:** nada de esto se probó en un navegador real
todavía. La cámara exige un dispositivo con cámara y contexto seguro, el sensor
de orientación exige un teléfono, y la extensión de automatización de Chrome de
esta máquina no llega a `localhost` (ya anotado en la Fase 4). **El asistente hay
que recorrerlo con un teléfono de verdad antes de darlo por bueno**: el gesto
táctil, el permiso de iOS y el comportamiento del nivel con un pulso real no los
puede confirmar ninguna prueba de Vitest.

### El procesamiento y la subida: el asistente completo

2026-09-06. El último tramo: procesar el set con `recorte-360`, subirlo por las URL
firmadas, revisarlo con el visor de siempre y publicarlo. Con esto el recorrido del
asistente existe de punta a punta.

**Un choque que apareció al planearlo, y que corrigió una decisión anterior.** Al
construir la cámara se decidió no persistir las tomas, con el argumento de que "no
perder el trabajo" se resolvería subiendo cada fotograma apenas se acepta. **Eso no
se puede hacer**: el factor de escala es común a todo el set y sale del rectángulo
más grande de *todos* los fotogramas, así que hasta que no está la última toma no se
puede procesar ninguna. Procesar sobre la marcha exigiría fijar la escala con la
primera toma, que es exactamente el defecto que `encuadreDelSet` existe para evitar.

Así que las tomas ahora **se guardan en IndexedDB** en cuanto se aceptan, y al volver
a la pantalla del mismo producto se ofrece continuar o empezar de nuevo. Lo guardado
se borra recién cuando el set está a salvo en el servidor: si la subida falla, las
tomas siguen ahí. IndexedDB a pelo, sin librería envolvente — son cinco operaciones y
una tabla de índices. Y que el disco rechace una toma no interrumpe nada: la captura
sigue en memoria y la pantalla avisa de que se quedó sin red de seguridad.

**El set se abre en el backend después de procesar, no antes.** Si el recorte falla
—fondo desparejo, producto tocando el borde—, no queda un `BORRADOR` huérfano en la
base de datos. Tiene su prueba.

**Dos desviaciones del documento, las dos ya corregidas en `docs/10-captura-360.md`:**

- **Sin respaldo JPEG.** El documento pedía WebP más JPEG, pero `/subidas` emite una
  key por fotograma: el respaldo exigiría 2N objetos y una columna más. Si algún día
  hace falta de verdad, se cambia primero el contrato de subida.
- **Sin Web Worker todavía.** Se procesa en el hilo principal, un fotograma a la vez
  y cediendo el turno entre uno y otro para que la barra de progreso se repinte.
  Pasarlo a un worker exige `OffscreenCanvas` —Safari solo desde 16.4— y vale la pena
  medirlo en un teléfono real antes de pagar esa complejidad.

Los motivos por los que el recorte puede fallar se traducen a **instrucciones**, no a
códigos: "en alguna toma el producto toca el borde del marco, aléjate un poco y repite
el set" en vez de `PRODUCTO_CORTADO`.

**Verificado** con `npm run verificar` completo: lint, **429 pruebas** de Vitest,
`ng build` y `gradlew.bat build`. El contrato tipado se regeneró contra el OpenAPI del
backend real corriendo (`bootRun` + PostgreSQL), no a mano. Las pruebas nuevas se
comprobaron **mutando la implementación**: el encuadre por fotograma en vez del común
del set, abrir el set antes de procesar, y la toma que no se guarda en disco — cada
mutación la atrapó la prueba que le tocaba.

**La Fase 5 no está cerrada**, y conviene ser exacto sobre por qué. Falta lo que la
regla de cierre exige y ninguna prueba puede dar:

1. **El recorrido completo en el navegador**, con un teléfono de verdad: cámara,
   permiso de sensor en iOS, gesto táctil y el nivel con un pulso real.
2. **La subida firmada de punta a punta contra Cloud Storage.** En desarrollo no hay
   llaves de GCS, así que `/subidas` y el `PUT` solo están probados contra dobles.
3. **El recorrido a mano de los endpoints del set contra `bootRun`**, que tampoco se
   hizo al construirlos.

Hasta que eso ocurra, lo construido está probado pero no visto funcionando.

### El recorrido a mano, hasta donde llega sin bucket

2026-09-06. Se hizo el pendiente 3 contra `bootRun` + PostgreSQL real, y se
encontró que **no era independiente del pendiente 2**: tres de los cinco
endpoints del set quedan verificados a mano, y los otros dos no pueden quedar
hasta que exista el bucket.

Verificado de punta a punta: `POST /api/v1/auth/sesion` (200, rol `ADMIN`),
`GET /api/v1/admin/productos` sobre los datos de siembra, `POST
/api/v1/admin/sets-rotacion` (201, `BORRADOR`) y `DELETE
/api/v1/admin/sets-rotacion/{id}` (204, y el segundo intento 404 con el problem
detail correcto, no un 500).

`POST /{id}/subidas` responde **500**: `IllegalStateException: Signing key was
not provided and could not be derived`. La credencial de GCS del `.env.local`
apuntaba a un archivo que no existe. De paso quedó comprobado algo que nadie
había mirado: **la aplicación arranca igual sin credenciales de GCS** —el bean
`Storage` de `ConfiguracionCatalogo` se construye sin problema— y el fallo
aparece recién al firmar. Es el comportamiento deseable, pero era una
suposición.

Sin bucket tampoco se puede pasar de ahí: `CompletarSetRotacion` verifica contra
el almacén real que cada objeto exista y pese más de cero, a propósito. Así que
el orden de los tres pendientes no es el que estaba escrito — **el bucket es el
camino crítico de los tres**, incluido el recorrido en el teléfono, que termina
subiendo el set.

Por eso se agregó `infra/dev/bucket-imagenes.mjs`: bucket, lectura pública,
CORS, cuenta de servicio y llave, idempotente. Es la única cosa de GCP que se
crea fuera de Terraform, y `infra/dev/README.md` explica por qué: montar con
Terraform un proyecto de dev exigiría primero un bucket de estado remoto para el
propio Terraform, y ese arranque en frío no se paga solo por un bucket y una
cuenta de servicio. La regla de producción no cambia.

### El hash que nunca cupo, y los pendientes 2 y 3 cerrados

2026-09-06, más tarde. Con el bucket de dev montado, el recorrido llegó hasta
`/completar` y reventó con un 500: `value too long for type character
varying(80)`. `imagen_producto.hash` guardaba la **key del objeto** (~99
caracteres) en una columna dimensionada para un SHA-256. **Nunca funcionó contra
Cloud Storage**, ni en el set de rotación ni en la imagen principal del panel
(`ConfirmarImagenPrincipal` hacía lo mismo desde la Fase 4). Las pruebas no lo
veían: el doble del almacén acepta cualquier key y usaban keys cortas.

Ensanchar la columna habría enterrado el problema. `docs/02-modelo-datos.md` dice
que `hash` es del contenido, para detectar recargas duplicadas, y con la key ahí
esa detección no podía funcionar nunca, porque cada key es única por
construcción. Así que el hash volvió a ser lo que el modelo dice: lo calcula el
navegador con `crypto.subtle` sobre los bytes que sube, `HashContenido` (objeto
de valor, `domain/compartido`) exige 64 hexadecimales, y `V19` lo repite como
`check` del esquema. El detalle y lo que se descartó, en `ADR-0019`.

De rebote apareció otra cosa: `TokensJwtTest` fallaba una de cada dieciséis
corridas desde siempre. Cambiaba el último carácter del token, que en una firma
HS256 solo aporta cuatro bits significativos — el token "manipulado" a veces era
byte por byte el mismo. Esa prueba no probaba nada; ahora cambia el cuerpo por el
de otro usuario conservando la firma.

**Pendientes 2 y 3, cerrados** (`npm run verificar` completo: lint, 433 pruebas,
`ng build` y `gradlew.bat build`). El recorrido entero contra el bucket real,
paso por paso: sesión `ADMIN`, abrir el set (201, `BORRADOR`), ocho URL firmadas
V4 (201), ocho `PUT` directos a `storage.googleapis.com`, `/completar` (200,
`COMPLETO`), `/publicar` (200, `PUBLICADO`), y `GET /api/v1/productos/{slug}`
sirviendo `rotacion` con los ocho fotogramas apuntando al bucket — comprobado
además que el objeto se lee público (200, `image/webp`). Al terminar se borró el
set de prueba y sus objetos: el catálogo de dev quedó como estaba.

**Queda solo el pendiente 1**, el recorrido en un teléfono real, que necesita el
túnel HTTPS y su origen agregado al CORS del bucket (`infra/dev/README.md`).

### La Fase 5, cerrada: el recorrido en el teléfono

2026-09-07. El pendiente 1, hecho en un iPhone y en un Android sobre un túnel de
Cloudflare (`cloudflared tunnel --url http://localhost:4200`; `proxy.conf.json`
manda `/api` al backend, así que un solo túnel sirve app y API sin contenido
mixto). La cuadrícula de guía y el nivelador se comportaron bien en los dos, y el
permiso del sensor de orientación de iOS —el caso estricto, que exige gesto del
usuario y certificado confiable— se concedió sin pelear. El procedimiento quedó
en `apps/web/README.md`, que es donde `docs/07-infra-gcp.md` decía desde hace
fases que estaba, y no estaba.

El dev server corre con `ng serve --allowed-hosts` para aceptar el host del
túnel. `angular.json` no se tocó: `security.allowedHosts` exige el host exacto
—el comodín `.trycloudflare.com` no le sirve— y un host efímero no tiene por qué
quedar versionado.

**Dos defectos que solo aparecen al llegar por un enlace**, encontrados así y
corregidos:

1. **Se veía parpadear la pantalla protegida.** `adminGuard` se abstiene en el
   servidor —con razón: el SSR no reenvía la cookie de refresco, así que no tiene
   con qué decidir—, de modo que el servidor pintaba la pantalla de captura y un
   instante después el cliente redirigía al login. Ahora `/admin` se renderiza
   solo en el cliente (`RenderMode.Client`), que además no cuesta nada: no
   necesita SEO y sus datos ya venían del cliente. La protección real nunca
   estuvo ahí — el backend exige el rol en cada endpoint.
2. **El enlace se perdía.** Tras iniciar sesión se caía siempre en el panel y
   había que volver a buscar el enlace. `adminGuard` ahora anota a dónde ibas
   (`?destino=`) y el login vuelve ahí. Solo acepta rutas relativas de este sitio
   dentro de `/admin`: un `destino` viene de la URL, o sea del usuario, y sin ese
   filtro un enlace preparado convertiría el formulario en un salto a otro sitio
   con la credencial recién escrita.

**Fase 5 completa.** El asistente de captura funciona de punta a punta en un
teléfono real contra Cloud Storage real: cámara, guía, nivel, procesado, subida
firmada, revisión y publicación.

### Borrar un set borra sus objetos

2026-09-06, cerrando el día. `DELETE /api/v1/admin/sets-rotacion/{id}` borraba la
fila y dejaba los objetos en el bucket. No era un olvido: el javadoc lo
justificaba con que "el bucket tiene versionado y borrar bytes es irreversible".
Solo que **el bucket de dev no tenía versionado**, así que la red en la que se
apoyaba esa decisión no existía, y mientras tanto el espacio no se reclamaba
nunca.

Ahora se borra, y la red se montó de verdad: versionado de objetos en el bucket
más una regla de ciclo de vida que expira las versiones no vigentes a los 30 días
(`TODO(negocio)`: ese plazo es un valor de arranque, no una decisión tomada). Sin
la regla, las versiones no vigentes se acumulan y no se habría reclamado nada.

Se borra **por prefijo**, no recorriendo los fotogramas conocidos: un set que
murió a medio subir dejó objetos que nunca fueron una fila, y son justamente los
que más falta hace reclamar. Y los objetos se borran **antes** que la fila: al
revés, un fallo a mitad deja los objetos huérfanos para siempre, porque
reintentar responde 404 y ya nadie sabe qué prefijo limpiar.

Un detalle que solo apareció probando contra el bucket real: la primera versión
usaba `blob.delete()` sobre los objetos que devuelve el listado, y **eso borra la
generación concreta, saltándose el versionado** — la prueba mostró cero versiones
recuperables. Borrando por nombre, sin generación, quedan las cuatro. Está en
`docs/07-infra-gcp.md` para que no se repita.

### Reemplazar la imagen principal también reclama su objeto

2026-09-07. El mismo defecto que en los sets, en el otro caso de uso: cada
reemplazo de la imagen principal dejaba pagando el objeto anterior. Se borra por
prefijo —`productos/{id}/principal-`— **salvo la key recién subida**, así que se
lleva también lo que quedó de subidas que nunca se confirmaron. El prefijo llega
hasta `principal-` a propósito: con solo el id del producto, reemplazar la imagen
se llevaría por delante los fotogramas del visor 360 (tiene su prueba).

**El orden es el contrario al del set, y no por descuido.** Un set se va entero,
así que allá los objetos se borran antes que la fila. Aquí la fila se
*reemplaza*: borrar antes y que falle el guardado dejaría al producto apuntando a
un objeto inexistente, una imagen rota en una ficha viva. Se guarda primero y se
limpia después. El precio es que una limpieza fallida deja objetos sin reclamar,
así que no se traga en silencio —`ConfirmacionDeImagenPrincipal` lo reporta y el
controlador lo registra como error— y tampoco tumba una confirmación que ya se
guardó. Se cura sola: el siguiente reemplazo borra todo lo que haya quedado bajo
el prefijo.

Verificado contra el bucket real subiendo dos veces la principal del mismo
producto: la primera vuelta borra 0 objetos, la segunda borra 1, en el bucket
queda solo la vigente con la anterior como versión recuperable, y la ficha sirve
la nueva. Las pruebas se comprobaron con dos mutaciones —no conservar la key
nueva, y recortar el prefijo a solo el id del producto—; cada una la atrapó la
prueba que le tocaba.

### El cierre formal de la fase, y el enlace que faltaba

2026-09-07. Al recorrer la regla de cierre punto por punto apareció que **la
Fase 5 no cumplía el punto 2**, aunque la pantalla nueva de la fase sí estaba
enlazada: el enlace roto estaba una casilla más arriba.

`/admin/productos/{id}/captura-360` se alcanza desde la lista de productos del
panel, correcto desde el día que se construyó. Pero **al panel no se llegaba con
clics**. El único enlace a `/admin` en toda la aplicación vive en el encabezado
detrás de `@if (sesion.esAdmin())`, o sea que aparece cuando ya hay sesión de
`ADMIN` — que es justo lo que no se puede conseguir sin entrar. Y la otra puerta
no servía: `IniciarSesionClientePage` **rechaza a propósito** cualquier sesión
que no sea `CLIENTE`, así que entrar con el correo del admin por
`/cuenta/iniciar-sesion` cierra la sesión y muestra un error. La única forma de
llegar era teclear `/admin`.

Es la misma deuda que las fases 3 y 4 ya habían pagado una vez, con una vuelta
de tuerca: esta vez la pantalla nueva **sí** estaba enlazada, y el recorrido
fallaba igual. Verificar el enlace de la pantalla nueva no basta; hay que
recorrer la cadena entera desde la portada, que es literalmente lo que la regla
dice y lo que no se estaba haciendo.

**El arreglo**: "Panel administrativo" en el pie, visible siempre y en los dos
idiomas, apuntando a `/admin` y no al formulario — con sesión cae en el panel, y
sin ella `adminGuard` redirige al ingreso anotando el destino, que ejercita de
paso el arreglo del `?destino=`. Va siempre visible porque un enlace que solo
aparece con sesión de `ADMIN` no sirve para llegar a iniciarla. No es un secreto
que se filtre: la protección real es del backend, que exige el rol en cada
endpoint. Su prueba se comprobó mutando el destino del enlace.

**El recorrido completo, con clics reales** contra `ng serve` + `bootRun` +
PostgreSQL: portada → tenis (visor 360 girando: un arrastre de 160 px avanza
tres fotogramas, "Fotograma 4 de 8", la pista se va con la primera interacción)
→ pie → Panel administrativo → Productos → Capturar 360, con el paso 1 del
asistente pintando su lista de preparación y el selector de 4/8/16 fotogramas.
Sin sesión, `/admin/productos` redirige a
`/admin/iniciar-sesion?destino=%2Fes%2Fadmin%2Fproductos` **sin que parpadee la
pantalla protegida** — el `RenderMode.Client` haciendo lo suyo. En consola, solo
el `NG02956` ya conocido.

**Y una corrección sobre la herramienta, que venía arrastrándose desde la Fase
4:** la extensión de automatización de Chrome **sí llega a `localhost`** en esta
máquina. Las notas de las fases 4 y 5 que dicen lo contrario quedan como estaban
—describen lo que se creía entonces—, pero la limitación no existe y no hay que
seguir asumiéndola. Lo que sigue necesitando un teléfono de verdad es lo que
siempre necesitó uno: cámara, sensor de orientación y gesto táctil.

**Documentos corregidos en este cierre**, decisiones que se habían tomado
durante la fase y vivían solo aquí o en un comentario del código:

- `apps/web/README.md` **decía una mentira que rompía el procedimiento**:
  afirmaba que `angular.json` ya traía `.trycloudflare.com` en
  `security.allowedHosts`. No lo trae —dice `localhost`— y este mismo documento
  explicaba por qué no debía traerlo. Ahora el procedimiento lleva la bandera
  `ng serve --allowed-hosts`, que es booleana y acepta cualquier host.
- `docs/01-arquitectura.md`: qué se renderiza en el servidor y por qué `/admin`
  no.
- `docs/06-testing.md`: **cómo se comprueba que una prueba prueba algo**
  (mutar la implementación), con los tres casos de la fase en que una prueba
  verde no probaba nada; y los puertos de dispositivo como estrategia, con la
  lista de lo que solo da un teléfono.
- `docs/08-seguridad-legal.md`: el filtro de redirect abierto del `?destino=`.
- `apps/web/CLAUDE.md`: `urlPreferida` como regla única de formato, el valor por
  omisión de `ts-galeria.prioritaria`, la congelación de las entradas de
  `NgOptimizedImage`, y **no usar la identidad de un objeto como señal de
  cambio** frente a una revalidación en segundo plano.

**Fase 5 cerrada** (`npm run verificar`: lint limpio, 439 pruebas de Vitest,
`ng build` y `gradlew.bat build`), ahora sí con las tres condiciones de la regla
de cierre cumplidas y verificadas.

**Lo que queda abierto**, anotado para que nadie lo descubra otra vez:

- ~~**De la revisión adversarial del visor**, cuatro hallazgos vivos: un
  fotograma roto se salta en silencio (ni log ni señal); la pista sale en cada
  visita en vez de "la primera vez" como pide `docs/10-captura-360.md`; sin
  captura de puntero, soltar fuera del marco deja `arrastrando` en verdadero; y
  `guardarSetRotacion` del sembrador no tiene prueba.~~ **Cerrados**
  (2026-09-07): el fotograma roto queda registrado en consola con su índice y su
  URL —en pantalla no se muestra nada, y eso es una decisión: qué decirle a quien
  está mirando un producto cuando falta una foto es de producto, no de este
  componente—; la pista sale una sola vez por navegador, con la marca en
  `localStorage` y los dos accesos al almacén tolerando que no haya almacén, y
  de paso deja de pintarla el servidor; y el set sembrado tiene cinco pruebas
  contra Postgres real. El cuarto hallazgo **estaba mal anotado**:
  `setPointerCapture` vive en el componente desde su primer commit (`3a95f01`,
  comprobado con `git log -S`), así que soltar fuera del marco nunca dejó
  `arrastrando` en verdadero.
- ~~**Tercera copia del host literal de imágenes** en `SembradorCatalogo`, contra
  la regla dura #5. Perfil `local`, pero ya son tres.~~ **Cerrada** (2026-09-07):
  el host es una constante y la URL la arma `urlDeSiembra(semilla, ancho, alto)`.
  Sigue siendo un literal y no una variable de entorno **a propósito**: la forma
  de la ruta es la API de picsum.photos, así que hacer configurable solo el host
  no dejaría apuntar la siembra a otro sitio — cumplir la regla en el papel. El
  razonamiento quedó en el código, no solo aquí.
- **`NG02956`**: sin `preconnect` al host de imágenes. Sigue esperando el host
  real, porque el arreglo es una URL literal en el `<head>` que la regla dura #5
  prohíbe.
- **El presupuesto de bundle** se pasa por 3,54 kB de los 600 kB. No lo causó
  esta fase (comprobado contra `main` en su momento): el código del visor y del
  asistente va en trozos perezosos.
- **`TODO(negocio)` `DIAS_RETENCION_VERSIONES_IMAGEN`**: los 30 días de
  expiración de versiones no vigentes del bucket son un valor de arranque, no una
  decisión tomada.
- **`TODO(negocio)`** de la copia del hero y su imagen 4:3 de 1200x900, heredado
  de la Fase 4.
- ~~**Cosmético, en la lista de productos del panel**: "Editar" y "Capturar 360"
  se pintan pegados ("EditarCapturar 360"), sin separación.~~ Cerrado en
  `3d3b915`, durante el tramo de interfaz posterior a la fase.
- **El carrito sigue sin vencer** (Fase 2) y `Playwright` sigue sin existir,
  aunque `docs/06-testing.md` lo nombra como la herramienta de los recorridos
  completos. Los dos son de la Fase 6.

## Fase 6. Cierre para publicar

Textos definitivos en los dos idiomas, políticas legales revisadas por abogado,
SEO con sitemap, hreflang y datos estructurados de producto, auditoría de
accesibilidad, Lighthouse con el visor 360 activo, respaldo restaurado de prueba,
y el DNS movido con el cuidado de `docs/07-infra-gcp.md`.

### Bloque legal, deudas arrastradas y accesibilidad (2026-09-08)

Tres de los cuatro frentes de la fase, en doce commits. Queda el SEO técnico
(sitemap, hreflang, datos estructurados), que no se tocó.

**Autorización de datos, de punta a punta.** Agregado `AutorizacionDatos` en
`domain/legal`, tabla propia `autorizacion_datos` (`V20`), y exigida en el
registro y en la creación del pedido. Decisiones que quedaron en el código:

- **La versión del texto la fija el servidor** (`POLITICA_DATOS_VERSION`), nunca
  el cliente: si el navegador declarara qué versión aceptó, bastaría manipular la
  petición para dejar constancia de una aceptación que nunca ocurrió (regla dura
  #7). Va acoplada al texto, que vive en los JSON de Transloco: cambiar el texto
  y cambiar la fecha son el mismo commit.
- **Tabla propia y sin FK a usuario**, por la misma razón que `linea_carrito` no
  la tiene a variante: quien compra sin cuenta también autoriza, y una FK
  convertiría un dato de auditoría en un error de Postgres capaz de tumbar la
  compra que intenta dejar constancia.
- **`direccion_ip` es `text`**, aunque un IPv6 quepa en 45: viene de una cabecera
  de proxy, y una columna estrecha con un valor de fuera ya reventó una vez
  (`ADR-0019`).
- **La guarda va primero.** En el registro, comprobar el correo duplicado antes
  que la autorización respondía 409 a quien no consintió nada — le confirmaba que
  ese correo tiene cuenta. En el pedido, comprobarla al final habría dejado
  existencias reservadas y un consecutivo quemado.

**Tres documentos legales**, en español e inglés, enlazados en el pie. Redactados
verificando la norma vigente y no de memoria: retracto de cinco días hábiles y
reintegro en quince días calendario (el plazo que introdujo la **Ley 2439 de
2024**, que redujo el anterior), entrega supletiva de treinta días calendario, y
**Ley 1581 de 2012 sin reforma** — el proyecto radicado en 2025 sigue en trámite.
Sin banner de cookies **a propósito**: no hay analítica ni píxeles que bloquear.

**El carrito ya vence** (deuda desde la Fase 2). Se expira por **última
actividad** y no por creación: un carrito usado cada semana lleva meses creado y
no es basura. `TareaPurgaCarritos` es la segunda tarea programada del proyecto,
con el patrón de `TareaConciliacionWompi`.

**Playwright existe** (`npm run e2e`), con dos recorridos contra servicios
reales. Usa el Chrome instalado porque la descarga de Chromium falla en esta
máquina.

**Tres defectos reales encontrados, ninguno previsto en el plan:**

1. **El NIT del pie tenía mal el dígito de verificación** desde que se escribió
   (`-1` en vez de `-9`). Calculado con el algoritmo de la DIAN, no elegido entre
   dos opciones. Es información obligatoria del proveedor (Ley 1480).
2. **Jackson 3 no rellena los componentes que falten de un `record`**: un cuerpo
   sin `autorizaDatos` no cae en `false`, revienta la deserialización entera. El
   resultado es igual de seguro pero por otra razón, y razonar sobre "el
   primitivo protege por omisión" habría sido un error la próxima vez. Anotado en
   `apps/api/CLAUDE.md`.
3. **Sin marcar la casilla, «Continuar» no hacía nada y no decía por qué.** Lo
   encontró el recorrido de Playwright; las pruebas de Vitest comprobaban que no
   se guardara el borrador, que es cierto, pero no que se informara al comprador.

**Accesibilidad.** `axe` pasó de cinco archivos a doce (las tres legales,
registro, inicio de sesión, método de pago y estado del pedido). Las siete nuevas
pasaron a la primera, así que se comprobó que comprueban algo: una imagen sin
`alt` metida a propósito las hace fallar. `npm run contrastes` en verde, 0 pares
por debajo del mínimo. Foco verificado en el navegador real —`:focus-visible`
coincide con teclado y pinta el anillo de 3 px—, que es lo que jsdom no da.

**Hallazgos de coherencia entre el texto legal y el sistema**, la parte que la
skill de textos legales llama Fase 4 y que casi nadie hace. **Ninguno es un bug:
son promesas del documento que la operación todavía no puede cumplir**, y hay que
resolverlas antes de abrir:

- **No existe ningún flujo de retracto, reembolso ni reversión del pago.** El
  grafo de `EstadoPedido` no los contempla y no hay caso de uso que devuelva
  dinero. Hoy se atenderían a mano. Es la deuda más grande que deja este bloque.
- **La garantía tampoco tiene flujo**: se atiende por correo.
- **El plazo de entrega real no está decidido**, así que el texto lleva el
  supletivo legal de 30 días calendario marcado como dato pendiente.
- **Seis datos de negocio marcados con `[[ ]]`** en los textos: transportadora,
  proveedor de correo transaccional, plazo de entrega real, quién paga el flete
  de la devolución, garantía de celulares y horario de atención.
- **Los borradores los revisa un abogado antes de publicar.**

**Lo que este bloque no hizo:** SEO técnico (sitemap, hreflang, datos
estructurados) y **Lighthouse**, que sigue sin correrse — necesita DevTools sobre
un `ng build` servido en producción.

### SEO técnico y la nota de idioma (2026-09-08)

El frente que el bloque anterior no tocó, en tres partes, más un hallazgo legal
que salió en medio.

**Metadatos por página.** No había **ninguna** llamada a `Title` ni a `Meta` en
todo `apps/web`: las quince pantallas compartían el `<title>Tecno Sport</title>`
estático de `index.html`, sin `description`, sin canónico y sin Open Graph. Eso
pesaba más para el posicionamiento que el sitemap que se iba a construir.

`core/seo/` lo resuelve con un servicio central alimentado por `data.seo` de cada
ruta, y un hook (`usarMetadatos`) para la única pantalla cuyo título sale de datos
—la ficha—. Decisiones que quedaron en el código:

- **`indexable` por omisión es `false`**, con prueba. Una pantalla nueva que se
  olvide de declararlo se queda fuera del índice, que se arregla con un commit;
  al revés, el descuido publica el carrito o el panel y eso se arregla pidiéndole
  a Google que desindexe.
- **El canónico poda los parámetros de consulta.** La única pantalla que los usa
  es la rejilla, y ahí `?categoria=`, `?orden=` y `?cursor=` no son páginas
  distintas sino recortes de la misma; declararlas todas canónicas sería pedir
  que se indexe una combinación por cada filtro.
- **`hreflang` sale de la misma función que el selector de idioma del
  encabezado** (`urlEnOtroIdioma`). Lo que se le promete al rastreador tiene que
  ser exactamente adonde va el visitante que pulsa el selector.
- **El inicializador de entorno, no el constructor de `App`**: corre antes de la
  primera navegación, que es la única que ve un rastreador sin JavaScript.

**Tres defectos encontrados, dos de ellos propios:**

1. **La ruta de la ficha se marcó `indexable: true` y estaba mal.** Un slug
   inventado no da 404: el router lo acepta, la consulta falla y la página
   responde 200 con "No encontramos este producto". Eso es un *soft 404*, y así
   habría entrado al índice una URL basura por cada enlace roto. Ahora la ruta es
   `noindex` y solo la ficha que **sí** cargó su producto se declara indexable.
2. **La siembra tiene `descripcion` vacía** —la columna es `not null default ''`,
   así que es un estado legítimo— y esas fichas salían sin `meta description`. Se
   añadió un respaldo que no inventa nada: plantilla traducida con el nombre y la
   marca reales.
3. **`comun.traduccion_cortesia` existía solo en inglés y ninguna plantilla la
   mostraba.** Lo destapó la prueba de paridad de claves del scope `legales`, que
   faltaba desde que el scope se creó. No era un detalle de traducción: es la nota
   de que **la versión en castellano es la que rige**, y sin ella quien compra
   navegando en inglés aceptaba un documento cuyo original nunca vio. Verificado
   contra la fuente oficial: **Ley 1480 de 2011, art. 23** ("la información mínima
   debe estar en castellano") y **art. 37.1** ("en los contratos se utilizará el
   idioma castellano"), con las condiciones que no cumplan declaradas ineficaces.
   Ahora se muestra en los dos idiomas, diciendo lo que corresponde en cada uno.
   `autorizacion_datos` no guarda el idioma de lectura y **no hace falta**: si la
   versión española rige siempre, el idioma en que se leyó es irrelevante.

**Sitemap y robots.** `GET /api/v1/mapa-del-sitio` en el backend —puerto propio y
estrecho, no un método más en `RepositorioProductos`— y `/sitemap.xml` +
`/robots.txt` en el servidor Express, antes del manejador de Angular. El endpoint
devuelve **slugs, no URL**: el backend no sabe, ni tiene por qué, que la ficha
vive en `/{idioma}/productos/{slug}`, y el día de la app móvil esa ruta no
significará nada.

- **`robots.txt` solo prohíbe `/admin`**, y la estrechez es deliberada. El
  carrito, el checkout y la cuenta ya salen con `noindex`; añadirles `Disallow`
  impediría que el rastreador **lea** ese `noindex` y podrían acabar indexadas
  igual, sin descripción y sin forma de sacarlas. Para no aparecer hay que dejar
  entrar. `/admin` es la excepción porque se renderiza en cliente y su HTML no
  lleva el `noindex`.
- **`lastmod` solo donde es verdad**: las tres legales, desde
  `legales.comun.version`. La portada y la rejilla no lo llevan, porque poner la
  fecha del despliegue le enseña a Google que nuestro `lastmod` no significa nada.
- **Un solo sitemap, no uno por idioma.** `docs/05-i18n.md` decía lo contrario y
  se corrigió: el formato pide una entrada por versión con sus alternativas.

**Datos estructurados.** `Product` con `AggregateOffer` y `BreadcrumbList` en la
ficha; `Organization` y `WebSite` en la portada. Los datos del negocio salen de
las claves del pie —las que la ley ya obliga a publicar— y no de una segunda
copia. `openingHours` **no se emite**: el horario de atención sigue sin decidirse,
y Google lo muestra como si fuera cierto. El escapado de `<` al serializar no es
cosmético: la descripción de un producto la escribe el panel, y un `</script>` ahí
cerraría la etiqueta en el HTML del SSR.

**Lo verificado en el navegador y no solo en jsdom**, que es lo que las pruebas no
alcanzan: que el `<head>` completo —título, canónico, las tres alternativas y los
bloques de JSON-LD— salga **ya escrito en el HTML del SSR**, incluida la ficha,
cuyos metadatos los pone un `effect` con datos del resolver; que el sitemap
responda con 18 URL y XML bien formado, validado con un parser de verdad; y que
**con la API caída siga respondiendo 200** con las páginas fijas, apagando la API
para comprobarlo en vez de confiar en el `catch`.

**Lighthouse, por fin** (`lighthouse` como devDependency de `apps/web`), sobre el
build de producción servido, en móvil y con estrangulamiento. Tres pantallas:

| | rendimiento | accesibilidad | buenas prácticas | SEO |
|---|---|---|---|---|
| portada | 67 | 100 | 96 | 100 |
| ficha | 58 | 100 | 96 | 100 |
| legales | 66 | 100 | 96 | 100 |

**La primera corrida fue inválida y conviene saber por qué**, porque le va a pasar
a cualquiera que sirva el build en local. La ficha dio SEO 61 y salió
`noindex,nofollow`: el respaldo de su ruta, o sea que el producto no había
cargado. En el navegador `baseUrl()` es relativa a propósito —en producción el
balanceador enruta `/api` al backend en el mismo dominio— pero **el servidor SSR
construido no hace ese proxy; solo lo hace `ng serve` con `proxy.conf.json`**. Así
que tras hidratar, la consulta del producto moría y la pantalla se quedaba con los
metadatos del caso "producto no encontrado". No es un fallo de producción, pero
invalida la medición: hay que poner un proxy delante que mande `/api` al backend
y el resto al servidor SSR. Las cifras de arriba son las de esa segunda corrida.

**Lo que las cifras dicen y lo que no.** El SEO y la accesibilidad son sólidos y
se pueden dar por buenos. **El rendimiento todavía no se puede juzgar**: en la
ficha, 16 de las 51 peticiones van a `picsum.photos` —el host de imágenes de la
siembra—, incluidos los ocho fotogramas de rotación a 1000×1000, y eso es lo que
lleva su LCP a 10,2 s. Con el bucket real esa cifra no significa lo mismo. **Hay
que repetir la medición cuando las imágenes salgan de GCS.**

Dos hallazgos que sí son reales e independientes de la siembra:

- **JavaScript sin usar**: 186 KB de un chunk de 352 KB en la ficha, ~2 s de
  ahorro estimado. Es la única oportunidad que Lighthouse reporta por encima de
  100 ms en las dos pantallas.
- **Buenas prácticas 96 por un error de consola en toda visita anónima**:
  `/api/v1/auth/refresco` responde 401 cuando no hay sesión, y el navegador lo
  registra como error. Funcionalmente es correcto —no hay cookie de refresco que
  usar— pero deja ruido en la consola de todos los visitantes y Lighthouse lo
  cuenta.

### Las dos deudas de esa medición, cerradas (2026-09-08)

**El 401 en toda visita anónima.** `POST /api/v1/auth/refresco` responde ahora
**204** cuando no llega la cookie, y sigue en 401 cuando llega una que no sirve
—basura, vencida, ya usada—. La distinción es la que importa: el frontend
pregunta en cada arranque **porque la cookie es `HttpOnly` y no puede saberlo de
otro modo**, así que quien nunca inició sesión no está fallando la
autenticación. Con 401, el navegador de cada visitante registraba un error en su
consola en cada visita. Es la primera anotación de springdoc del proyecto
(`@ApiResponse`): el 204 no se infiere del `ResponseEntity<SesionRespuesta>` y
sin declararlo no llegaba al OpenAPI ni, por tanto, a `tipos.ts`.

**Un defecto de paso, encontrado al escribir la prueba del 500.** El adaptador
decidía si la respuesta había fallado mirando `error`, que openapi-fetch solo
rellena cuando el fallo trae cuerpo JSON. Un 500 vacío o en HTML —lo que
devuelve un balanceador— se colaba hasta `aSesion(undefined)` y el llamador
recibía un `TypeError` en vez del mensaje de error. Ahora mira `response.ok`.
El mismo patrón está en `iniciarSesion`, dos líneas más arriba, y **sigue ahí**:
arreglarlo exige decidir qué se le dice al comprador cuando el servidor falla,
que no es "correo o clave incorrectos".

**Los 186 KB de JavaScript sin usar no eran nuestros, y conviene corregir el
apunte anterior.** Construyendo con `--source-map` y atribuyendo los bytes de
cada chunk a su paquete de origen (decodificando los `mappings`, sin instalar
nada), el chunk de 352 KB que Lighthouse señala en la ficha resulta ser
**`@angular/core` al 87,9 %**, con rxjs y Transloco detrás. Lo "sin usar" es
código del framework que no se ejecuta en la carga inicial —hidratación,
`@defer`, i18n en tiempo de ejecución— y no sale de ahí sin renunciar a
decisiones de arquitectura. **Esa deuda, como estaba escrita, no era
accionable.**

Lo que sí lo era estaba en el paquete **inicial** (`main`, 156 KB), o sea en
todas las pantallas, y Lighthouse no lo reporta porque cada pieza queda bajo su
umbral:

| | en `main` | qué se hizo |
|---|---|---|
| `@angular/forms` | 38,6 kB | **fuera**, ver abajo |
| `@tanstack/query-core` | 36,1 kB | se queda: lo usa toda pantalla con datos |
| `tailwind-merge` | 29,2 kB | se queda, y con motivo: el comentario de `cn.ts` documenta dos bugs reales que resuelve, uno de ellos silencioso |

**`@angular/forms` entraba por el encabezado.** `TsSelect` implementaba
`ControlValueAccessor`, y quien lo importa siempre son los selectores de idioma
y de tema — dos `<select>` de dos y tres opciones que arrastraban el motor de
formularios a la portada, a la rejilla y a la ficha, que no tienen ni un
formulario. El puente pasó a una directiva aparte, `TsSelectControl`, que es lo
que hace el propio framework con sus accesores; el componente quedó con `valor`
(modelo escribible) y `cambio`. **`cambio` se emite solo en la elección del
usuario y no en `writeValue`**, o un formulario quedaría sucio sin que nadie lo
toque — verificado en el navegador: el select que se tocó queda `dirty` y
`touched`, el de al lado sigue intacto.

Grafo inicial de JavaScript: **630,9 kB -> 590,7 kB**. Lo demás se verificó
donde jsdom no llega: el tema cambia con teclado y sobrevive a recargar, el
idioma navega y el selector sigue al botón de atrás, y los filtros del catálogo
conservan su valor al recargar con la URL filtrada — que es el caso de
"opciones que llegan después del valor" en producción.

**Sigue pendiente repetir Lighthouse** cuando las imágenes salgan de GCS: el
rendimiento medido no significa nada mientras 16 de las 51 peticiones de la
ficha vayan a `picsum.photos`.

### El arnés de Lighthouse, y lo que la medición nueva confirmó (2026-09-09)

`lighthouse` era dependencia de `apps/web` pero **no había ningún script que lo
corriera**: la medición de la Fase 6 se hizo a mano, incluido el proxy que la hizo
válida, y repetirla significaba reconstruir de memoria una trampa ya documentada.
`npm run lighthouse` (`tools/medir-lighthouse.mjs`) levanta el build de
producción, el servidor SSR y **el proxy que manda `/api` al backend**, y mide las
tres pantallas.

Lo que lo convierte en arnés y no en script son dos guardas que fallan **antes**
de medir, en vez de dejar salir cifras sin sentido: que el catálogo responda a
través del proxy, y que la ficha no salga `noindex`. Comprobado que disparan
apuntando el proxy a un puerto muerto — el fallo exacto de la primera corrida.

| | rendimiento | accesibilidad | buenas prácticas | SEO |
|---|---|---|---|---|
| portada | 69–70 | 100 | **100** | 100 |
| ficha | 62 | 100 | **100** | 100 |
| legales | 70–71 | 100 | **100** | 100 |

**Buenas prácticas pasó de 96 a 100**, y eso es una confirmación medida y no
supuesta: el 96 venía del error de consola que dejaba `POST /auth/refresco` con
401 en toda visita anónima, y ese arreglo (ahora 204) se dio por bueno sin volver
a medir. El rendimiento sube un par de puntos y **sigue sin significar nada**
mientras las imágenes vengan de `picsum.photos`.


### El bloque de infraestructura, que no tenía fase (2026-09-08)

La Fase 6 enumeraba cuatro frentes —textos, legales, SEO, accesibilidad— y
ninguno era el despliegue. El pipeline y Terraform estaban descritos en
`docs/07-infra-gcp.md` sin fase asignada, así que en la práctica quedaron fuera
del plan: **no había `.github/`, ni un solo archivo `.tf`, y los diecisiete
pull requests que se habían mezclado entraron sin una sola comprobación
automática**. `infra/README.md` describía cinco módulos de Terraform que no
existen. Se le da fase: es el bloque que le falta a la Fase 6, en cuatro etapas.

#### Etapa 1, cerrada: integración continua

`.github/workflows/verificar.yml`, dos trabajos en paralelo en cada pull request
y en cada merge a `main`. Medido en la primera corrida real: **web 82 s, api
4 m 11 s**.

- **Los dos ejecutan `tools/verificar.mjs`**, el mismo archivo que corre en
  local, con `--solo-web` o `--solo-api`. Podrían haber sido dos listas de
  comandos escritas en el YAML, y ese es justo el error: el día que se agregue
  un paso al script, esa lista no se enteraría y CI dejaría de significar lo
  mismo que la verificación de quien programa.
- **Sin filtros por ruta, y eso se aparta de `docs/07-infra-gcp.md`**, que los
  pide. Con `paths:`, el trabajo que no aplica no se salta: se queda *pendiente
  para siempre*, y una comprobación obligatoria pendiente bloquea el merge sin
  decir por qué. El repositorio es público —los minutos no se cobran— y los dos
  trabajos corren en paralelo. Si algún día molesta la espera, se resuelve con
  un trabajo que decida por ruta y reporte éxito cuando no aplica.
- **Se comprobó que dispara**, que es la única forma de creerle a un guardián:
  un commit temporal con una dependencia invertida en `carrito.store.ts` y una
  aserción falsa en `AutenticacionControladorTest`. El trabajo `web` murió en un
  segundo por `capas`; el `api`, por su prueba. Cada uno por su motivo.

**Tres cosas que hacían falta antes, y las tres eran defectos:**

1. **`verificar` no podía correr fuera de Windows**: llevaba clavado
   `.\gradlew.bat`. Ahora elige por plataforma y resuelve por ruta absoluta
   desde la ubicación del script — el prefijo relativo tampoco sirve, porque Git
   Bash define `NoDefaultCurrentDirectoryInExePath` y con eso `cmd` deja de
   buscar en el directorio actual.
2. **`apps/api/gradlew` estaba en git como `100644`.** En Linux, `./gradlew`
   muere con *permission denied* antes de compilar nada.
3. **La suite estaba a un pelo de volverse intermitente.** El tiempo por omisión
   de Vitest son 5 s y `construirSitemap > no pasa del tope de URL que admite el
   formato` tarda **4,7 s** —construye las 50.000 URL del límite, no puede hacer
   menos—, con las de `axe` entre 2 y 3,3 s. Con la máquina ocupada, seis
   pruebas se cayeron por tiempo sin que nada estuviera roto; un ejecutor de CI
   tiene la mitad de núcleos. Subido a 30 s con `runnerConfig`, **y comprobado
   que la configuración se aplica** bajándola a 1 ms y viendo caer 436 pruebas.

`npm run contrastes` pasó a correr dentro de `verificar`: 1,1 s, y hasta ahora
solo se ejecutaba si alguien se acordaba.

#### Etapa 2, cerrada: los dos guardianes que faltaban

**Deriva del contrato.** Trabajo nuevo en cada pull request: levanta PostgreSQL
y `bootRun`, regenera `packages/contratos/src/tipos.ts` y falla si el diff no
queda vacío. **60 s.** Atrapa lo que hasta ahora dependía de acordarse —cambiar
un DTO del backend y no regenerar el cliente—, que no se nota hasta que el
frontend usa un campo que ya no existe. Comprobado que dispara: cambiando una
descripción del OpenAPI sin regenerar, el trabajo falla, dice qué comando corregir
y **imprime la línea del diff**; los otros dos siguen verdes.

**Para que fuera posible hubo que arreglar un defecto del contrato público.** El
webhook de Wompi recibe `JsonNode` —la firma se calcula sobre el evento tal como
llega y su forma la decide la pasarela—, y springdoc, al ver el tipo, publicaba
la clase de Jackson entera en el OpenAPI: treinta y pico de propiedades booleanas
(`isArray`, `isBigDecimal`, `nodeType`) que no describen nada del evento y que se
llevaba cualquier cliente nuestro, incluida la app móvil que viene. Y salían **en
orden distinto en cada arranque**, porque vienen de reflexión sobre los métodos de
la clase: cada `npm run contratos` producía un diff falso, así que el guardián
habría nacido en rojo permanente. Declarado como objeto libre, que es lo que es;
verificado que dos generaciones seguidas dan un archivo idéntico byte a byte.

**Recorridos.** Flujo aparte (`recorridos`), con los tres servicios levantados por
él mismo y el mismo canal de Chrome que pide el config —para que CI y la máquina
de desarrollo prueben contra el mismo navegador—, guardando trazas y registros
cuando algo falla. Corre al mezclar a `main` y a demanda desde Actions. Se probó
antes de mezclarlo, activándolo temporalmente en el pull request: **128 s**, en
verde, los dos recorridos.

Esos 128 s dejan la decisión de "no en cada pull request" apoyada en un solo
argumento, y conviene que quede escrito cuál: **ya no es el costo** —corre en
paralelo con un trabajo de 207 s, o sea que no añade reloj—, es que una prueba de
navegador que falle por azar bloquearía todos los merges. Si se estabiliza, pasar
a `pull_request` es una línea.

Tiempos de la etapa, medidos: `web` 84 s, `contrato` 60 s, `api` 207 s,
`recorridos` 128 s.

#### Etapa 3, cerrada: el ambiente `dev` en línea, y gratis

**El sitio está en línea** (9 de septiembre de 2026):
`https://tecnosport-web-sdlqfchkiq-ue.a.run.app`, cuatro productos servidos desde Neon, portada en
200 en menos de medio segundo, ficha y portada traducidas en los dos idiomas, `sitemap.xml` con 18
URL y el proxy de `/api` sirviendo desde el dominio de la web.

Los límites de la capa gratuita se verificaron contra la documentación de Google, no de memoria.
**Se puede tener el sitio en línea sin pagar, con una sola excepción: PostgreSQL.**

| Pieza | Capa gratuita | ¿Alcanzó? |
|---|---|---|
| Cloud Run (API y web) | 2 M peticiones, 180.000 vCPU-s, 360.000 GiB-s, 1 GB de salida desde NA | Sí, escalando a cero |
| Cloud Storage | 5 GB-mes, solo `us-central1`/`us-east1`/`us-west1` | Sí |
| Artifact Registry | 0,5 GB | Con poda: 3 versiones y borrado a los 7 días |
| Secret Manager | 6 versiones activas | Justo; sobra una y cuesta centavos |
| Cloud SQL | **ninguna** | Por eso la base es Neon |
| Balanceador | ninguna | No hace falta: el SSR hace de proxy |

Todo vive en `us-east1`, la región del bucket de imágenes que ya existía.

**Decisiones que quedaron en el código y conviene no volver a discutir:**

- **La base de dev es Neon**, con el punto de conexión **directo y no el del pooler**: por el
  pooler, las migraciones pueden topar con sentencias que PgBouncer no admite en modo transacción.
- **Sin balanceador: el servidor SSR hace de proxy de `/api`.** No es un atajo. La cookie de
  refresco es `HttpOnly`, `SameSite=Lax` y acotada a `/api/v1/auth`; con la API en otro dominio, el
  navegador no la manda y la sesión no sobrevive a un F5.
- **`min-instances = 0`**: una instancia siempre encendida son ~2,59 millones de vCPU-s al mes
  contra los 180.000 gratuitos. Se paga con arranque en frío.
- **Federación de identidad, cero llaves JSON**, acotada por condición al repositorio. Esa
  condición es la seguridad entera.
- **Los valores de los secretos nunca pasan por Terraform**: el recipiente sí, el contenido se
  carga con `gcloud`. Lo que se le pasa por variable acaba escrito en el estado.
- **La imagen del servicio está en `ignore_changes`**: la mueve el despliegue, y sin eso cada
  `apply` desharía el último despliegue en silencio.

**Nueve defectos, y ninguno se veía leyendo.** Todos salieron de correr las cosas —la imagen en
local, el `apply` de verdad, el sitio desde afuera— y esa es la lección de la etapa:

1. **`NG_ALLOWED_HOSTS`**: `security.allowedHosts` de `angular.json` viaja dentro del bundle del
   servidor y solo admitía `tecnosport.co`. En un dominio `*.run.app`, **400 a cada petición**. Se
   resolvió con comodín, porque la URL no se conoce antes de crear el servicio.
2. **Sin `API_URL_PUBLICA`, el SSR se pide a sí mismo.** `baseUrl()` cae a `localhost:8080`, que
   dentro del contenedor es el propio servidor web: cada render dispara otro render. Cuelga sin
   error.
3. **El SSR pedía sus traducciones por HTTP** resolviendo una ruta relativa contra el `Host`. Sin
   alcanzarse, la página sale con las claves de Transloco crudas y **sin un solo error**. Ahora las
   lee del disco (`docs/05-i18n.md`).
4. **Un secreto sin versión no se puede montar**: la revisión no arranca y Cloud Run lo reporta
   como "internal error" sin mencionar los secretos. De ahí `secretos_cargados`.
5. **La sonda de arranque HTTP no cabía con la imagen de arranque**, que no sirve `/api/v1/salud`:
   el servicio no habría podido existir antes del primer despliegue real.
6. **`DB_PARAMS` faltaba en la plantilla de la URL de JDBC.** Neon exige TLS y la rechaza sin
   `?sslmode=require`.
7. **El paso de migraciones leía `template.containers`** (API v2) cuando `gcloud run services
   describe` devuelve la forma Knative. Habría abortado diciendo que faltaba `DB_HOST` con
   `DB_HOST` puesto.
8. **El primer despliegue quedó en verde con la tienda vacía**: los sembradores cuelgan de
   `@Profile("local")` y en Cloud Run no hay perfil. Que no siembren solos en un ambiente
   desplegado es correcto; lo que faltaba es que dev existe para recorrer el sitio. Ahora
   `@Profile({"local","dev"})`, y son idempotentes, que con arranques en frío importa.
9. **El 411 del POST sin cuerpo era una falsa alarma, y queda escrita porque enseña más que un
   defecto.** `POST /api/v1/auth/refresco` devolvía 411 a través del proxy, y se dio por hecho que
   era el proxy reenviando un flujo vacío sin longitud declarada. Al comprobarlo por los dos
   caminos con más cuidado, **la petición directa a la API también daba 411**: la causa era `curl
   -X POST` sin datos, que no manda `Content-Length`, y el frontend de Cloud Run la exige. Un
   navegador siempre la manda, y con ella son 204 por los dos caminos. **La aplicación nunca
   estuvo rota.** El cambio que se hizo —juntar el cuerpo en memoria en vez de reenviarlo en
   flujo— se queda porque garantiza una longitud declarada sea lo que sea que mande el cliente, y
   porque por aquí solo pasa JSON pequeño; pero su motivo real es robustez, no arreglar un fallo
   de producción. La lección: una herramienta de diagnóstico también es una variable del
   experimento.

**Dos hallazgos de configuración que el código ya contradecía:** la llave privada de Wompi no la
lee nadie (el estado de una transacción se consulta con la pública, `Authorization: Bearer`), así
que se fue de `.env.example` y del documento; y `WOMPI_LLAVE_PUBLICA` faltaba en el servicio, con
lo que la aplicación arrancaba con un relleno que Wompi rechaza.

**Y una advertencia que se pagó en el camino:** editando la zona de `tecnosport.co` para el
subdominio de Resend **se perdió el SPF de la raíz**. Se detectó consultando el servidor
autoritativo y se restauró con el valor que este proyecto había anotado esa misma mañana. Sin ese
apunte, no habríamos sabido qué restaurar.

**El despliegue ya corre al mezclar a `main`.** Estuvo solo a demanda hasta que hubo base de datos
y hasta que dos corridas manuales demostraron el flujo completo.

#### Etapa 4: producción

Los mismos módulos con balanceador, Cloud SQL, `min-instances=1`, Secret
Manager, Cloud Scheduler y el DNS con el cuidado de no tocar los MX. **Después
de la Fase 7**: esa fase mete siete variables `SKYDROPX_*`/`ORIGEN_*` y cambia
el modelo de cobro, y montar los secretos de producción antes significa
volver a tocarlos.


## El bloque del retracto, que tampoco tenía fase (2026-09-09)

La Fase 6 dejó escrito que "no existe ningún flujo de retracto, reembolso ni
reversión del pago" y lo llamó *la deuda más grande que deja este bloque*. Se
cierra la parte del retracto y el reembolso; la reversión del pago y la garantía
siguen sin flujo, y ahora es una decisión escrita y no un olvido.

**El texto publicado no mentía, y eso cambió el alcance.** Los términos prometen
el retracto por correo y WhatsApp, no un flujo en el sitio. Así que no hacía falta
pantalla para el comprador: lo que faltaba era que quedara constancia, que el
plazo fuera medible y que el estado del pedido lo reflejara.

**Dos huecos reales encontrados en el camino, ninguno previsto:**

1. **`RECAUDO_CONCILIADO` era terminal**, así que una compra contraentrega
   entregada y cobrada **no tenía ningún camino de vuelta**. El artículo 47 no
   distingue el método de pago. Ahora sale hacia `DEVUELTO`; desde
   `RECAUDO_PENDIENTE` sigue sin poderse, porque mientras el dinero no haya
   llegado no hay nada que reintegrar.
2. **Una compra contraentrega nunca confirma su reserva de inventario.** Solo lo
   hacen el pago por Wompi y la transferencia conciliada, así que la unidad
   vendida jamás sale del saldo total: queda una reserva abierta para siempre.
   `Inventario.devolver` lo soporta —decide entre entrada y liberación según cómo
   quedó la reserva— pero **no lo arregla**. Queda como deuda propia.

**El veredicto de plazo tiene tres valores y no dos.** Sin el calendario de
festivos cargado, el límite calculable es el más temprano posible —un festivo solo
lo empuja hacia adelante—, así que "llegó a tiempo" se puede afirmar pero "llegó
tarde" no. De ahí `INDETERMINADO`, que desaparece solo el día que se cargue el
calendario. **`TODO: FESTIVOS_COLOMBIA`** es el dato pendiente. Radicar fuera de
plazo nunca se bloquea: decide una persona con el dato delante.

**El reembolso es un registro, no una orden de pago.** De los tres métodos de
pago, dos se devuelven por fuera del sistema por definición, y para el tercero no
está verificado que Wompi exponga la devolución por API. Un caso de uso que
pretendiera devolver automáticamente sería mentira en dos de cada tres pedidos.

**Se cerró de paso el hallazgo 3 de `docs/12-legales-de-envio.md`**, que estaba
asignado a la Fase 7: el seguimiento público devolvía el `Envio` completo —costo
real del flete y comisión de recaudo— a cualquiera con un id y el correo. No tenía
sentido añadirle el retracto a un DTO que ya filtraba de más. La causa de fondo
quedó escrita: **el panel y el comprador compartían el mismo record**, y por eso la
fuga era invisible.

**Tres pruebas propias nacieron vacías y los mutantes las destaparon**, que es la
lección transversal de este bloque:

- La del contraentrega solo miraba `saldoDisponible`, y ese número da lo mismo con
  la implementación correcta que sumando una entrada indebida.
- La de la fuga del flete afirmaba sobre un pedido que **todavía no tenía envío**,
  así que el bloque no se ejercitaba.
- El primer mutante del plazo —contar el día de la entrega y salir un día antes—
  resultó **equivalente por accidente** y no rompió nada.

**Lo que este bloque no hizo:** reversión del pago, garantía y radicación de PQR
con número, los tres prometidos en los términos y los tres atendidos a mano
todavía. Y falta el recorrido a mano contra `bootRun` real, que en este proyecto
es donde aparecen los defectos que las pruebas no ven.

## El bloque de la atención, que cerró los tres legales pendientes (2026-09-10)

El bloque del retracto dejó escrito lo que no había hecho: "reversión del pago,
garantía y radicación de PQR, los tres prometidos en los términos y los tres
atendidos a mano". Esto los cierra, y cierra también la deuda de inventario que
ese mismo bloque había dejado anotada.

**El plan salió de una auditoría con `vacios-legales-del-sitio`, y la skill se
corrigió antes de usarla.** Le faltaban dos cosas para este trabajo: la ficha de
PQR era la única de las cuatro sin `rastro mínimo`, y no había ninguna ficha del
reintegro como asunto compartido. Lo segundo importaba más de lo que parecía —
siguiendo la skill al pie de la letra se auditan las figuras una por una, y así
se pierden las que no tienen ficha.

**Los términos publicados prometen cinco caminos que devuelven dinero, no tres.**
Retracto, garantía y reversión son los que uno espera. Los otros dos aparecieron
leyendo el documento entero en busca de toda frase que prometiera devolver dinero,
y viven en secciones que nadie lee como secciones de dinero: **no disponibilidad
sobrevenida** ("Disponibilidad", quince días calendario) e **incumplimiento del
plazo de entrega** ("Envío y entrega"). Llevaban tiempo prometidos sin una línea
de código detrás y sin que nadie los hubiera contado.

**El hallazgo que ordenó el diseño de la atención:** el mismo buzón recibe
solicitudes con relojes legales distintos. Los términos prometen quince días
hábiles para "toda petición" y la política de datos promete diez para una
consulta, apuntando las dos al mismo correo. Una sola constante habría incumplido
la más corta sin que nadie lo notara — es el caso 2 del encabezado de la skill,
el que nadie ve porque las dos partes funcionan. El sistema cumple el más corto
por tipo; **corregir la contradicción del texto es otra tarea y no es de código**.

**Lo que se construyó, en orden:**

1. **La deuda de inventario del contraentrega.** `ConfirmarReservasDeLineas` solo
   se llamaba desde el pago de Wompi y desde la transferencia conciliada, o sea
   desde los dos caminos por los que entra dinero antes de despachar. Un
   contraentrega no pasa por ninguno: su reserva quedaba abierta para siempre.
   Ahora confirma **al entregar** y no al conciliar el recaudo — la mercancía sale
   cuando el comprador la recibe, y entre `ENTREGADO` y `RECAUDO_CONCILIADO`
   pueden pasar semanas.
2. **`Reintegro` sale de dentro de `SolicitudRetracto`** y pasa a ser agregado
   propio con `motivo` y `origenId` obligatorios (`V23`). Lo que se perdió, dicho
   sin adornos: ya no es estructuralmente imposible escribir un reintegro
   huérfano. Lo sustituye la invariante del lado contrario — una solicitud que se
   declara reembolsada exige el id de su constancia, en el dominio y en la base.
3. **Radicación de PQR** con número (`TS-PQR-2026-000123`), tipo, y **dos fechas
   y no una**: el plazo corre desde que llegó, no desde que alguien la registró.
   La prórroga vale por el aviso, no por otorgarla. `V24`.
4. **Garantía** con las **tres** salidas de la ley y el término **por categoría**.
   `V25`.
5. **Reversión** con sus cuatro causales tasadas y el registro de qué se hizo para
   facilitar el trámite. `V26`.
6. **Cancelación** por los dos caminos que nadie había contado: `EstadoPedido`
   gana `CANCELADO`, alcanzable solo antes de despachar.

**Tres decisiones que conviene no volver a discutir:**

- **Garantía y reversión radican su propia solicitud de atención; la cancelación
  no.** Aquéllas son peticiones del comprador con su plazo corriendo; ésta es el
  negocio avisando de algo suyo. Meterla en la bandeja llenaría de ruido lo que
  hay que responder.
- **Resolver una garantía o una reversión responde su solicitud.** Separarlas
  dejaría casos resueltos con el plazo corriendo para siempre en la bandeja.
- **Solo queda constancia de dinero cuando el dinero salió de aquí.** Si revierte
  el emisor, la plata vuelve por la red de pagos: inventarle un `Reintegro` sería
  registrar un pago que no hicimos.

**Un defecto real lo atrapó una prueba**, y es el que vale la pena recordar:
encadenar dos `Optional` con `flatMap` confundía "no encuentro el producto" con
"nadie ha decidido este término", y los dos caían al término general de doce
meses. El segundo es justo el que no debe caer — es el de los celulares.

**Y una duplicación que iba por su tercera copia:** la cuenta de días hábiles y el
veredicto de plazo salieron de `PlazoDeRetracto` y `PlazoDeRespuesta` a
`CalendarioHabil`. Del `TODO: FESTIVOS_COLOMBIA` cuelgan ahora **tres** plazos
legales y no uno; cargarlo los cierra los tres a la vez.

**Recorrido a mano contra `bootRun` + PostgreSQL + Mailpit reales**, que es donde
aparecen los defectos que las pruebas no ven: dos solicitudes del mismo día con
límites distintos (23 y 30 de septiembre), prórroga aceptada en la consulta de
datos y rechazada con 422 en la petición, respuesta que la saca de la bandeja,
tres correos reales en Mailpit; y del lado del dinero, cancelar con el pago sin
entrar libera la reserva y no deja constancia, cancelar con el dinero adentro se
bloquea con 422 hasta que se informa el reintegro, y entonces la reserva
confirmada vuelve como `ENTRADA` —no como liberación— con su fila en `reintegro`.

**El recorrido con clics reales encontró tres defectos que la batería no veía**, y
es el argumento entero de por qué esa regla existe:

1. El botón de cerrar sesión se montaba **encima** del enlace nuevo del panel. El
   host de `ts-boton` es `display: inline`, y el margen inferior de un
   `inline-block` no separa a un hermano en la misma línea: con dos enlaces cabía
   por poco, con el tercero dejó de caber.
2. La ayuda de los campos de fecha, escrita como un `<p>` suelto antes del
   componente, quedaba pegada al campo **anterior** —en la bandeja parecía ser del
   asunto— y ningún lector de pantalla la relacionaba con nada. `ts-campo` ganó
   `ayuda`, atada con `aria-describedby`.
3. Los textos nuevos en castellano iban **sin tildes**, en un sitio donde todo el
   resto las lleva.

Y una lección sobre cómo no arreglarlo: el primer intento de poner las tildes fue
un reemplazo por expresión regular sobre todo el JSON, y rompió la clave de
interpolación `{{dias}}` y, en los specs, el identificador `ReclamacionGarantia`.
Se rehízo con el mapeo exacto del diff y solo dentro de literales entre comillas.

**Datos de negocio que quedaron pendientes al cerrar el bloque**, ninguno
inventado: `TODO: FESTIVOS_COLOMBIA` (con tres plazos colgando),
`[[GARANTÍA DE CELULARES]]`, `[[QUIÉN PAGA EL FLETE DE DEVOLUCIÓN]]`,
`[[HORARIO DE ATENCIÓN]]` y `[[PLAZO DE ENTREGA REAL]]`. **Cinco de estos se
cerraron el 10 de septiembre**, y el bloque siguiente cuenta por qué cuatro nunca
fueron datos del negocio.

**Para revisión de abogado**, sin resolver aquí: el 10 contra el 15 sobre el mismo
buzón —si se corrige el texto de los términos o el sistema cumple siempre el más
corto—, y "desgaste normal" como exclusión de garantía, que podría ser más amplia
que la legal.

## El cierre de los datos pendientes

10 de septiembre de 2026. Dos frentes que parecían dos tareas de datos y eran
una de clasificación: **la mitad de lo que estaba esperando una decisión del
negocio no dependía del negocio.**

### Los festivos no eran un dato de negocio

De `TODO: FESTIVOS_COLOMBIA` colgaban tres plazos legales, y llevaba una fase
entera sin moverse por una razón simple: **nadie va a decidir un festivo.** Están
en la ley y la ley es determinista. Se calculan ahora, y no se cargan de una
tabla por año, porque una tabla habría que alimentarla cada diciembre y el
diciembre que nadie se acordara los tres plazos volverían a responder "no se sabe"
sin que ninguna prueba se quejara. `ADR-0024`.

**Verificar antes de escribir código no fue un trámite**: apareció la **Ley 2578
del 1 de junio de 2026** (Diario Oficial No. 53.510 del 2 de junio), que declaró
festivo el 9 de julio y remite a la Ley 51 de 1983 para fijar la fecha del
descanso. Colombia pasó de dieciocho festivos a diecinueve tres meses antes, y un
calendario escrito de memoria habría estado mal desde el primer día. Contra esa
ley hay una demanda de constitucionalidad en curso; mientras no haya decisión,
rige.

Dos detalles que conviene no volver a discutir: **el Jueves y el Viernes Santos
no se trasladan** —no están en la lista del art. 1— y **2025 tuvo diecisiete
festivos y no dieciocho**, porque el 29 de junio cayó domingo y el Sagrado Corazón
viernes, y los dos se trasladaron al mismo lunes 30. Lo segundo salió de una
prueba que falló: contar festivos por la lista de la ley da un número equivocado.

### Los seis marcadores estaban publicados

La plantilla del documento legal pinta cada párrafo tal como está
(`documento-legal.page.html:29`), así que los seis `[[ ]]` no eran una anotación
interna: la página de términos decía "el plazo de entrega es de
`[[PLAZO DE ENTREGA REAL]]` días calendario". En dos idiomas, durante una fase
completa, sin que nada fallara — ninguna prueba mira el contenido de un texto
legal.

**Cuatro de los seis no eran datos del negocio**, y por eso no se movían:

| Marcador | Qué era en realidad |
|---|---|
| `[[QUIÉN PAGA EL FLETE DE DEVOLUCIÓN]]` | Lo reparte la ley, y con **dos** respuestas: por retracto el transporte lo paga el comprador (art. 47 de la Ley 1480), pero los gastos de devolver el dinero los paga el vendedor; en garantía la reparación y su transporte son gratuitos (art. 11). Donde había una frase ahora hay dos, en dos secciones. Y la respuesta estaba escrita en `docs/12-legales-de-envio.md` desde el 8 de septiembre |
| `[[GARANTÍA DE CELULARES]]` | El texto prometía "la garantía del fabricante", que puede ser **menor** que la legal. No hay régimen especial para equipos terminales: un año para producto nuevo, y si el productor anuncia más, manda el mayor |
| `[[TRANSPORTADORA]]` | Decidida en `ADR-0021/0023`, pero Skydropx todavía no despacha ni un pedido |
| `[[PROVEEDOR DE CORREO TRANSACCIONAL]]` | Resend está decidido para dev (`docs/07-infra-gcp.md`), no para producción |

Los dos últimos enseñaron algo que no estaba escrito: **decidido no es
construido.** Nombrar en la política de datos a un tercero que todavía no recibe
ni un dato es cambiar una promesa falsa por otra más concreta y más fácil de
desmentir. Los dos quedan descritos por su categoría, y se los nombra el día que
reciban datos — que en el caso de Skydropx es un paso de la Fase 7.

Los dos que sí son del negocio quedaron sin marcador y sin dato inventado, con las
dos únicas salidas honestas: el **plazo de entrega** declara el término legal
supletivo de treinta días calendario diciendo que es el legal, y el **horario de
atención** se quita, porque el plazo de quince días hábiles se sostiene sin él.
`ADR-0025`, con la regla y su guarda: `npm run marcadores`, dentro de
`npm run verificar`.

### Los tres datos del negocio, cerrados

Llegaron el mismo día, y uno de los tres se cerró **decidiendo no prometer**:

- **Plazo de entrega: no se promete plazo propio.** El despacho depende de la
  transportadora y de la gestión de Skydropx, así que lo que obliga es el término
  legal supletivo de treinta días calendario, publicado como legal. En la Fase 7
  el estimado de la cotización se muestra **como estimado**, nunca como promesa.
  Decidido así con la consecuencia sobre la mesa: un plazo propio más corto es
  exigible el primer día, y **nadie vigila hoy su vencimiento** — ver el pendiente
  de más abajo.
- **Horario de atención: todos los días, de 8:00 a.m. a 9:00 p.m.** Publicado en
  los términos y en el pie, y con una distinción que no conviene perder: es el
  horario de los **canales**, no de un local. Por eso no se emite como
  `openingHours` en los datos estructurados — emitirlo obligaría a declarar el
  negocio como `Store`, o sea a anunciar horario de visita, y el punto de recogida
  es un apartamento. Lo anunciado obliga: alguien que toque el timbre un domingo a
  las 8:50 p.m. tendría razón.
- **Proveedor de correo: Resend, también en producción.** Se eligió el que ya
  funciona en dev. La política de datos lo nombra; quedan a mano la verificación
  del dominio y la clave, y **sin decidir** la región de procesamiento y la razón
  social, que no se suponen (`docs/07-infra-gcp.md`).

Con eso se enlazaron también **las redes sociales** en el pie —Facebook e
Instagram, con `sameAs` en los datos estructurados— y se corrigió el número de
contacto, que el sitio publicaba con el celular equivocado en el pie y en tres
párrafos de los legales.

Y el **punto de retiro** quedó publicado con su dirección, sin el "sin costo" de
la etiqueta: mientras el flete va embebido en el precio, recoger no ahorra nada, y
un "sin costo" que no evita ningún costo es publicidad engañosa el primer día. Es
el hallazgo 3 de `docs/12-legales-de-envio.md`, adelantado — vuelve a ser cierto
en la Fase 7.

### El medio de pago que prefiere el comprador

El hallazgo de la Ley 2439 se cerró el mismo día en que se encontró, y vale
recordar qué era: el sistema **podía cumplir la ley y no podía demostrarlo**.
Sabía por dónde salió la plata y no por dónde la pidieron.

`SolicitudRetracto` gana `medioPreferido` (`V27`), con tres reglas que valen más
que el campo: se anota una vez y no se corrige —corregirla borraría la constancia
de lo que el comprador pidió—, con el dinero ya devuelto no se anota nada, y
devolver por otro medio **no se bloquea** pero queda contrastado. Lo último es
deliberado: puede haber una cuenta que rebota, y quien decide es una persona; lo
que no puede pasar es que no quede rastro.

El texto publicado pasa al estándar de la ley ("por el medio de pago que
prefieras"), y el acuse de retracto también — prometía el estándar viejo.

**PSE no hacía falta construirlo.** Se revisó porque parecía pendiente y ya
estaba: `MetodoPago.PSE` existe desde la Fase 3, `MetodosDePagoDisponibles`
devuelve `EnumSet.allOf` y el checkout lo lista con su etiqueta. Wompi resuelve el
flujo en su Web Checkout.

### Lo que queda abierto

**Nadie vigila el vencimiento del plazo de entrega.** ~~`MotivoCancelacion` ya
trae `PLAZO_INCUMPLIDO`, pero las dos únicas tareas programadas son la purga de
carritos y la conciliación de Wompi. Con el término legal de treinta días
publicado, un pedido pagado y sin despachar lo incumple en silencio.~~ **Cerrado
el 10 de septiembre**, antes de entrar a la Fase 7 y no dentro de ella: no
dependía de nada del envío cotizado y era un incumplimiento vivo. Ver abajo.

**Sigue para revisión de abogado**, además de lo que ya estaba: el "desgaste
normal" como exclusión de garantía; si describir a un tercero por su categoría
—en vez de nombrarlo— satisface el deber de información mientras ese tercero no
reciba datos; y en qué región procesa Resend, que es dato de contrato.

## La revisión adversarial de los tres bloques sin fase (2026-09-10)

Los bloques del retracto, de la atención y del cierre de datos pendientes se
habían mezclado a `main` sin pasar por `/revisar`, que es lo que el plan pide al
terminar cada fase. La revisión cubrió 210 archivos productivos y unas 18.700
líneas, con la batería en verde antes de empezar: **todo lo que sigue lo dejaron
pasar 723 pruebas de Vitest, 872 del backend, `npm run capas` y
`npm run marcadores`.**

Doce hallazgos. Los tres primeros son los que no habrían debido llegar a la
Fase 7.

### El tope del reintegro era por operación y no acumulado

Los cuatro caminos que devuelven dinero —retracto, garantía, reversión y
cancelación— llevaban cada uno su copia de la misma comparación, y las cuatro
miraban lo mismo: que el monto de *esa* operación no pasara del total del pedido.
Ninguna miraba lo ya devuelto.

Un pedido de 500.000 entregado admitía, por endpoints del panel y sin tocar la
base: retracto, recepción y reintegro por 500.000; y después una garantía
radicada sobre el mismo pedido —`RadicarReclamacionGarantia` solo exige que tenga
fecha de entrega, no mira su estado— resuelta con `REINTEGRO` por otros 500.000.
Cada monto válido por separado, un millón devuelto sobre una venta de quinientos
mil, y la mercancía de vuelta en el almacén.

La consulta que lo destapa, `RepositorioReintegros.buscarPorPedido`, existía
desde que se creó el puerto y **no la llamaba nadie**: servía para pintar
pantallas. Y el Javadoc de `MontoDeReintegroInvalidoException` ya decía
"compartida por los cinco motivos: ninguno puede devolver más de lo que entró" —
la intención estaba escrita y la suma nunca se hizo.

`TopeDeReintegro` es ahora el único dueño de la regla, con el patrón de
`AplicadorDeResultadoDePago`. Bloquea en vez de advertir, a diferencia del medio
preferido o de la vigencia de garantía: si 500.001 de golpe se rechaza desde la
Fase 3, 500.000 más 500.000 en dos pasos no puede pasar. Radicar una garantía
sobre un pedido ya devuelto sigue permitido a propósito — la fuga la cierra el
tope, y prohibir la radicación sería una regla de negocio que nadie decidió.

**Una prueba que ya existía decidió el diseño.** Poner el tope después de la
transición de la solicitud —para que un doble clic muriera en la máquina de
estados, que diagnostica mejor ese caso— rompió `noSeDevuelveMasDeLoQueSePago`,
que exige que un monto inválido **no** transicione la solicitud: quien reintenta
con el monto corregido tiene que encontrarla como la dejó. Tenía razón la prueba.

### El plazo de reintegro desaparecía de la pantalla el día que vencía

`panel-retracto.html` preguntaba `@if (diasParaReintegrar(); as dias)`, y los días
salían de `Math.ceil((límite - ahora) / 86.400.000)`. Con el límite vencido hace
unas horas eso da **`-0`**, que es *falsy* —así que el bloque entero se escondía—
y tampoco es `< 0` —así que `plazoVencido()` decía que no—. Durante las primeras
veinticuatro horas de incumplimiento del plazo de quince días calendario del
artículo 47, el panel no decía nada; a partir de la hora 24 el aviso volvía, así
que mirando la pantalla tampoco se notaba.

Había prueba de plazo vencido y pasaba: usaba el año 2020, o sea miles de días
negativos, que sí son *truthy* y sí son menores que cero. La nueva usa fechas
relativas al reloj, porque lo que se prueba es la distancia al límite.

### Dos caminos de dinero respondían 500 por un cuerpo incompleto

`ResolverGarantia` y `ResolverReversion` no validaban nada del cuerpo: con
desenlace `REINTEGRO` y monto ausente, el nulo llegaba hasta el `requireNonNull`
de `Dinero`, y `NullPointerException` no cae en el 422 de "solicitud inválida"
sino en el manejador genérico. `CancelarPedido` tenía la guarda desde el primer
día; los otros dos no la heredaron.

### Los correos: la regla dura #4 incumplida en tres mitades

Los siete correos transaccionales se concatenaban dentro de los casos de uso, en
un solo idioma; los cinco nuevos iban **sin una sola tilde** ("articulo 47",
"quince (15) dias", "Guardalo"), y el asunto de una PQR se interpolaba en HTML
sin escapar — lo escribe una persona en el panel, y un `<` rompía el correo del
comprador. El mismo razonamiento del escapado ya se había aplicado al JSON-LD de
la ficha y no aquí.

Ahora hay un puerto con llaves tipadas y dos paquetes de mensajes. El enum no es
ceremonia: permite que el adaptador recorra `values()` al arrancar y se niegue a
levantar el servicio si falta un texto. El idioma es el castellano y está
razonado, no elegido por comodidad: ni `Pedido` ni `Usuario` guardan idioma, y la
Ley 1480 exige la información mínima en castellano (art. 23) y ese idioma en los
contratos (art. 37.1).

**La guarda de arranque tumbó las 154 pruebas de `infrastructure`**, porque sus
contextos no cargan el `application.yml` de `bootstrap` y el `MessageSource`
inyectado llegaba sin paquete. Lo encontró el build, y enseña algo que vale más
que el arreglo: *un componente que se niega a arrancar sin su configuración no
puede depender de que otra capa se acuerde de configurarlo.* Ahora el adaptador
construye su propio `ResourceBundleMessageSource`.

### El dato de negocio que se publica en diez sitios, ahora con guardián

El celular corregido el 10 de septiembre estaba mal en el pie y en tres párrafos
legales, y al arreglarlo no quedó nada que impidiera la reincidencia: el teléfono
vive en diez copias y dos formatos, el NIT en ocho, el correo en catorce.
`npm run datos-negocio` toma como fuente el bloque `pie` de `es.json` y exige que
toda aparición coincida, normalizando el formato; y comprueba la versión legal en
los cuatro sitios donde vive, que es el caso más grave — si `legales.comun.version`
se separa de `POLITICA_DATOS_VERSION`, cada fila de `autorizacion_datos` apunta a
una versión del texto que nunca se publicó.

**Encontró algo en su primera corrida, antes de estar comiteado**: la dirección
del punto de recogida estaba escrita de dos formas, con y sin espacio tras el
numeral. Unificada.

### Lo demás, y por qué también contaba

- **El pie guardaba antes de aplicar.** Con el almacenamiento bloqueado, el
  `setItem` lanzaba y la línea que pone `data-movimiento` nunca corría: la casilla
  quedaba marcada y el movimiento sin reducir. No es persistencia, es la
  preferencia de accesibilidad sin aplicar. La primera prueba escrita para esto no
  valía —espiando `setItem` sobre `window.localStorage`, jsdom no lo intercepta y
  pasaba con el defecto puesto—; sobre `Storage.prototype` sí.
- **Un comentario que argumentaba contra su propio archivo**: decía que poner los
  logos de Facebook e Instagram exigiría una dependencia nueva y no valía la pena,
  ocho líneas encima de los `ts-icono-marca` que los pintan.
- **Los seis paneles tapaban el motivo del error.** El 422 de "el reintegro no
  cabe", el 409 de "ya hay un retracto en curso" y una caída de red se veían
  idénticos, y el `codigo` del `ProblemDetail` se tiraba a la basura. Ahora el
  código elige una clave de Transloco; el `detail` del backend no se muestra,
  porque viene en un solo idioma y escrito fuera de Transloco.
- **Nadie había decidido no versionar los agregados.** Buscando bloqueo optimista
  en los cinco nuevos apareció que **ninguna de las veinticinco entidades JPA lo
  tiene**. `ADR-0027` fija la decisión, lo que sí está protegido y probado (dinero,
  inventario, consecutivo) y el disparador para volver: el día que exista un
  segundo `ADMIN`.
- **Tres comentarios explicaban decisiones con "los festivos pueden cargarse más
  adelante"**, que dejó de ser cierto con `ADR-0024`. Dos siguen siendo correctas
  por otra razón, más duradera, y ahora es la que está escrita: un calendario
  legal cambia —la Ley 2578 llegó con el año empezado y está demandada— y
  recalcular movería hacia atrás el dato con el que alguien decidió. El comentario
  de `V22` **no se corrige**: editar una migración aplicada le cambia el checksum y
  Flyway rechaza la base entera. La explicación va a la columna en `V28`.

### Lo que esta revisión enseñó sobre las pruebas

Tres veces pasó lo mismo y conviene que quede escrito: **una prueba verde no dice
que el código esté bien, dice que la prueba pasa.**

1. `RegistrarRetractoTest` afirmaba `contains("articulo 47")`, sin tilde: la prueba
   estaba **fijando** el defecto.
2. La prueba del plazo vencido usaba el año 2020, el único rango donde el `-0` no
   aparece.
3. La primera prueba del `localStorage` pasaba con el defecto puesto.

De ahí la regla que se siguió en los cinco commits de arreglo: **revertir el
arreglo y comprobar que la prueba falla**, antes de darla por buena. Los cinco lo
tienen anotado en su mensaje.

Quedan, para cuando se retomen: el orden de las guardas en `ResolverGarantia` y
`ResolverReversion`, que construyen el `Reintegro` antes de la guarda de estado de
su agregado —hoy lo cubren el tope y la transacción—, y las cuatro copias
idénticas de `RepositorioReintegrosFalso` en las pruebas.


## El vigilante del plazo de entrega (2026-09-10)

Lo único que la revisión adversarial dejó abierto y no era de la Fase 7. Los
términos publicados prometen treinta días calendario para entregar (Ley 1480 de
2011, art. 18) y que, si no se cumple, quien compró puede terminar el contrato y
recuperar su dinero. La segunda mitad tenía código desde el bloque del retracto
—`CancelarPedido` acepta `PLAZO_INCUMPLIDO`—; la primera no tenía nada.

**Avisa y no cancela** (`ADR-0028`). El artículo 18 le da la opción al consumidor,
no obliga al vendedor a deshacer el pedido por su cuenta: puede preferir esperar,
y cancelárselo sin preguntarle sería decidir por él. Quien decide es una persona,
y entonces corre el `CancelarPedido` que ya existía. Tampoco radica una PQR, por
lo mismo que no la radica aquél: esa bandeja es de peticiones del comprador.

Tres decisiones con filo, las tres anotadas en el ADR:

- **El plazo cuelga del historial, no de una columna.** Se lee del registro de
  `PAGADO`, o del de `CONFIRMADO_CONTRAENTREGA` cuando se paga al recibir —ahí se
  celebra el contrato y no hay confirmación de pago que esperar—. Mismo
  razonamiento que `Pedido.fechaDeEntrega()`. La única columna nueva es cuándo
  salió el aviso, que no se deduce de ningún estado.
- **Días calendario, no hábiles**, así que `PlazoDeEntrega` no recibe
  `CalendarioHabil` y nunca responde `INDETERMINADO`. Es el primer plazo del
  sistema que no pasa por los festivos de `ADR-0024`.
- **Cubre los despachados sin entregar**, porque el plazo corre hasta la entrega.
  El falso positivo está asumido a sabiendas —hoy la entrega se marca a mano— y por
  eso ese caso lleva un párrafo propio que no acusa a nadie. Con el seguimiento de
  la Fase 7 (`ADR-0022`) esa marca deja de ser manual y el párrafo sobra.

Un detalle que solo apareció al pintar el panel: un pedido **ya entregado** se
juzga contra su fecha de entrega y no contra el reloj de hoy. Medirlo contra ahora
habría pintado como incumplido cualquier pedido viejo entregado en plazo — un
panel que grita en las filas equivocadas deja de mirarse.

La tarea corre cada doce horas y es la tercera del sistema, junto a la purga de
carritos y la conciliación de Wompi.

### La revisión adversarial, que reescribió la garantía

Los dos commits pasaron por `/revisar` antes del PR, con la batería en verde. Doce
hallazgos; los tres primeros tumbaron el mecanismo entero y están contados en
`ADR-0028`: el barrido en una sola transacción reenviaba los correos ya
mandados si fallaba tarde, varias instancias de Cloud Run escribían N veces, y
`guardar` pisaba la marca. Los tres se cerraron con una escritura condicional
atómica, y la columna quedó de solo lectura para el agregado.

Otros dos merecen quedar escritos porque son el mismo error de siempre —prometer
lo que el software no hace—:

- **El correo del despachado prometía una cancelación imposible.** De
  `DESPACHADO` solo se sale entregando o con el rechazo en la entrega. El panel
  repetía la promesa mandando a usar un botón que ese estado no ofrece.
- **El retraso inicial igualaba al intervalo**, así que cada despliegue reiniciaba
  la cuenta de doce horas: desplegando a diario, el vigilante no habría corrido
  nunca. Ahora arranca a los cinco minutos y las propiedades se niegan a
  levantarse si alguien vuelve a igualarlos.

Y dos que solo aparecieron **mirando la pantalla**, que es lo que
`docs/06-testing.md` dice que las pruebas no atrapan:

- El detalle decía «Vence 1/09/2026, 12:00 a. m.» para un plazo que se agotó al
  terminar el 31 de agosto. El límite es el instante siguiente al último día;
  pintarlo crudo regala un día.
- **Todo pedido `CANCELADO` salía con la columna Estado en blanco**, y eso es
  anterior a este trabajo: `CLAVE_ETIQUETA_ESTADO` no tenía esa entrada ni el tipo
  del panel ese valor, desde que se construyó la cancelación. Ninguna prueba lo
  vio porque ninguna sembraba un cancelado. De paso el filtro de estado lo ganó,
  que tampoco lo tenía.

El par de contraste del rojo sobre la superficie elevada tampoco estaba en
`npm run contrastes` —pasa, 5.52 y 5.04— y ahora está: el guardián miraba el rojo
sobre fondo y sobre superficie, pero no sobre la fila expandida, que es donde se
pinta.

Dos commits: el backend completo —dominio, migración `V31`, los dos puertos, caso
de uso, seis textos en los dos idiomas y la tarea— y el panel, que ya lo ve en la
lista sin tener que expandir la fila.

## Fase 7. Envío cotizado con Skydropx y seguimiento

Decidida el 8 de septiembre de 2026, **documentada y sin una línea de código
todavía**. Cambia una premisa que llevaba desde la Fase 3: el precio publicado
deja de incluir el envío. Lo que la sostiene: `ADR-0021` (cotización),
`ADR-0022` (seguimiento), `ADR-0023` (recaudo por Skydropx),
`docs/11-pagos-y-envios.md` y `docs/12-legales-de-envio.md`.

**Lo verificado antes de decidir, no supuesto** (regla dura #9, y en este
proyecto ya costó una sesión creer un vector de firma inventado): la API de
Skydropx usa OAuth 2.0 con client credentials, token de 2 horas y límite de 2
peticiones por segundo; la **cotización es asíncrona** (`POST /quotations`, luego
`GET /quotations/{id}` hasta `is_completed`, tarifas válidas 24 horas); el envío
se crea con `quotation_id` más `rate_id`; y los estados de seguimiento son los
doce que lista `ADR-0022`. Lo que **no** se pudo confirmar en fuente oficial y
queda como `TODO`: el host base de la cuenta colombiana, el nombre exacto de la
cabecera de firma del webhook, y los límites y comisiones del recaudo.

Orden de construcción, un caso de uso a la vez:

1. ~~**Paquete por variante.**~~ **Hecho el 10 de septiembre de 2026.** Objeto de
   valor `Paquete` en el dominio, las cuatro columnas `not null` en `V32` con un
   `check` de positividad, y el panel pidiéndolas al crear una variante.

   Tres cosas que solo aparecieron al construirlo:

   - **La migración rellena por SKU explícito y falla si no reconoce una fila.**
     Un `default` le habría puesto el mismo peso a una camiseta y a unos tenis.
     Preferible un despliegue detenido a un flete cobrado de menos.
   - **El catálogo sembrado lleva medidas de demostración declaradas como tales**,
     y eso no viola "no inventes datos de negocio": ese catálogo es ficción
     completa, y `hashDeSiembra` ya había resuelto antes el mismo dilema en esta
     misma clase.
   - **El `check` de positividad está en la base además de en el dominio** porque
     `SembradorCatalogo` escribe entidades JPA directo, sin pasar por `Paquete`.
     Una invariante que solo vive en el dominio no protege al que lo esquiva.

   Y una cuarta que vale para la fase entera: **`packages/contratos/src/tipos.ts`
   quedó desactualizado y `npm run verificar` pasó igual.** Los cuatro campos
   nuevos no estaban en el tipo generado, el frontend los mandaba, y ni el lint ni
   el build ni las 748 pruebas dijeron nada. Hay que correr `npm run contratos`
   con el backend arriba **cada vez que cambie un DTO**, porque ningún guardián lo
   vigila.

   Queda vivo el `TODO` del peso real del catálogo de producción.
2. **Puerto `CotizadorEnvio` y `SkydropxClient`.** Con el token cacheado, el
   sondeo acotado por tiempo e intentos, y una prueba que ejercite la cotización
   que **nunca** completa. Aquí entran también las variables `SKYDROPX_*` y
   `ORIGEN_*` en `.env.example` y en las propiedades tipadas: hoy están
   documentadas en `docs/07-infra-gcp.md` y no existen en ningún archivo.
3. **`POST /api/v1/envios/cotizacion`**, con `409 ENVIO_SIN_COBERTURA` como caso
   de negocio y no como error de sistema.
4. **Totales del pedido.** `Pedido` gana el costo de envío y la tarifa congelada;
   `Pedido.total()` pasa a ser líneas más envío, y su Javadoc actual —"el envío no
   se agrega: ya está en cada precio unitario"— muere con el cambio.
5. **Checkout.** Cotización en el paso de dirección, línea de envío y total en el
   resumen, ahorro visible en la recogida, y el aviso de efectivo en
   contraentrega. Las claves de i18n están redactadas en
   `docs/12-legales-de-envio.md`, sección 3.
6. **Contraentrega desde la cotización** (`ADR-0023`): retirar
   `cobertura_contraentrega` y sus endpoints, y que `MetodosDePagoDisponibles`
   dependa de la tarifa con recaudo.
7. **Guía en el despacho** y **seguimiento**: webhook firmado, eventos
   `append-only`, `TareaConciliacionEnvios`, y el DTO público de seguimiento
   **reducido** — hoy expone el costo real del flete y la comisión de recaudo, que
   es el hallazgo 3 de `docs/12-legales-de-envio.md` y es un bug de hoy, no del
   cambio.
8. **Textos legales**, en el mismo commit que enciende la cotización, con la
   fecha de versión nueva.

**Los tres hallazgos que la auditoría legal dejó por escrito** y que no se pueden
perder de vista al construir:

- El resumen del checkout muestra hoy solo "Subtotal", sin total ni envío: con
  flete aparte, eso incumple el **artículo 50 de la Ley 1480 de 2011**, que exige
  el resumen con los costos de envío separados y la suma total antes de finalizar
  la transacción.
- `GET /api/v1/pedidos/{id}/seguimiento` devuelve el `Envio` completo, con el
  costo real y la comisión de recaudo, a quien tenga el id y el correo.
- "Retiro en punto (Medellín, sin costo)" engañaba, porque el flete va embebido
  en el precio y recoger no ahorra nada. **Se adelantó el arreglo el 10 de
  septiembre**: la etiqueta perdió el "sin costo" y ganó la dirección del punto.
  Con la cotización vuelve a ser cierto, y ahí se puede volver a poner — junto con
  `resumen.retiro_ahorro`, que dice cuánto se ahorra de verdad.

**Datos de negocio pendientes que bloquean partes de la fase**, todos en la
sección 4 de `docs/12-legales-de-envio.md`: el IVA del flete, los límites y la
comisión del recaudo, la entidad con la que se firma con Skydropx, y el peso y las
dimensiones del catálogo sembrado.

Tres de los que estaban en esta lista se cerraron el 10 de septiembre: el plazo de
entrega real —decidiendo **no** prometer uno propio—, y la dirección y el horario
del punto de recogida, que es Cra. 26C # 38B-31, barrio La Milagrosa, apto. 401,
y se coordina al confirmar el pedido en vez de tener horario de mostrador.

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
están integrados y las pantallas nuevas están en la navegación del sitio.**
"Backend cerrado" o "vitrina cerrada" documentan un avance, no el cierre de la
fase. Cerrar exige las tres cosas, no dos:

1. **Los dos lados funcionando juntos**, verificado a mano contra el backend
   real.
2. **Cada pantalla nueva alcanzable con clics desde la portada**, sin teclear
   una URL: enlazada desde el encabezado, el pie, la portada o la pantalla que
   la precede en el recorrido. Si la pantalla exige un rol o un estado (panel
   de `ADMIN`, cuenta iniciada), el enlace aparece cuando ese rol o ese estado
   existe, no siempre. Una pantalla que solo se abre escribiendo su ruta no
   está entregada: está escondida.
3. **El recorrido completo hecho de verdad en el navegador**, de punta a
   punta, no pantalla por pantalla y cada una por su URL.

Las dos primeras se aprendieron a la mala. Documentar el backend de una fase
sin su frontend dejó, en su momento, la Fase 3 marcada como completa sin
checkout en el navegador. Y documentar el frontend sin su navegación dejó las
fases 3 y 4 cerradas con dieciocho pantallas —todo el checkout, toda la cuenta
de cliente y todo el panel administrativo— que solo se alcanzaban tecleando la
ruta: el encabezado no cambió desde la Fase 2, y no había un solo enlace hacia
`/cuenta` ni hacia `/admin` en toda la aplicación. Ninguna de las dos se
repite.

**Cuando algo salga mal, no pidas un parche encima.** Vuelve al plan, corrige la
premisa y regenera. Los parches encadenados sobre un diseño equivocado son la
forma más rápida de terminar con código que nadie entiende.

**Al terminar cada fase, pide una revisión adversarial** con `/revisar`.

**Actualiza los documentos cuando la realidad cambie.** Un `CLAUDE.md` que
describe un proyecto que ya no existe envenena cada respuesta que venga después.
