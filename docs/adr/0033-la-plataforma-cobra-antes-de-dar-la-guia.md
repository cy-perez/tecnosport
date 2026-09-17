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

### 1. La emisión es un agregado propio, y se persiste antes de que haya guía

`EmisionDeGuia` guarda el pedido, la tarifa con la que se emitió, **los identificadores de envío que
devolvió la plataforma** y su estado. Nace cuando la plataforma responde `202`.

Sin esa fila, un reinicio del servicio entre el cobro y la respuesta deja una guía pagada que nadie
sabe que existe. No es un caso teórico: la primera emisión del proyecto respondió `408` con la guía
ya creada y 19.465 descontados (§6.2).

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

### 5. Un corte de red se reintenta, y es lo contrario de lo prudente

El cuerpo lleva `unique_shipment: true`, que hace la creación idempotente por `rate_id` durante 96
horas. Con eso, repetir la llamada tras un `408` o un corte **recupera** los identificadores del
envío que ya se pagó en vez de crear otro. Rendirse al primer corte es lo que los perdería.

Agotados los intentos, el rechazo dice con todas sus letras que puede haber una guía viva del otro
lado. No es una hipótesis: es el caso medido.

### 6. Crear por v2, releer por v1

`POST /api/v2/shipments` devuelve siempre un arreglo de envíos, que es lo que hace falta porque en
Colombia ninguna transportadora admite multipaquete y todo pedido de dos bultos es multienvío
(`adr/0031`). `GET /api/v2/shipments/{id}` **no existe** —404 con el HTML del sitio—, así que releer
va por v1. La integración usa las dos versiones a propósito.

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

## Lo que queda abierto

- **¿`FALLIDO` es terminal?** Sigue sin decidirse (`adr/0022`). La emisión que murió el 16 de
  septiembre no llegó a producir eventos de rastreo, así que la pregunta sigue sin medirse.
- **La recolección** está desbloqueada del lado nuestro —el barrio viaja en la cotización y el envío
  lo hereda— y bloqueada del lado de la transportadora, cuyo conector de recolección respondió
  `ECONNREFUSED` tres veces seguidas a las 18:45. Programar una de verdad sigue pendiente.
- **El barrio del destino.** `Direccion` no lo tiene y el checkout no lo pide. Para la recolección
  basta el del origen —el campo que reclama la plataforma es "Shipper address2"—, pero el del
  destino mejoraría la entrega.
