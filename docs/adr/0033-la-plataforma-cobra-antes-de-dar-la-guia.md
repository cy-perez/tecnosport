# ADR-0033 — La plataforma cobra antes de dar la guía

Fecha: 2026-09-16
Estado: aceptado
Reemplaza en parte a: `adr/0021` (que diseñó la emisión como una llamada síncrona)
Relacionados: `adr/0022`, `adr/0031`, `adr/0032`

## Contexto

`POST /shipments` de Skydropx **no devuelve una guía**. Devuelve `202` con
`payment_status: paid`, `master_tracking_number: null` y `workflow_status: in_progress`. El número
aparece después, o no aparece nunca y el envío muere en `error` con el saldo reembolsado.

Medido el 16 de septiembre de 2026 emitiendo tres guías reales
(`docs/13-skydropx-capacidades.md` §6.10):

| Emisión | Tiempo hasta el estado terminal | Desenlace |
|---|---|---|
| Servientrega, 1 bulto | ~45 s | `success` |
| Servientrega, 2 bultos | ~25 s | `success` los dos |
| Coordinadora, 1 bulto | **más de 4 minutos** | `error`, reembolsado |

O sea: la ventana no tiene tope conocido, y el camino que más tarda es el que fracasa.

`adr/0021` había diseñado este tramo como una llamada más, del estilo de la cotización. No lo es.
Entre el cobro y la guía hay un intervalo de minutos con plata comprometida y nada que mostrar, y
ese intervalo es el que hay que modelar.

## Decisión

### 1. La emisión es un agregado propio, y se persiste **antes** de llamar

`EmisionDeGuia` guarda el pedido, la transportadora, la tarifa con la que se emite, quién la pidió y
—cuando llegan— los identificadores de envío que devolvió la plataforma.

**Nace `SOLICITADA`, en su propia transacción, antes del `POST`.** La primera versión de este ADR
decía "nace cuando la plataforma responde `202`", y con eso no cumplía lo que el propio ADR
prometía: entre el cobro y la respuesta no había fila. Un reinicio ahí deja una guía pagada que
nadie sabe que existe, y se lleva con ella el `rate_id`, que es **lo único** que la recupera por
idempotencia dentro de las 96 horas. No es teórico: la primera emisión del proyecto respondió `408`
con la guía ya creada y 19.465 descontados (§6.2).

Seis estados, y los dos que no son obvios:

| Estado | Qué significa | ¿Bloquea otra emisión? |
|---|---|---|
| `SOLICITADA` | Se va a pedir; la fila existe antes del cobro | Sí |
| `EN_CURSO` | La plataforma cobró y creó los envíos; falta la guía | Sí |
| `INDETERMINADA` | La llamada no terminó y **pudo cobrar igual** | Sí |
| `EMITIDA` / `FALLIDA` / `PARCIAL` | Terminó | No |

`INDETERMINADA` es la respuesta honesta al proveedor que no contesta: darlo por fallido invita a
reintentarlo, y reintentar sobre un cobro que sí ocurrió paga dos veces. No la resuelve ningún
programa — la mira una persona, con el `rate_id` que la fila guarda. Mientras tanto el despacho a
mano sigue disponible, que es la salida de quien está apurado.

**Un rechazo también deja fila.** Cualquiera de ellos: el `rate_id` es lo que hace falta para
buscar, y no escribirlo era perderlo justo en el caso en que más se necesita.

### 2. El pedido no se mueve hasta que la guía viva

`EmitirGuiaDePedido` **no despacha**. El pedido se queda en `EN_PREPARACION`, y quien despacha es
`ResolverEmisionesEnCurso` —una tarea programada— cuando ya hay números de guía, llamando al
`DespacharPedido` que ya existía.

Esto responde la decisión abierta que el plan de arranque dejó escrita como *"¿qué le pasa al pedido
cuando su guía muere? Devolver el pedido a la cola toca inventario y el grafo del pedido"*. **No hay
nada que devolver**: el pedido nunca se movió. La rama de fallo se reduce a registrar el motivo y
dejar el pedido donde estaba, listo para reintentarlo.

### 3. Emitir recotiza siempre

Las tarifas de Skydropx valen 24 horas y entre el pago y el despacho suele pasar más. El camino
"usar la tarifa congelada si todavía vive" se recorrería de vez en cuando y se rompería callado, que
es la peor propiedad que puede tener un camino.

