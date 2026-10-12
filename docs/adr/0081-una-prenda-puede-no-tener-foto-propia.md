# ADR-0081: una prenda puede no tener foto propia, y puede no venir en todas las tallas

Fecha: 2026-10-12
Estado: aceptada
Amplía: `ADR-0070` (una variante es una prenda), cuyas prendas solo existían si alguna foto las
retrataba, y el punto 5 de `ADR-0078` (las tallas por tono), que la aprobación adivinaba cruzando
nombres.

## Contexto

Cinco borradores de Violeta en dev, revisados el 12 de octubre de 2026, no tenían cómo aprobarse con
sus colores:

- **La blusa licrada**: dos fotos, una con el negro, el cocoa y el verde de frente y otra con los
  mismos tres de espalda.
- **El bodi, el croptop y el enterizo**: una sola foto con todos los tonos.
- **La camiseta regular**: cinco tonos repartidos en dos fotos, tres en una y dos en la otra.

Ninguna de esas fotos es de un solo tono. Y en `ADR-0070` una variante de color existía solo si
alguna foto la retrataba: la foto con todos los colores vale para todas las prendas, así que la
aprobación no creaba ningún color. Quien compraba no podía decir cuál quería, y el pedido no lo
congelaba.

Además, el enterizo reparte las tallas por tono («ivory S M L XL / beige rosa S L XL / azul M L
XL») y la blusa también («SM ML negro / ML cocoa / SM verde»). La aprobación las leía del texto
(`TallasPorTono`) buscando el tono por su nombre en la paleta y por el que la lectura vio en las
fotos de ese tono. Sin fotos de un solo tono, ese segundo nombre no existe, y «cocoa» no está en la
paleta: el cocoa salía en todas las tallas.

## Decisión

### Las prendas llegan aparte de las fotos

`AprobarBorradorComando` trae `prendas`: número, color, muestra y, opcionalmente, tallas. Las fotos
siguen diciendo de qué prenda son. **Una prenda que ninguna foto cita es una variante sin foto
propia**: se crea igual, con su color, y quien compra la ve en la foto de todos, que vale para
todas. La ficha ya lo soportaba: elegir un color sin foto deja la galería donde está (`ADR-0069`).

El pedido no cambia: congela el valor del atributo Color en `LineaPedido.detalleVariante`, y de
ahí lo leen el carrito, el correo y la guía. Lo que faltaba era que la variante existiera.

Sin `prendas`, la aprobación hace lo de antes: las prendas salen de las fotos.

### Orden y numeración

Primero las prendas con foto, en el orden de su primera foto; después las que no tienen ninguna,
en el orden en que llegan. Un color repetido entre prendas se numera como en `ADR-0070`.

**Una prenda sin foto no puede llamarse como otra.** El número distingue cuando hay una foto que
mirar. Sin foto, «Negro 1» y «Negro 2» se leen igual en la ficha, en el correo y en la guía. Se
responde 422 `PRENDA_INCOHERENTE` antes de crear nada. El negocio aclaró que una foto con todos
los colores trae una prenda por color, así que el caso no debería darse; la regla está para que,
si se da por un error al marcar, no llegue a la vitrina.

### Las tallas de cada prenda, si quien aprueba las dice

Cada prenda puede traer sus tallas, que tienen que estar entre las del producto. Cuando las trae,
mandan, y la aprobación deja de adivinar. Sin ellas, decide `TallasPorTono` como antes. Se rechazan
una prenda sin ninguna talla y tallas por prenda en un producto sin lista de tallas.

En el panel hay una casilla, «Las tallas cambian según el color», y debajo una tabla de color y
talla. La pidió el negocio. Llega marcada y llena cuando el anuncio reparte las tallas por tono.
Busca el tono por el color elegido y por cómo lo llama el proveedor, así que «cocoa» encuentra sus
tallas aunque la prenda se apruebe como «Café». Sin marcar, todos los colores vienen en todas las
tallas, que es lo de casi siempre.

**El panel siempre manda las tallas de cada prenda cuando el producto tiene lista de tallas**:
todas si la casilla está desmarcada. Si mandara nulo, la API volvería a recortarlas con
`TallasPorTono`, y desmarcar la casilla no haría lo que dice. Peor aún, el resultado sería una
mezcla: el tono que coincide con la paleta saldría recortado y el «cocoa» aprobado como «Café», en
todas las tallas. Lo encontró la revisión del mismo día. El nulo queda para los clientes que no
dicen tallas.

### El panel propone las prendas sin foto

La lectura de fotos ya veía los colores de cada foto (`ADR-0078`). El borrador guardaba solo los de
la foto de un tono; los de la foto con varios se quedaban en el JSON de la lectura.

- **`VerBorrador` vuelve a leer ese JSON al abrir el borrador** (`InterpreteDeLecturaDeFotos`), y
  cada foto trae `tonosVistos`. Sirve también para los borradores que ya estaban en revisión, sin
  migrar nada. El intérprete es el mismo código con que el lector lee la respuesta del modelo.
- Solo de las fotos que el borrador tiene para él solo. Una foto que otro producto de la
  publicación también conserva puede retratar los colores de ese otro producto: es la misma
  cautela con que la extracción solo toma los tonos de las fotos cuando son suyas.
- El panel propone una prenda sin foto por cada tono de esas fotos y del texto del anuncio
  (`tonosNombrados`) que ninguna foto de un solo tono cubra. Si la paleta no tiene ese nombre, la
  prenda llega sin color y con la pista del proveedor («El proveedor lo llama «cocoa»»). No se
  aprueba hasta elegir el color o quitar la prenda: no se inventa un color, y tampoco se pierde un
  tono que se vende.
- Marcarle a una foto suelta el color de una prenda sin foto la mete en esa prenda: es la foto de
  ese tono que el anuncio nombró. Si se creara otra prenda, quedarían «Negro 1» y «Negro 2», y la de
  sin foto quedaría repetida.

## Consecuencias

- La camiseta del caso 2 (cinco tonos en dos fotos) se aprueba con cinco variantes sin foto
  propia. **Elegir el cuarto tono no lleva a la segunda foto.** Para eso una foto tendría que
  colgar de varias variantes, y hoy cuelga de una (`imagen_producto.variante_id`). Se decidió
  dejarlo fuera el 12 de octubre de 2026: la compra queda bien hecha y la galería no se mueve.
- Las fotos con varios tonos de un borrador con un solo producto en la publicación proponen
  colores al abrirlo, también en los borradores que ya estaban en revisión.
- Los tonos del texto se proponen aunque la lectura de fotos esté apagada. Si el anuncio nombra
  colores y ninguna foto se marca, el producto sale con esos colores sin foto. Antes salía sin
  ningún color.
