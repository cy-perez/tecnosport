# ADR-0046: una variante sin medir se vende, pero solo con recogida

Fecha: 2026-09-19
Estado: aceptada
Reemplaza en parte a: `ADR-0021`

## Contexto

`ADR-0021` hizo obligatorio el paquete de la variante —peso y tres medidas— y la
`V32` lo llevó a la base como `NOT NULL`, negándose a propósito a rellenar por
defecto: *"un flete cobrado de menos se paga; una migración que falla se
arregla"*.

El argumento era correcto y sigue siéndolo: **sin peso ni dimensiones no hay
cotización de envío**. Lo que escondía es un salto que nadie miró de cerca:

> de *"no se puede cotizar"* no se sigue *"no se puede vender"*.

Se puede vender para **recogida en el punto**, que es un canal que este negocio
ya tiene, ya ofrece en el checkout y ya usa como salida cuando ninguna
transportadora cubre el destino (`ENVIO_SIN_COBERTURA`) o cuando un artículo vale
más de lo asegurable (`ADR-0036`).

El salto se hizo visible al cargar el primer catálogo real, el 19 de septiembre
de 2026:

- **Los fabricantes de celulares y tablets no publican las medidas de su caja.**
  Ni Motorola en su ficha oficial de soporte, ni Samsung, ni los agregadores. JBL
  sí las publica; ellos no.
- De los doce productos listos para publicar del primer lote, **siete se
  quedaban fuera** por un dato que no existe en ninguna fuente pública y que
  exige tener el producto en la mano con una báscula.

Un requisito que **bloquea la venta de todo un catálogo** para proteger el flete
de una parte de él está mal calibrado. La protección es real; el precio que
cobraba, no.

## Decisión

**El paquete de la variante pasa a ser opcional.** Una variante sin medir es un
estado legítimo del negocio: se vende, y solo se ofrece con recogida en el punto.

Lo que **no** cambia, y es la mitad importante de esta decisión:

1. **El objeto de valor `Paquete` sigue exigiendo las cuatro cifras mayores que
   cero.** Si existe, es válido. La diferencia entre *"no lo sé todavía"* (nulo) y
   *"mide cero"* (inválido) es justo la que hay que conservar: la segunda es la
   que cobra fletes de menos en silencio, que es contra lo que advertía la `V32`.
   Ese razonamiento sigue en pie entero; lo único que cambia es que ahora hay una
   tercera respuesta posible.
2. **Van las cuatro o ninguna.** Una fila con tres reventaría al *leerse* en vez
   de al escribirse, que es el peor momento para enterarse. Lo garantizan una
   restricción `check` en la `V55`, el constructor compacto del DTO y el del
   comando — tres capas, porque una regla que solo vive en el DTO se salta por
   cualquier otra puerta.
3. **La medida del producto desnudo sigue prohibida.** Un celular pesa 190 g y su
   caja con cargador pasa de 400: esa cifra no es una aproximación, es un error
   garantizado en la dirección cara. Entre no medir y medir mal, no medir.

## Cómo se comporta

`ArmadorDeBultos` recoge las variantes sin paquete igual que recoge las que
superan el techo asegurable, y lanza `ArticuloSinMedidasException` con **todos**
los culpables nombrados —no el primero—, porque quitar uno y volver a chocar con
el siguiente es cómo se abandona un carrito.

`MetodosDePagoDisponibles` la atrapa en los mismos dos sitios donde ya atrapaba a
su hermana, y el checkout ofrece solo recogida. Sale como `409` con `codigo:
ARTICULO_SIN_MEDIDAS` y la lista de artículos, con la misma forma que
`ARTICULO_NO_ASEGURABLE`.

**Cuando un artículo tiene los dos problemas, manda el techo asegurable.** De los
dos motivos para no despachar, ese es el que no se arregla nunca; el otro se
arregla en cuanto alguien pase el producto por la báscula. Decirle al comprador
"nos falta medirlo" cuando el artículo jamás va a poder viajar asegurado sería
darle una esperanza falsa.

## Consecuencias

**A favor:**

- El catálogo se publica sin esperar a la báscula. Lo que se pierde mientras
  tanto es el envío a domicilio de esos productos, no la venta.
- La recogida deja de ser solo una salida de emergencia y pasa a ser un canal con
  su propio motivo de existir.
- Medir deja de ser un requisito de carga y pasa a ser una mejora incremental:
  cada producto que se mida gana domicilio, de a uno, sin desplegar nada.

**En contra, y hay que decirlo:**

- **Un producto sin medir vende menos.** La recogida es de Medellín; quien compra
  desde Cali y no puede recibirlo a domicilio, probablemente no compra. La
  pérdida es silenciosa: no hay un error en ningún log, hay un carrito que no se
  convirtió.
- **No hay nada que avise de cuántos productos están sin medir.** Hoy se sabe
  consultando la base. Un vigilante que lo reporte es trabajo pendiente, y hasta
  que exista, el riesgo es que "temporal" se vuelva permanente por olvido — que
  es exactamente cómo acaban estas cosas.

**Qué reabre esta decisión:** si se mide que una parte apreciable del carrito
abandonado viene de artículos sin medidas, la respuesta no es volver al `NOT
NULL` —eso solo cambia el abandono por no-publicar— sino atacar el pendiente:
medir. El dato para decidirlo no existe todavía.