El comprador pagó la tarifa congelada (`Pedido.tarifaEnvio`) y el negocio paga la de hoy
(`GuiaEnvio.costo`). Los dos números ya se guardaban por separado, así que la diferencia queda
medible en vez de escondida. Si el pedido es contraentrega, la recotización va con recaudo, por lo
mismo que al crearlo.

### 4. `PARCIAL` es un estado, no un fallo

En multienvío los envíos son independientes: un pedido de dos bultos puede terminar con una guía
viva y pagada y otra muerta. Despachar media compra o reintentarla entera son decisiones con plata
de por medio que no toma un programa.

La emisión queda en `PARCIAL`, **con los números de las guías vivas escritos en el detalle**, el
pedido no se mueve, y alguien mira. Llamarlo `FALLIDA` escondería que hay una guía que cancelar o
usar.

### 5. El reintento no repite transportadora, y un eco de la caché no es una emisión

Las tarifas se recotizan en cada intento, y ahí se cruzan dos comportamientos del proveedor que por
separado son inofensivos y juntos no: **la cotización se deduplica por contenido** —mismo carrito,
mismo destino, mismo `rate_id`— y **`unique_shipment` cachea la creación por `rate_id` 96 horas**.
Un reintento del mismo pedido recibía de vuelta los envíos *muertos* del intento anterior, chocaba
contra la unicidad y moría en un 500, dejando el pedido sin salida por el panel.

Dos defensas, y la primera es la que importa:

- **No se vuelve a elegir la transportadora que ya falló para ese pedido.** El fallo más caro que se
  ha medido es determinista —el contador de remisiones de Coordinadora está atascado— y es además la
  tarifa más barata, o sea la que `TarifaEnvio.masEconomica` elige sola. Sin excluirla, cada
  reintento repite el mismo fracaso.
- **Si aun así vuelven identificadores que ya son nuestros**, se reconoce como eco de la caché y se
  cierra con un mensaje que dice qué hacer, en vez de reventar.

### 6. Un corte de red se reintenta, y es lo contrario de lo prudente

El cuerpo lleva `unique_shipment: true`, que hace la creación idempotente por `rate_id` durante 96
horas. Con eso, repetir la llamada tras un `408` o un corte **recupera** los identificadores del
envío que ya se pagó en vez de crear otro. Rendirse al primer corte es lo que los perdería.

Agotados los intentos, el rechazo dice con todas sus letras que puede haber una guía viva del otro
lado. No es una hipótesis: es el caso medido.

### 7. Crear por v2, releer por v1

`POST /api/v2/shipments` devuelve siempre un arreglo de envíos, que es lo que hace falta porque en
Colombia ninguna transportadora admite multipaquete y todo pedido de dos bultos es multienvío
(`adr/0031`). `GET /api/v2/shipments/{id}` **no existe** —404 con el HTML del sitio—, así que releer
va por v1. La integración usa las dos versiones a propósito.

### 8. Cada emisión se resuelve sola, en su propia transacción

La tarea que cierra las emisiones **no envuelve el lote**. Las otras tres tareas del proyecto sí
pueden: su trabajo es idempotente y reintentar la vuelta entera no cuesta nada. Aquí no, por dos
motivos:

- **Despachar manda un correo al comprador**, y un correo enviado no se deshace con un `rollback`:
  se reenvía en la vuelta siguiente.
- **Una emisión que no se deja resolver bloquearía a todas las demás.** `DespacharPedido` lanza si
  el pedido ya no está en `EN_PREPARACION` —porque alguien lo despachó a mano con el formulario que
  está en la misma pantalla, o lo canceló—, y como el lote se lee de la más vieja a la más nueva, la
  ofensora volvía a ser la primera en cada vuelta. Una píldora permanente, cada minuto, con una
  traza por único síntoma.

Ahora cada emisión va en su propio `try` y su propia transacción, y el pedido que se movió por otra
vía resuelve la emisión como `PARCIAL` —las guías existen y están pagadas— en vez de reventar.

**No se bloquea el despacho a mano mientras hay una emisión abierta**, y es deliberado: con la tarea
arreglada el choque ya no rompe nada, queda anotado como `PARCIAL` con los números de las guías
vivas, y bloquearlo obligaría a distinguir al sistema de la persona por el prefijo del actor, que es
frágil. El desperdicio posible —una guía pagada que no se usa— se ve, que es lo que hacía falta.

