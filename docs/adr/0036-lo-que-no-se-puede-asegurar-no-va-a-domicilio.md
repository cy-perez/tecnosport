# ADR-0036 — Lo que no se puede asegurar no va a domicilio, y se dice

Fecha: 2026-09-17
Estado: aceptado
Relacionados: `adr/0021`, `adr/0035`, `docs/13-skydropx-capacidades.md` §6.13

## Contexto

`ADR-0035` cerró el extremo de abajo del valor declarado. La medición de `docs/13` §6.13 cerró el
de arriba, y resultó ser igual de duro: **el rango es [10.000, 5.000.000] y los dos extremos se
validan por bulto, en la entrada, antes de que ninguna transportadora vea nada.** 5.000.000 cotiza;
5.000.001 responde `422 "El valor declarado debe ser menor o igual a 5000000"`.

Los dos extremos rompen igual —tumban la cotización entera— pero **no se arreglan igual**:

- Abajo se puede ajustar sin perjudicar a nadie. Declarar un cable de 8.000 en 10.000 no le quita
  nada al comprador ni al negocio: son diecisiete pesos de seguro en la única tarifa que cobra por
  el declarado, y la transportadora responde por más, no por menos.
- **Arriba, ajustar es regalar la diferencia.** Declarar un celular de 8.000.000 en 5.000.000 hace
  que la transportadora responda hasta el tope si se pierde, y los tres millones restantes los pone
  el negocio. No es un ajuste de borde: es autoasegurarse sin decirlo.

Y el comportamiento de hoy ya decide, solo que en silencio: ese artículo no se puede cotizar, el
comprador recibe "no se pudo cotizar, intenta más tarde", y reintentar no va a funcionar nunca. El
sistema ya dejó de vender ese artículo a domicilio. Lo único que no hace es decirlo.

## Decisión

**Un artículo cuyo valor supera el máximo asegurable no se despacha a domicilio, y el checkout lo
dice con esas palabras.** El carrito que lo lleve cae al camino que ya existe —recogida en el
punto— pero con su propio mensaje, nombrando el artículo que lo causó.

El texto, decidido por el negocio y no redactado aquí a medias:

> Este artículo solo está disponible para recogida en nuestro punto, porque su valor supera el
> máximo que la transportadora puede asegurar.

### Dónde se detecta

En `ArmadorDeBultos`, junto al piso y por la misma razón: es el único sitio por el que pasan los dos
caminos —cotizar y emitir—, y ahí el bulto todavía sabe de qué variante viene. La excepción lleva
**el nombre y el id de las variantes culpables**, porque un mensaje que solo puede decir "algo de tu
carrito" es casi tan inútil como el "intenta más tarde" que vino a reemplazar.

### El carrito entero cae a recogida, no solo el artículo caro

Un carrito con un celular de 8.000.000 y una camiseta no se parte. Despachar la camiseta y dejar el
celular para recoger serían dos entregas de un mismo pedido, con dos momentos y dos estados, y este
sistema no tiene pedidos parciales — `Pedido` tiene un envío y `adr/0031` ya decidió que varias
guías son de un solo envío, no de varios. Partirlo es un cambio de modelo, no un detalle de esta
decisión.

## Alternativas rechazadas

- **Recortar el declarado al tope y despachar igual.** Es la única que mantiene la venta a domicilio,
  y por eso se consideró en serio. Se descarta porque traslada al negocio una pérdida que no está
  presupuestada ni medida: un solo paquete perdido de 8.000.000 cuesta tres millones, y nadie decidió
  que el negocio asegure por su cuenta la diferencia. Si algún día se decide, es un cambio de esta
  decisión y se escribe como tal — no algo que aparezca porque un `if` se movió.
- **Dejarlo como está.** Es lo que hay hoy: el artículo tampoco se vende, pero el comprador cree que
  el problema es temporal y el negocio no se entera de nada.
- **Partir el pedido** en lo que va a domicilio y lo que se recoge. Ver arriba: es un cambio de
  modelo.

## Consecuencias

- **La gama alta se vende solo con recogida en el punto**, y eso es una decisión comercial visible,
  no un fallo. Queda medible: si duele, se sabrá por los pedidos que no se cierran, no por un log.
- **El máximo es configuración** (`SKYDROPX_VALOR_DECLARADO_MAXIMO`, 5.000.000), como el mínimo, y
  por el mismo motivo: es de la plataforma y se iría con ella. Arrancar con un mínimo mayor o igual
  al máximo revienta el arranque en vez de esperar a la primera cotización.
- **La consulta de métodos de pago tiene que tratarlo como "sin envío"**, igual que la falta de
  cobertura. Si no, pedir los métodos de pago de ese carrito devolvería un error en vez de una
  respuesta, y el comprador vería el checkout roto en lugar de la recogida.
- **El código de error es nuevo y público** (`docs/03-api.md`). El checkout necesita distinguirlo de
  `ENVIO_SIN_COBERTURA` para elegir el texto: los dos terminan en recogida, pero uno se arregla
  cambiando la dirección y el otro no se arregla de ninguna manera.
