# ADR 0021. Cotización de envío con Skydropx, precio base más flete

Fecha: 2026-09-08. Estado: aceptada. Supera a `adr/0012`, y retoma la idea del
puerto de `adr/0004`.

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

**Dato pendiente, no inventado:** el tratamiento del IVA sobre el flete que se le
cobra al comprador. `TODO: ¿el costo de envío cobrado lleva IVA? Consultar con el
contador.` Hasta que se resuelva, el flete se maneja como un valor en `Dinero`
sin desglose y el IVA de las líneas no cambia.
