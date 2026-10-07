# ADR-0070 — Una variante es una prenda, y una prenda puede tener varias fotos

**Fecha:** 2026-10-07
**Estado:** aceptado. Afina la sección «Un tono que se repite se numera» de
`ADR-0069`: la numeración se queda, pero ahora distingue prendas, no fotos.

## Contexto

Desde el `ADR-0069`, al aprobar un borrador **cada foto con color era una
variante**. Eso arregló el pantalón Americanino —dos prendas «Azul oscuro» que
colapsaban en un solo círculo— y rompió el caso contrario, que es el más común:

- **Un bolso fotografiado desde dos ángulos.** Las dos fotos son de la única
  prenda que hay. Marcarlas «Rojo» y «Rojo» creaba «Rojo 1» y «Rojo 2»: dos
  círculos para una sola prenda, y quien compra elige entre dos cosas iguales.
- **Seis fotos, tres prendas de dos fotos cada una** (roja, blanca, negra). Salían
  seis variantes.
- **Y sin marcar ningún color, la ficha enseñaba una sola foto**: la principal
  «vale para todos los tonos» y el `ADR-0069` la deja fuera de la galería.

La salida obvia era volver a agrupar por color. **Se descartó por el jean plus
para dama**: cuatro diseños distintos, los cuatro negros, que hoy salen como
«Negro 1» a «Negro 4». Agrupando por color serían un solo círculo, que es
exactamente el defecto que el `ADR-0069` vino a corregir.

O sea: **el color no dice si dos fotos son la misma prenda, ni en un sentido ni
en el otro**. Mismo color puede ser dos ángulos de una prenda o dos prendas
distintas, y la única que lo sabe es la persona que mira las fotos.

## Decisión

### La prenda llega explícita

Cada foto de la aprobación trae un campo opcional `prenda`, un número desde 1.
Las fotos con el mismo número son **una sola variante** y llevan el mismo color.
`AprobarBorrador` agrupa por prenda, no por color, y la principal cuelga de la
variante de su prenda como cualquier otra foto.

### La numeración distingue prendas

Un color que se repite **entre prendas distintas** se numera, en el orden de la
primera foto de cada una: dos prendas negras son «Negro 1» y «Negro 2», tengan
las fotos que tengan. Un color que aparece en una sola prenda se queda como está,
aunque esa prenda tenga cinco fotos. Las razones para numerar son las del
`ADR-0069`: el valor del atributo es lo que congela el pedido y lo que lee quien
empaca.

### Sin `prenda`, como antes

Una foto con color y sin `prenda` es una prenda ella sola. Es lo que hacía el
contrato antes de tener el campo, así que un cliente que no lo manda obtiene lo
mismo de siempre. Una foto sin color vale para todas, igual que antes.

### Lo incoherente se rechaza antes de crear nada

Una prenda sin color, o con fotos de dos colores distintos, no se puede volver
una variante: responde **422 `PRENDA_INCOHERENTE`**. Se comprueba antes de crear
el producto y de subir una sola foto al bucket público, así que el rechazo no
deja ni producto a medias ni archivos huérfanos.

### En el panel

- Cada foto tiene, junto a su color, un selector **«Prenda de la foto N»** con
  las opciones «Ninguna: vale para todas», las prendas que ya existen (con su
  color) y «Una prenda nueva».
- **Marcarle un color a una foto suelta la vuelve una prenda**. Así el jean no
  pide un solo clic más que hoy. Para juntar una foto con otra se elige la
  prenda de la otra, y la foto toma su color.
- **Cambiar el color de una foto de una prenda lo cambia en la prenda entera.**
  Quitárselo a una foto que está sola la devuelve a valer para todas. En una
  prenda de varias fotos, quitarlo deja **la prenda** sin color y no saca a
  nadie: el selector es de casillas, y cambiar «Negro» por «Café» pasa por un
  instante sin ninguno marcado. Si ese instante sacara la foto, cambiar de color
  desharía el grupo. Para sacar una foto de su prenda está el selector.
- Un atajo, **«Todas las fotos son la misma prenda»**, para el producto que
  viene en una sola prenda fotografiada desde varios ángulos.
- Un resumen, **«Variantes de color que se crean»**, con el valor que tendrá
  cada variante y sus fotos («Café: las fotos 2, 4», «Negro 1: la foto 3»).
  Permite revisar la agrupación antes de que exista; la numeración la calcula
  igual que la API.

Se eligió un selector y no una casilla tipo «misma prenda que la foto anterior»
porque no depende del orden: marcar otra foto como principal la mueve al frente,
y eso rompería una agrupación por fotos contiguas.

## Consecuencias

- Los productos **ya aprobados** conservan su forma. Si alguno tiene «Rojo 1» y
  «Rojo 2» que en realidad son la misma prenda, no se corrige solo: sería una
  migración puntual, como `V86`, sobre productos identificados.
- Reagrupar las fotos de un producto ya publicado, desde su edición, es otro
  caso de uso y queda fuera.
- **Queda abierto**: «Negro 1…4» le dice poco a quien compra. Una mejora posible
  es que quien revisa escriba un nombre corto por prenda («Negro bota recta») y
  que solo se numere cuando no escribe ninguno. Cambia lo que congela el pedido,
  así que se decide aparte.

## Lo que se verificó en el navegador

El 7 de octubre de 2026, en local, con una exportación real de D'Osman (un bolso
con cinco fotos). Con la foto 2 en «Café», la foto 4 en la misma prenda, y las
fotos 3 y 5 en «Negro» por separado, el resumen dijo «Café: las fotos 2, 4»,
«Negro 1: la foto 3» y «Negro 2: la foto 5». La aprobación publicó **3
variantes**, y la ficha mostró tres círculos. Pulsar «Negro 2» llevó a su foto.
La principal, que valía para todas, quedó fuera de la galería como manda el
`ADR-0069`.
