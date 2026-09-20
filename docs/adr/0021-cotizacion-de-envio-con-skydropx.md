# ADR 0021. Cotización de envío con Skydropx, precio base más flete

Fecha: 2026-09-08. Estado: aceptada. Supera a `adr/0012`, y retoma la idea del
puerto de `adr/0004`. **Superada en parte por `adr/0046`** (19 de septiembre de
2026): el paquete por variante deja de ser obligatorio, porque de "no se puede
cotizar" no se sigue "no se puede vender" — se vende con recogida en el punto.
Todo lo demás de este ADR sigue vigente.

## Contexto

`adr/0012` decidió no cotizar: cada producto se publicaba con un precio que ya
incluía un valor de envío estándar, igual en todo el país. Eso simplificó el
checkout —sin código DANE del destino, sin peso ni dimensiones por variante— a
cambio de dos cosas que el negocio ya no acepta:

1. **El comprador de Medellín subsidia al de Leticia.** Un flete promedio metido
   en el precio encarece lo que se vende cerca y regala lo que se vende lejos.
2. **El margen depende del destino y nadie lo ve al vender.** El costo real se
   registraba en `Envio` después del despacho, cuando ya no se podía decidir
   nada.

El negocio decidió integrar **Skydropx Colombia**, un agregador con una sola
integración que cubre varias transportadoras (Servientrega, Coordinadora, Envía,
TCC, Deprisa, Inter Rapidísimo y otras). Es la estrategia 2 de las tres que
`adr/0004` había dejado planteadas.

## Decisión

**El precio publicado de un producto vuelve a ser un precio base**, con IVA
incluido y **sin flete**. El costo de envío se cotiza contra Skydropx con el
destino real y se cobra aparte, informado por separado en el resumen del pedido
antes de pagar.

Eso no es solo una preferencia comercial: el **artículo 50 de la Ley 1480 de
2011** exige que, antes de finalizar la transacción, el comprador vea el precio
individual de cada bien, el precio total, **los costos adicionales de envío
informados de forma adecuada y separada**, y la suma total a pagar. El modelo
nuevo es el que la norma describe; el precio-todo-incluido era la excepción que
había que justificar.

**Renace el puerto `CotizadorEnvio`** en `application`, con una sola
implementación real, `SkydropxClient`, en `infrastructure`. El dominio no sabe
que existe Skydropx.

**El servidor elige la tarifa, no el comprador.** De todas las que devuelve la
cotización para ese destino y ese paquete, se toma **la más económica**. El
checkout muestra un único costo de envío con su plazo estimado. Un selector de
transportadoras es un paso más de checkout, una decisión más para quien solo
quiere comprar una camiseta, y una tarifa más que habría que blindar contra
manipulación del cliente. Si más adelante el negocio quiere ofrecer "más rápido
por más plata", se reabre esta decisión: el puerto ya devuelve la lista completa.
**Matizado el 16 de septiembre**: la más económica puede ser una que no recoja
por API; ver la corrección al final.