## Alternativas descartadas

- **Sondear dentro de la petición del panel.** Dejaría a quien despacha mirando una pantalla quieta
  hasta cuatro minutos, y perdería una guía pagada si el proceso se cae. El caso lento es
  justamente el que fracasa.
- **Híbrido: sondear treinta segundos y soltar el resto a la tarea.** Necesita igual el agregado
  persistido, y además mantiene el camino síncrono. Dos caminos para el mismo trabajo.
- **Marcar el pedido despachado con el `202`.** Es lo que haría cualquier integración escrita sin
  medir, y deja pedidos "despachados" con una guía que no existe y que nadie va a recoger.
- **Cancelar automáticamente las guías vivas de una emisión parcial.** La API lo permite
  (`POST /shipments/{id}/cancellations`) y reembolsa, pero es una acción con plata que no debería
  dispararse sola sobre un caso que todavía no se ha visto ocurrir.

## Consecuencias

- Una tabla nueva (`emision_de_guia`) con su tabla de identificadores externos, y un índice único
  parcial que impide dos emisiones abiertas para el mismo pedido: entre leer y escribir cabe un
  segundo clic, y ese segundo clic sería otro cobro.
- Una cuarta tarea programada, que corre cada minuto en vez de cada varias horas como las otras
  tres. No es caro: solo hay algo que revisar cuando alguien acaba de pulsar el botón.
- El despacho a mano **se queda**. Una guía emitida en la web de la transportadora sigue siendo
  posible, y esa no tiene código de plataforma ni etiqueta.
- `GuiaEnvio.codigoTransportadora` deja de estar vacío para las guías que emitimos nosotros: la
  respuesta del envío trae `carrier_name`, que es exactamente el código que exige el rastreo. La
  conciliación de `adr/0022` empieza a tener guías que conciliar.

## Lo que la primera versión de este ADR tenía mal

Se escribió el 16 de septiembre y se revisó al día siguiente con dos revisiones adversariales. Lo
que encontraron, porque el error es más instructivo que la corrección:

- **La fila se escribía después de cobrar.** El ADR prometía que ninguna guía pagada se pierde de
  vista y la implementación no lo cumplía en tres ramas distintas. Había además una prueba que
  fijaba como deseado el comportamiento defectuoso —"un rechazo no deja emisión"— que es la forma
  más eficaz de que un defecto sobreviva a una revisión.
- **El índice único evitaba la fila duplicada, no el cobro duplicado.** Se leía, se cobraba y
  después se escribía; lo que de verdad impedía el doble cobro eran dos comportamientos del
  proveedor, no nuestro código.
- **El mensaje del rechazo decía que reintentar con la misma tarifa recuperaría el envío**, y no
  guardaba la tarifa en ninguna parte.
- **El lote transaccional de la tarea** podía atascarse para siempre por un pedido movido a mano.
- Y tres cosas de forma: un puerto con una sola implementación que nunca se dobló, el mismo método
  de conversión de teléfono duplicado carácter por carácter en dos sitios, y un 502 que culpaba a la
  transportadora cuando el problema era que faltaban nuestras credenciales.

## Una decisión de negocio que salió de aquí

**El valor declarado es el precio congelado del pedido, no el del catálogo.** Es el monto que la
transportadora paga si pierde el paquete, y tiene que coincidir con la factura contra la que se
reclama — que dice lo que el comprador pagó, no lo que el producto cuesta hoy. El peso y las medidas
siguen saliendo del catálogo, que es lo correcto para lo contrario: describen el objeto físico, y si
se corrigen porque estaban mal medidas el despacho tiene que usar las buenas.

## Lo que queda abierto

- **¿`FALLIDO` es terminal?** Sigue sin decidirse (`adr/0022`). La emisión que murió el 16 de
  septiembre no llegó a producir eventos de rastreo, así que la pregunta sigue sin medirse.
- **La recolección** está desbloqueada del lado nuestro —el barrio viaja en la cotización y el envío
  lo hereda— y bloqueada del lado de la transportadora, cuyo conector de recolección respondió
  `ECONNREFUSED` tres veces seguidas a las 18:45. Programar una de verdad sigue pendiente.
- **El barrio del destino.** `Direccion` no lo tiene y el checkout no lo pide. Para la recolección
  basta el del origen —el campo que reclama la plataforma es "Shipper address2"—, pero el del
  destino mejoraría la entrega.
