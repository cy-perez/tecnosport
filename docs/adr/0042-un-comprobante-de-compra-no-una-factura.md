# ADR-0042 — Un comprobante de compra, no una factura

Fecha: 2026-09-18
Estado: aceptado
Relacionados: `adr/0028`, `adr/0041`, `docs/08-seguridad-legal.md`,
`docs/09-plan-de-arranque.md`

## Contexto

El encargo fue: *"para dar mayor fiabilidad a las compras, en cada compra expidamos una factura que
será enviada al comprador a su correo registrado"*.

El objetivo —que comprar en el sitio dé confianza— es correcto y hasta hoy no estaba cubierto: **el
sistema no le mandaba al comprador ningún correo al comprar**. Los siete correos transaccionales
cubren el retracto, la cancelación, el despacho, el plazo vencido, la PQR y las dos cosas de la
cuenta. De la compra misma, nada: quien pagaba veía la pantalla de estado y no recibía un solo
renglón.

Lo que no se podía hacer literalmente es la palabra *factura*, y no por una preferencia de diseño.

## La puerta de una sola dirección

`adr/0041` dejó establecido que el negocio es una persona natural no responsable de IVA. De ahí
salen dos cosas encadenadas:

1. **Hoy no está obligado a facturar.** Las personas naturales de los parágrafos 3 y 5 del art. 437
   del Estatuto Tributario, mientras cumplan la totalidad de sus condiciones, están entre los no
   obligados a expedir factura — art. 1.6.1.4.3 del Decreto 1625 de 2016, desarrollado por el art. 8
   de la Resolución DIAN 000165 de 2023.
2. **Y si decidiera facturar, dejaría de poder no hacerlo.** El parágrafo 1 de ese art. 8 lo dice
   sin rodeos: los no obligados *"que opten por expedir factura de venta y/o documento equivalente
   **se consideran para efectos tributarios obligados a facturar**"*, con los requisitos de la ley y
   el reglamento — habilitación como facturador electrónico, resolución de numeración, validación
   previa de la DIAN.

O sea que emitir una factura no es una funcionalidad: es un compromiso tributario permanente, con un
proveedor tecnológico detrás y una sanción cuando falte una. Y la parte que lo vuelve irreversible
en la práctica es que **no hay vuelta atrás por decisión propia**.

## Decisión

**Se manda un comprobante de compra, y el documento dice que no es una factura de venta.**

Lleva lo que hace falta para lo que el comprador necesita de verdad: el número del pedido, la fecha,
qué llevó con su SKU y su importe, el subtotal, el flete, el total, cómo paga, cómo se le entrega,
quién le vendió —con NIT, dirección y contacto, que es información obligatoria del proveedor bajo la
Ley 1480— y el enlace a su pedido.

Y lleva la frase que evita la confusión más cara: que **ese documento no es una factura**, con la
norma que explica por qué no hay una.

## Por qué es una tarea programada y no una llamada en cada camino

Un pedido queda en firme por **cuatro caminos**: la contraentrega verificada, el pago en línea
aprobado por el webhook, ese mismo pago descubierto por la conciliación, y la transferencia manual
conciliada a mano. Colgar el envío de cada uno significa que los cuatro tienen que acordarse, y que
un quinto camino futuro nazca sin comprobante y nadie lo note.

Como tarea, la regla se enuncia una sola vez y sobre el estado: **todo pedido en firme tiene su
comprobante**. Reusa el mecanismo ya probado del vigilante del plazo de entrega —consulta por la
marca en nulo, reclamo condicional por pedido, una escritura comprometida por sí misma— que existe
justamente porque bajo carga hay varias instancias corriendo la misma tarea.

De paso gana algo que no era el objetivo y vale: **mandar correos deja de colgar del camino del
dinero**. Un fallo de SMTP no tiene por dónde estropear un pago que se está aplicando.

El precio es que llega con el retraso de la tarea —dos minutos por omisión— y no en el mismo
segundo. Se acepta: el acuse de que el pedido entró ya lo da la pantalla de estado al volver del
checkout; el comprobante es el soporte, no el acuse.

## Consecuencias

- Una tarea programada más, la novena, y una columna `comprobante_enviado_en` que solo escribe su
  reclamo condicional.
- Los textos viven en `correos_es.properties` y `correos_en.properties`, con sus llaves en
  `TextoDeCorreo`, como los otros siete.