**El costo de envío se congela en el pedido**, igual que el precio y el nombre de
cada línea (`docs/02-modelo-datos.md`, "Congelado del pedido"). Se guarda la
tarifa elegida con su identificador de Skydropx, la transportadora, el servicio,
el valor cobrado, el plazo estimado y el vencimiento de la tarifa. El cliente
nunca envía el costo de envío: llega en la respuesta del servidor y se recalcula
antes de cobrar (regla dura #7).

**La cotización es asíncrona y así se consume.** Skydropx crea la cotización
(`POST /quotations`) y las tarifas van llegando de a poco; hay que consultar
(`GET /quotations/{id}`) hasta que `is_completed` sea verdadero, con un tope de
tiempo y de intentos. Las tarifas valen 24 horas. No se bloquea el hilo de la
petición más allá de ese tope: vencido, se trata como cotización fallida.

**Sin tarifa no hay envío a domicilio: solo recogida en el punto.** Si Skydropx
no responde, si el destino no tiene cobertura o si ninguna transportadora
devuelve tarifa, el checkout **no** inventa un valor ni aplica una tarifa de
respaldo. Ofrece únicamente la recogida en el punto de Medellín y lo explica. Es
el mismo criterio *fail-closed* con el que arrancó la cobertura de contraentrega:
cobrar un flete inventado es despachar a pérdida o cobrarle de más al comprador,
y las dos son peores que no vender.

**La recogida en el punto exonera el costo de envío.** Costo de envío en cero,
sin cotización, sin dirección de entrega y sin guía. Ya existía como
`TipoEntrega.RECOGIDA_EN_PUNTO` y sigue siendo el camino más simple del sistema.
Lo nuevo es que ahora **ahorra plata de verdad** y por eso hay que decirlo en el
checkout, junto al costo que se evita.

**Peso y dimensiones vuelven a ser obligatorios por variante.** Es lo que
`adr/0004` ya advertía y `adr/0012` había eliminado: sin paquete no hay
cotización. Una variante sin peso ni dimensiones no se puede publicar, y el
catálogo existente necesita relleno antes de encender la cotización.

## Alternativas

**Tarifa de respaldo configurable** cuando la cotización falla: el checkout no se
cae nunca, a costa de cobrar un número que el negocio tendría que inventar y que
se desactualiza solo. Descartada por eso; si se retoma, el número es un dato de
negocio, no una constante que le toque elegir a quien programe.

**Seguir con el costo estándar incluido y usar Skydropx solo para rotular**:
mantiene el checkout de hoy, pero deja intactos los dos problemas del contexto.

**API directa por transportadora** (estrategia 3 de `adr/0004`): mejor tarifa
negociada, una integración por transportadora y un contrato comercial por cada
una. No se justifica con el volumen de la fase 1.

## Consecuencias

El checkout gana un paso real: **la dirección de entrega se necesita antes del
método de pago**, porque sin ciudad no hay cotización. `ResumenPage` ya pide la
dirección, así que el orden actual del recorrido sobrevive; lo que cambia es que
ese paso ahora hace una llamada al servidor y puede fallar.

El carrito **no cotiza**. Mostrar "envío desde $X" en la página del carrito
exigiría un destino que todavía no existe, y `ADR-0011` no aplica aquí: no hay
nada que precargar en el resolver.

Los textos de la vitrina y de los documentos legales que dicen que el precio
incluye el envío **quedan falsos** y se corrigen en el mismo commit que enciende
la cotización, no antes ni después (`docs/12-legales-de-envio.md`).

El costo real que registra `Envio` desde `adr/0013` deja de ser un dato huérfano:
ahora se compara contra el flete cobrado y el margen del pedido se puede leer.

~~**Dato pendiente, no inventado:** el tratamiento del IVA sobre el flete que se le
cobra al comprador. `TODO: ¿el costo de envío cobrado lleva IVA? Consultar con el
contador.`~~ **Cerrado el 18 de septiembre de 2026 por `adr/0040`: el flete cobrado
no se grava.** El flete se sigue manejando como un valor en `Dinero` sin desglose y
el IVA de las líneas no cambia — que era el comportamiento provisional y ahora es el
decidido. Es una decisión de negocio sin concepto de contador, y `adr/0040` escribe
qué la reabre.

## Corrección del 16 de septiembre de 2026, medida contra el sandbox

Tres sesiones de sondas contra la cuenta de pruebas
(`docs/13-skydropx-capacidades.md`, §6) tocaron cuatro cosas que este ADR daba
por sabidas. Ninguna lo contradice de frente; las cuatro le quitan una certeza.

**1. "La más económica" dejó de ser una regla incondicional.** Cada tarifa
declara si la transportadora recoge por API en el campo `pickup`: es `true` en
Coordinadora, Servientrega e Inter Rapidísimo, y `false` en 99 minutes y Envía,
que solo recogen por soporte. Y 99 minutes es de las que más cotizan. Mientras el
despacho siga siendo "alguien lleva los paquetes al punto", la regla de arriba se
sostiene entera. El día que se elija recolección programada —decisión abierta #1
de `docs/13`—, la regla pasa a ser **la más barata de las que recogen**, que no
es la más barata, y el margen del pedido lo paga. No se cambia hoy porque la
decisión no está tomada, pero el servidor que elige tarifa tiene que poder
filtrar por ese campo, no solo ordenar por precio.

**2. La dirección necesita barrio, y este ADR no lo pidió.** El barrio viaja a
Skydropx como `area_level3`, y **solo se hereda desde la cotización**: el cuerpo
de emisión no lo declara y lo descarta en silencio. Sin barrio en el origen,
programar la recolección responde `422 Shipper address2 not valid: null`. Hoy
`Direccion`, el record del dominio, tiene departamento, ciudad, dirección e
indicaciones con sus códigos DANE, y **no tiene barrio**: agregarlo toca el
record, el formulario del checkout, los DTO de presentación y los textos de
Transloco en los dos idiomas. Está medido para la dirección de origen. Para el
destino no hay medición —la entrega nunca se ejerció— aunque las transportadoras
colombianas suelen pedirlo. ~~`TODO: ¿el destino exige barrio para entregar, o
solo el origen para recoger?`~~ **Resuelto el 17 de septiembre de 2026
(`docs/13` §6.14): lo exige en las dos puntas, y en ese orden.** Con los dos
barrios vacíos, `POST /pickups` se queja del origen; con el origen puesto y el
destino vacío, se queja del destino con el mismo mensaje cambiando la palabra que
nombra la punta. El error se mueve exactamente con el barrio que falta. Queda una
rendija honesta: la fila que aísla el destino es de Envía y las demás de
Servientrega, y cerrarla del todo exigía una emisión que costaba 8.200 de los
10.088 de saldo. No se gastó porque no cambia ninguna decisión.

Lo que sí cambia una decisión es la consecuencia. `Direccion.barrio` ya existe y
el checkout lo pide **sin exigirlo**, así que un comprador que lo deje vacío
produce una guía que no se podrá recoger por API el día que el conector de la
transportadora vuelva. **Se decide dejarlo opcional**: exigirlo le cobra fricción
a cada comprador de hoy por una capacidad que todavía no existe y que no depende
de nosotros. Y queda fijada la regla de ese día, para no volver a pensarla: **sin
barrio de destino, esa guía se recoge a mano.**

**3. El valor declarado va dentro de cada bulto, y tiene un mínimo que nadie ha
decidido.** `declared_amount` es un campo de cada `parcel`, no de la cotización;
el mapeador lo mandaba fuera y con otro nombre, y por eso durante días pareció
que Servientrega, Envía y Coordinadora no cotizaban Colombia. Corregido y con
prueba. Lo que queda no es técnico: el sandbox **exige un mínimo de 10.000 COP
por bulto**, así que un pedido de una funda de 8.000 tendría que declarar más de
lo que vale, y lo declarado es lo que la transportadora indemniza si se pierde.
~~`TODO: ¿qué valor se declara cuando la mercancía vale menos del mínimo de
10.000 por bulto?`~~ **Decidido el 17 de septiembre de 2026 en `ADR-0035`: el
bulto que declara menos se eleva al mínimo asegurable**, y se eleva en
`ArmadorDeBultos` y no en el mapeador, para que el bulto que circula por
`application` diga lo que de verdad se declara. Se declara entonces más que la
factura cuando hay varias unidades baratas —tres cables de 8.000 son 30.000
declarados contra 24.000 facturados—, que es el precio de cumplir el mínimo.
`ADR-0036` decide el otro extremo del rango: lo que supera el techo no va a
domicilio, y el checkout lo dice con el nombre del artículo.

**4. Entre pedir la guía y tener la guía hay minutos, y puede no haberla.** La
emisión responde `202` sin número de guía y el envío queda en un estado no
terminal —`in_progress`, `pending` o `creation_waiting`— durante minutos: la
medición del 16 de septiembre tardó 2 min 22 s en llegar a `success`. Y puede
terminar en `error` con el saldo reembolsado: pasó tres veces en una noche, por
fallas de las transportadoras y no del cuerpo enviado. Este ADR solo congela la
tarifa; **la frase que hay que corregir vive en `ADR-0022`** —"`DespacharPedido`
deja de recibir la guía escrita a mano: la pide a Skydropx y la guarda"—, porque
no hay nada que guardar todavía. Se anota aquí porque es la cotización congelada
la que se estaría dando por consumada: **un pedido no se marca despachado con la
respuesta de creación**, y hace falta una rama para el `error` que lo devuelva a
la cola en vez de dejarlo con una guía que no existe.

**5. `is_completed: true` no significa que todas las transportadoras
contestaron.** Medido de casualidad y confirmado a propósito (`docs/13` §6.5): una
cotización volvió `is_completed: true` con la tarifa de 99 minutes en `pending`,
y al releer esa misma cotización un minuto después la tarifa estaba en
`price_found_external`, con precio. Este ADR sondea hasta `is_completed` y ahí se
planta, y `MapeadorCotizacionSkydropxV1` descarta toda tarifa que no venga en
`success`. Juntando las dos cosas, **una transportadora lenta se pierde en
silencio**: esa vez la que faltaba era la más cara y no cambiaba nada, pero nada
garantiza que la próxima no sea la más barata. La cotización no miente —la tarifa
aparece después— y el checkout no la ve.

El sondeo no se cambia aquí, y es a propósito, porque la decisión tiene dos filos.
Esperar a que no quede ninguna tarifa en `pending` alarga el checkout contra un
proveedor que ya es lento. No esperar cobra de más, y el comprador no tiene cómo
enterarse de que se le ofreció la segunda mejor tarifa. Hoy son ocho intentos con
un techo de diez segundos, elegido por lo que alguien tolera mirando un resumen de
pedido sin total, no por lo que el proveedor tarda en contestar.

`TODO (dato de negocio): cuántos segundos de más tolera el checkout con tal de no
perder una tarifa que todavía no ha contestado. Es el único número que falta para
cerrar esto, y no se inventa aquí.`
