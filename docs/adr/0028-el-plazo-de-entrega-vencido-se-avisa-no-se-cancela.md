# ADR 0028. Un plazo de entrega vencido se avisa, no se cancela

Fecha: 2026-09-10. Estado: aceptada.

## Contexto

Los términos publicados prometen dos cosas en la sección "Envío y entrega":

> No pactamos contigo un plazo de entrega distinto del legal, así que aplica el
> término de treinta (30) días calendario contados desde la confirmación del
> pago. [...] Si no entregamos dentro del plazo aplicable, puedes terminar el
> contrato y recuperar tu dinero.

Lo segundo tenía código detrás desde el bloque del retracto: `CancelarPedido`
acepta `MotivoCancelacion.PLAZO_INCUMPLIDO`, registra el reintegro y manda el
correo. Lo primero no tenía nada. Las dos únicas tareas programadas del sistema
eran la purga de carritos y la conciliación de Wompi, así que **nadie miraba el
vencimiento**: un pedido pagado y sin despachar incumplía en silencio y quien
compró se enteraba solo si preguntaba.

Lo levantó la revisión adversarial de los tres bloques sin fase, el 10 de
septiembre de 2026, y quedó anotado como lo único abierto que no era de la
Fase 7.

## Decisión

Una tarea programada revisa cada doce horas los pedidos vivos sin entregar, y a
los que pasaron de los treinta días calendario **les escribe al comprador y no
hace nada más**: no cancela, no reintegra, no cambia el estado del pedido y no
radica una PQR.

El artículo 18 de la Ley 1480 de 2011 le da al **consumidor** la opción de
terminar el contrato; no obliga al vendedor a deshacerlo por su cuenta. Puede
preferir esperar —el producto sigue siendo el que quería— y cancelarle el pedido
sin preguntarle sería decidir por él, además de liberar un inventario que quizá
no había que liberar. Quien decide es una persona: el comprador respondiendo, o
el panel; y entonces sí corre `CancelarPedido`, que ya existe.

Tampoco radica una solicitud de atención, por el mismo motivo por el que
`CancelarPedido` no la radica: la bandeja de PQR es de peticiones del comprador
con su plazo de respuesta corriendo, y esto es el negocio avisando de algo suyo.
Meterlo ahí llenaría de ruido la lista de lo que hay que responder.

Tres decisiones más, que son las que tienen filo:

**El plazo cuelga del historial, no de una columna.** `PlazoDeEntrega` cuenta
treinta días calendario desde el día siguiente a que el contrato quedó en firme,
y esa fecha se lee del historial del pedido: el registro de `PAGADO` para pago en
línea y transferencia, el de `CONFIRMADO_CONTRAENTREGA` para contraentrega —ahí
se celebra el contrato y no hay ninguna confirmación de pago que esperar, porque
se paga al recibir—. Es el mismo razonamiento de `Pedido.fechaDeEntrega()`: el
historial no se sobrescribe nunca, y una columna aparte sería una segunda verdad
capaz de divergir sin que nada avise. Lo único que sí se guarda es
`aviso_plazo_entrega_enviado_en`, que no se deduce de ningún estado: es el hecho
de que el correo salió, y de él depende no repetirlo cada doce horas.

**Días calendario, no hábiles, así que no hay `CalendarioHabil`.** A diferencia
del retracto, de las PQR y de la reversión, aquí ni los fines de semana ni los
festivos empujan el límite. Por eso `PlazoDeEntrega.verdicto` nunca devuelve
`INDETERMINADO`: sin festivos de por medio, "pasó el límite" siempre es una
afirmación segura.

**Cubre también los despachados sin entregar.** El plazo legal corre hasta la
entrega, no hasta el despacho, así que dejarlos fuera abriría un hueco real:
treinta días con la mercancía en tránsito siguen siendo un incumplimiento. El
riesgo es el contrario y está asumido a sabiendas: como hoy la entrega la marca
una persona en el panel, un despachado vencido suele ser un descuido de
operación, y el correo le llegaría a alguien que ya tiene el producto en la mano.
Por eso el aviso de ese caso lleva un párrafo propio que no acusa a nadie —"tu
pedido ya salió y va en camino; si ya lo recibiste, escríbenos y lo
corregimos"—. Con el seguimiento de la Fase 7 (`ADR-0022`), la entrega dejará de
marcarse a mano y ese párrafo perderá su razón de ser.

## Alternativas

**Cancelar y reintegrar automáticamente.** Es menos código —la tarea llamaría a
`CancelarPedido` y ya— y cierra el incumplimiento sin que nadie intervenga. Se
descartó porque decide por el comprador algo que la ley le dejó a él, y porque
un reintegro automático sobre un contraentrega entregado y sin conciliar es
justo el camino de doble pérdida que el tope de reintegro vino a cerrar.

**Solo una alerta en el panel, sin escribirle al comprador.** Evita el falso
positivo del despachado sin marcar. Se descartó porque el incumplimiento es del
negocio y el enterado tiene que ser quien lo sufre: una alerta que solo ve quien
incumplió no es un aviso, es una nota.

**Vigilar solo los pedidos sin despachar.** Más cómodo y sin falsos positivos,
pero deja fuera el caso en que la transportadora se queda con la mercancía, que
es exactamente cuando el comprador necesita saber que puede terminar el
contrato.

## Consecuencias

- Migración `V31`: una columna nueva en `pedido` y un índice parcial sobre los
  que todavía no tienen aviso.
- Un puerto nuevo en `RepositorioPedidos`, con filtro grueso: acota por
  `creado_en`, que siempre es anterior o igual al inicio del plazo, y la última
  palabra la tiene el dominio sobre la fecha real del historial.
- Seis textos nuevos en `correos_es.properties` y `correos_en.properties`.
- `PLAZO_ENTREGA_VIGILANCIA_INTERVALO_HORAS`, doce por omisión. No es un dato de
  negocio: el treinta vive en `PlazoDeEntrega`.
- El aviso se marca y se guarda **antes** de enviar el correo. El adaptador de
  producción se traga los fallos de envío (ver `EnviadorDeCorreo`), así que el
  orden contrario dejaría al pedido sin marcar y el vigilante volvería a
  escribirle cada doce horas. Un aviso perdido es mejor que uno repetido, y
  mientras no exista la bandeja de salida que ese puerto pide, no hay una tercera
  opción.
