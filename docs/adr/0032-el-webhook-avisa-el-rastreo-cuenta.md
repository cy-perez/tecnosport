# ADR 0032. El webhook avisa, el rastreo cuenta

Fecha: 2026-09-16. Estado: aceptada. Precisa el mecanismo de `adr/0022`, que daba por
hecho que el webhook traía el evento.

## Contexto

`ADR-0022` decidió que los eventos de seguimiento entran por dos caminos —el webhook
y la conciliación programada— y que los dos aplican **el mismo** evento por el mismo
componente. Lo que no estaba decidido es de dónde sale ese evento en cada camino,
porque cuando se escribió no se había visto ninguno.

Al ir a escribir el lector, con la documentación oficial delante y el rastreo ya
medido (`docs/13` §6.8), apareció el problema:

- **El cuerpo del webhook no trae identificador de evento ni fecha.** Trae
  `data.id` —que es el identificador del *paquete*, no del evento—, `status`,
  `tracking_number`, `tracking_url_provider`, `label_url`, `returned` y
  `returned_status`. Nada más.
- **El rastreo sí los trae**: cada evento viene con su `id` (UUID) y su `date`.

Y `EventoSeguimiento` necesita los dos. El identificador externo es lo que hace
idempotente el rastro —la unicidad `(guia_id, id_externo)` en base de datos— y la
fecha es cuándo ocurrió el movimiento, que no es cuándo nos enteramos y de la que
cuelgan plazos legales: el retracto corre desde la entrega.

Si cada camino fabricara su propia llave —un hash del cuerpo para el webhook, el UUID
para el rastreo—, **el mismo movimiento se registraría dos veces**, con dos llaves
distintas, y el comprador vería "Entregado" dos veces en la pantalla de su pedido.
Los efectos sobre el pedido no se duplicarían, porque la guarda por estado lo impide,
pero el rastro sí, y el rastro es lo que se lee el día de la reclamación.

## Decisión

**El webhook no aporta eventos: aporta la noticia de que hay que preguntar ya.**

- `LectorEventoDeEnvio` devuelve **solo el número de guía** del aviso, y descarta lo
  que no sea un evento de paquete —por la misma suscripción llegan órdenes,
  cotizaciones, tarifas, cargos extra y recolecciones—.
- `RecibirEventoDeEnvio` resuelve esa guía contra nuestros envíos y llama a
  `ConciliarGuia`, que consulta el rastreo y aplica lo que venga.
- `ConciliarGuia` es **el mismo objeto** que usa la tarea programada. Los dos caminos
  ya no se parecen: son el mismo código con distinto disparador.

**El rastreo es la única fuente del rastro.** Un solo formato de identificador, una
sola fecha, un solo mapeador que probar contra respuestas capturadas.

## Alternativas

**Derivar la llave del contenido en los dos caminos** —por ejemplo
`sha256(guía|estado|event_description)`— para que convergieran sin consultar. Se
descartó por dos motivos, y el segundo apareció midiendo: cuando la descripción llega
vacía —que es justo el caso de `picked_up` y `delivered`, medido— la llave se reduce
a `guía|estado`, y dos eventos del mismo estado (dos intentos de entrega, varios
tránsitos con el mismo texto) se colapsarían en uno, perdiendo historia real en
silencio. Y `description` y `event_description` **no son el mismo texto** entre el
rastreo y el webhook: el segundo llega en minúsculas. La convergencia que prometía
esa salida no era tan firme como parecía.

**Registrar el evento del webhook con la fecha de recepción.** Es inventar el dato
del que dependen los plazos. `EventoSeguimiento` separa `ocurrioEn` de `recibidoEn`
precisamente porque no son lo mismo; llenarlos con el mismo valor deshace esa
decisión el primer día.

**Leer el envío completo con `data.relationships.shipment.data.id`.** La relación
viene en el aviso y llevaría al envío, no al rastro. Habría que medir una forma más
—la de `GET /shipments/{id}`— para obtener lo mismo que ya da el rastreo. Queda
anotada como la salida si algún día hace falta resolver la transportadora de una guía
que no emitimos nosotros.

## Consecuencias

- **Una llamada más al proveedor por evento recibido**, contra un límite de dos
  peticiones por segundo. A este volumen no se nota. Si algún día se notara, la tarea
  programada sigue siendo la red: el webhook se puede apagar sin perder nada más que
  inmediatez.
- **Un webhook con el proveedor caído no aporta nada**, y está bien: es el caso que
  la conciliación programada ya cubría. El camino rápido puede fallar; el lento no.
- **Una guía de la que no conocemos el código de transportadora no se puede
  consultar por ninguno de los dos caminos** (`docs/13` §6.8). Hoy son las que teclea
  una persona en el panel. El desenlace tiene nombre propio —
  `SIN_CODIGO_DE_TRANSPORTADORA`— y se registra y se cuenta, en vez de parecer "sin
  novedad". Se cierra solo cuando la guía la emita el sistema.
- **El estado del aviso se ignora**, incluido el retorno. La documentación dice que
  durante una devolución `status` se queda en `in_return` todo el trayecto y el
  movimiento real viaja en `returned_status`, mientras que las suscripciones se
  siguen disparando con el estado operativo. Son dos vocabularios para lo mismo, y
  con esta decisión solo hay que entender uno.
- **Lo que falta para que el webhook aplique algo es el secreto del panel**
  (`SKYDROPX_SECRETO_WEBHOOK`), no código. Y falta comprobar contra un evento real
  dos cosas: que la firma cuadra y que el `tracking_number` está donde dice la
  documentación.
