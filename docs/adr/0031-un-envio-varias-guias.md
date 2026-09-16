# ADR 0031. Un envío, varias guías

Fecha: 2026-09-16. Estado: aceptada. Corrige la enmienda 4 de `adr/0022` y el
modelo de `Envio` que describe `docs/02-modelo-datos.md`.

## Contexto

Las sondas contra el sandbox de Skydropx midieron dos cosas que juntas rompen un
supuesto que nadie había escrito (`docs/13-skydropx-capacidades.md` §6.3):

- **`multi_packages_enabled: false` en los siete servicios de la cuenta.** Ninguna
  transportadora colombiana de las que integra Skydropx admite multipaquete.
- **Con dos bultos, la cotización cambia a `shipment_creation_type:
  multishipment` y cobra el doble** — 10.540 con uno, 21.080 con dos, 31.620 con
  tres, exactamente el múltiplo.

La regla de **un bulto por variante** ya estaba tomada. Las dos cosas juntas dicen
que **un pedido de dos variantes son dos guías**, cada una con su número, su cobro
y su propio hilo de eventos: se recogen, se entregan y se devuelven por separado.

El modelo asumía una y solo una: `Envio` guardaba `transportadora`, `guia` y
`costo_envio` como columnas escalares, y `evento_seguimiento` colgaba del envío.

## Decisión

**El `Envio` se queda como el despacho del pedido, y gana una colección de
`GuiaEnvio`.** El reparto de responsabilidades es el que ya estaba implícito:

- **`Envio` es la unidad de dinero del pedido.** La comisión de recaudo es una por
  pedido —la transportadora consigna el total, no bulto por bulto— y la fecha de
  conciliación también. Ahí se quedan.
- **`GuiaEnvio` es la unidad de rastreo y de costo.** Transportadora, número,
  lo que esa guía nos cuesta, y sus eventos. `Envio.costoEnvio()` es la **suma**,
  que es contra lo que se lee el margen del pedido.

**Los eventos cuelgan de la guía, no del envío.** Es lo único que no admitía medias
tintas: la transportadora reporta el movimiento de un paquete, y mezclar los dos
rastros le diría al comprador que le entregaron algo que sigue en camino. En base
de datos, `evento_seguimiento.guia_id`, con la unicidad de idempotencia rehecha
sobre `(guia_id, id_externo)`.

**El número de guía es único en toda la tabla**, no por envío. Es lo que
`buscarPorGuia` ya daba por cierto para resolver un evento del webhook sin
preguntar la transportadora, y hasta hoy no lo garantizaba nadie.

**La conciliación pregunta por guía viva, no por envío.** Un envío entra en el lote
cuando **al menos una** de sus guías lleva callada desde el corte y no ha terminado.
Medirlo sobre el envío entero lo daría por acabado con el primer paquete entregado
y dejaría el segundo sin conciliar para siempre. El tope por corrida pasa a contar
guías, porque el límite de dos peticiones por segundo del proveedor se gasta por
llamada y un envío de tres bultos son tres.

**Manda la primera guía para mover el pedido.** El primer `delivered` marca
`ENTREGADO` y el primer `in_return` marca `RECHAZADO_EN_ENTREGA`, igual que antes;
las demás guías registran su evento y no mueven nada, porque la guarda por estado
del pedido ya lo impide. Es la regla que menos código pide y la que **peor
describe la realidad**: un pedido puede quedar entregado con un paquete todavía en
tránsito, y el plazo de retracto —que corre desde la entrega— arranca ahí.
**Se toma con eso sabido.** Si el negocio ve entregas parciales en la práctica,
las dos salidas están descritas abajo.

## Alternativas

**Un `Envio` por bulto.** Cada paquete, su agregado. Lo más fiel a lo que hace la
transportadora, pero parte en dos lo que el pedido tiene de una sola pieza: la
comisión de recaudo, la fecha de despacho y el margen quedarían repartidos entre
filas que habría que volver a sumar en cada lectura, y `buscarPorPedidoId`
devolvería una lista donde todo el código de hoy espera uno.

**Consolidar en un solo bulto.** Declarar un paquete con la suma de pesos y el
mayor de los tamaños, y seguir con una guía. Es la que menos toca, y miente en el
único sitio donde mentir cuesta plata: las medidas son las que la transportadora
cobra, y una caja inventada se cotiza mal en los dos sentidos.

**Entrega parcial en `EstadoPedido`** (un `ENTREGADO_PARCIAL` y un
`DEVUELTO_PARCIAL`). Es lo que de verdad pasa, y es lo más caro: mover el grafo del
pedido arrastra la máquina de estados, el historial, el panel, el retracto y la
garantía. Queda como la salida si las entregas parciales dejan de ser teoría.

**Todas o ninguna, con alerta** (mover el pedido solo cuando todas las guías
coinciden, y levantar revisión manual cuando no). Más conservadora que la elegida
y sin tocar `EstadoPedido`. Se descartó a favor de "manda la primera".

## Consecuencias

`DespacharPedido` recibe una lista de guías, y el panel un formulario que deja
agregar y quitar paquetes. El correo de despacho gana una segunda versión
—`pedido.despacho.cuerpo_varias`— que **dice cuántos paquetes son y que pueden
llegar en días distintos**: quien recibe uno de dos sin saberlo cree que le faltó
media compra y escribe a atención.

La pantalla de estado muestra las guías todas. El seguimiento público sigue sin
exponer costo ni comisión, y ahora la prueba que vigila esa fuga cuenta **dos**
niveles de llaves: el costo por guía es margen, y sin la cuenta de adentro se
colaría sin que la de afuera se enterara.

Queda abierto lo que este ADR no decide: **quién arma los bultos**. Hoy el despacho
es manual y quien despacha escribe las guías que emitió; el día que la emisión sea
automática habrá que decidir cómo se agrupan las variantes en paquetes, y eso es
una decisión de operación antes que de código.