- **El NIT, el correo y el teléfono del negocio pasan a vivir también fuera de los JSON del sitio**,
  porque el comprobante identifica al vendedor. Por eso `npm run datos-negocio` ahora barre también
  esos dos `.properties`: una copia que el guardián no mira es exactamente el agujero por el que el
  celular estuvo mal en cuatro sitios durante una fase entera.
- El numeral 6 de los términos publicados promete el comprobante y dice que no es una factura. Es
  una promesa que el sistema cumple desde el mismo commit.

## Confirmado: el comprobante es el documento de la venta

Cuando se escribió este ADR quedaba una pregunta abierta que no era de programación — *qué recibe
quien compra como soporte de su compra* — y se dejó para el contador.

**Contestada el 18 de septiembre de 2026 por el dueño del negocio: el comprobante por correo es ese
documento**, precisamente porque los productos se manejan como no responsable de IVA. No hay un
segundo documento detrás ni se está esperando a nada: lo que el sistema manda es lo que la venta
entrega.

No cambia una línea de código —es lo que la tarea ya hace y lo que el numeral 6 de los términos ya
promete—, y esa es justamente la razón de escribirlo: pasa de ser una omisión con una nota al lado a
ser una decisión con dueño y con fecha. La distinción vale para el día que alguien pregunte por qué
no hay factura: la respuesta no es "no llegamos a decidirlo", es "se decidió esto, por esta norma".

**Y no dice "confirmado por el contador", porque ninguno lo revisó.** Lo confirmó quien responde por
el negocio, con las normas de este documento delante. Sigue en pie lo único que un contador tiene que
mirar, que es otra cosa: si la condición de no responsable se cumple contra los topes del parágrafo 3
del art. 437 (`adr/0041`).

## Una corrección a la `V51`, que no se puede hacer en la `V51`

El comentario del índice parcial de esa migración dice que las filas con la marca en nulo "son pocas
y siempre recientes", **y es falso**: los pedidos que nunca llegan a estar en firme —`CREADO`,
`PAGO_PENDIENTE`, `PAGO_FALLIDO`, `CANCELADO`— no reciben la marca jamás y se quedan en ese índice
para siempre. Lo levantó una revisión adversarial el mismo día.

No es un defecto de rendimiento: la consulta sigue siendo selectiva porque filtra por `estado`. Y la
corrección queda aquí y no allá **porque esa migración ya corrió**, y Flyway valida el checksum: una
frase mejor en un archivo aplicado es un despliegue detenido. Mismo criterio que el `TODO` de la
`V32`.

## Lo que NO resuelve, y conviene tenerlo a la vista

**Si el correo no sale en silencio, el comprador se queda sin su comprobante y nadie se entera.** El
reclamo se pone *antes* de mandar, porque es lo que impide que dos instancias manden dos correos. Un
fallo **que lanza** ya no cuesta nada: la revisión adversarial del mismo día lo señaló y ahora se
devuelve el reclamo y la vuelta siguiente lo reintenta, contándolo aparte en el registro. Lo que
sigue sin cubrirse es el fallo **silencioso**: el adaptador de producción se traga los de SMTP sin
relanzarlos —está escrito en el javadoc de `EnviadorDeCorreo` desde que otra revisión lo destapó—,
así que ahí la marca queda puesta y el correo no salió.

Eso significa que el número de "enviados" del registro dice *se intentó*, no *llegó*, y así queda
escrito en `ResultadoComprobantes`. La salida de verdad es la misma que aquel javadoc ya nombra: una
bandeja de salida con reintentos. Sigue sin existir, y ahora tiene un motivo más para existir —esto
es una obligación frente al comprador, no un aviso interno.

**Y no cubre a quien pida una factura de verdad.** Si un comprador la necesita para deducir el
gasto, la respuesta hoy es que no hay, y la razón está escrita arriba. Es el límite conocido de la
decisión confirmada, no un cabo suelto: el día que alguien la pida con insistencia, lo que hay que
volver a tomar no es una decisión de producto, es la del parágrafo 1 del art. 8, con todo lo que
arrastra.

## Qué lo reabre

1. **Que el negocio pase a ser responsable de IVA** (`adr/0041`), que es lo esperable si crece: ahí
   la obligación de facturar deja de ser opcional y este comprobante se convierte en el borrador de
   lo que tendrá que emitir de verdad.
2. **Que se decida facturar voluntariamente**, sabiendo que la decisión no se deshace.
3. **Que exista la bandeja de salida de correos**: entonces este envío deja de poder perderse en
   silencio, y la promesa del numeral 6 pasa a ser tan fuerte como su redacción.
