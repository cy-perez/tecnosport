# ADR-0069 — La ficha enseña todas las fotos, y lo que se publica en redes es el carrusel entero

**Fecha:** 2026-10-07
**Estado:** aceptado. Reemplaza, en la parte de la galería, lo que `V79` y la
casilla «las fotos generales acompañan a cada color» daban por decidido.

## Contexto

Un pantalón cargado por proveedor (`pantalon-estilo-levi-s-americanino`) tiene
**nueve fotos** —la principal y ocho de galería, una por color— y la ficha
enseñaba **una**.

No era un fallo suelto, eran tres decisiones que se sumaron:

1. `imagenesDelColor` recortaba la galería a las fotos del tono elegido.
2. Con `fotosGeneralesEnCadaColor` en falso —que es como sale de la revisión
   cuando cada color trae su foto— ni siquiera la principal acompañaba.
3. Al cargar siempre hay un color elegido, el de la primera variante
   disponible.

Resultado: una sola foto, **sin tira de miniaturas** —la tira solo se pinta con
más de una—, así que nada en la pantalla insinuaba que hubiera ocho más. Para
llegar a ellas había que ir pulsando círculos de color a ciegas, y a la
principal no se llegaba con ningún color.

Y en la misma superficie, dos cosas más:

- **Dos de esas ocho fotos tenían el mismo color.** `AprobarBorrador` agrupaba
  los tonos con `distinct()`, así que colapsaban en una variante con dos fotos
  colgando: quien compra veía un círculo donde hay dos prendas distintas.
- **La difusión en redes publicaba una foto**, la principal, y encima no
  publicaba nada: exigía `urlVistaPrevia` y ningún producto de proveedor la
  tiene (ver más abajo).

## Decisión

### La galería no filtra; el color mueve la foto activa

`imagenesDelColor` deja de existir y en su lugar hay
`indiceDeLaPrimeraDelColor`. La ficha compone **todas** las fotos —la principal
primero, detrás la galería en su orden— y elegir un color mueve cuál está
activa. **Lo que el color decide es dónde mirar, no cuánto se ve.**

La principal va primera porque es la que la tarjeta del catálogo usa de
previsualización: abrir en otra foto se lee como haber entrado a otro producto.
Por lo mismo, la ficha **abre en la principal aunque haya un color elegido**, y
solo sigue al color cuando alguien lo pulsa (`colorElegidoAMano`).

### La principal entra en la ficha solo si retrata un color

Pedido el 7 de octubre de 2026, y es el que cierra la forma de la galería:

- La principal marcada **«Vale para todos los tonos»** encabeza la tarjeta del
  catálogo y **no entra en la galería de la ficha**. Es la portada: no retrata
  ninguna de las prendas que se pueden elegir, así que en la tira sería una
  miniatura que no corresponde a ningún color y que al pulsarla no cambia nada
  de lo que se compra.
- La principal **con un color asignado** sale en las dos: encabeza la tarjeta y
  abre la ficha. Entonces sí es la foto de ese tono, y además es la que quien
  viene de la rejilla acaba de ver.

**Para distinguir los dos casos la principal tiene que poder llevar su
variante, y no podía.** Tres cosas lo impedían, y las tres eran deliberadas:

1. `AprobarBorrador` forzaba `variante_id` nulo en la principal, y con eso
   **descartaba el tono que quien revisa le había marcado a esa foto**.
2. El índice único de `V1` era `(producto_id) where tipo = 'PRINCIPAL' and
   variante_id is null`. Eso no solo impedía el color: dejaba un hueco por el
   que un producto podía tener varias filas `PRINCIPAL` con tal de que todas
   menos una llevaran variante.
3. El repositorio la buscaba con `findByProductoIdAndTipoAndVarianteIdIsNull`,
   así que una principal con color **no se habría encontrado** y el producto
   habría cargado sin imagen principal.

`V87` ensancha el índice a `where tipo = 'PRINCIPAL'`: además de permitir el
color, **la invariante queda más fuerte que antes**. Es seguro porque hoy no
existe ninguna fila `PRINCIPAL` con variante — la única vía que las crea es la
aprobación, que hasta ahora ponía nulo siempre.

**Y el intercambio con la galería conserva el color.** `comoPrincipal()` y
`comoGaleria()` lo soltaban. Soltarlo ahora tiene un efecto que nadie pediría:
ascender la foto de un tono la volvería genérica y **desaparecería de la
galería** — quien la asciende vería una foto menos en la ficha y nada lo
explicaría.

**El respaldo que hace falta:** si la galería está vacía, la principal entra
aunque sea genérica. Una ficha sin una sola foto no le sirve a nadie, y es el
mismo respaldo que ya tenía el recorte por color. La regla distingue la portada
de las fotos de cada prenda cuando hay prendas que distinguir.

**Consecuencia para lo que ya está cargado:** la principal del pantalón
Americanino vale para todos los tonos, así que su ficha pasa de nueve fotos a
ocho. Para que salgan las nueve hay que asignarle un color a esa principal
desde el panel.

### Un tono que se repite se numera

Dos fotos marcadas con el mismo color son dos variantes, y el valor del
atributo las distingue: «Azul oscuro 1», «Azul oscuro 2». Un tono que sale una
sola vez se queda como está.

Numerar es feo y es lo correcto. La variante se identifica **por el valor del
atributo**, y ese valor es lo que el pedido congela
(`LineaPedido.detalleVariante`) y lo que lee quien empaca. Dos variantes con el
mismo texto se leen igual en el carrito, en el correo y en la guía, y nadie
sabría cuál de las dos prendas meter en la caja. El número no dice nada de la
prenda; distingue, que es lo que hace falta.

Se consideró identificar la variante por la foto en vez de por el texto. Se
descartó: el correo y la guía son texto, así que ahí seguirían siendo
indistinguibles — el problema se movía de sitio en vez de resolverse.

### En redes sale el carrusel entero

`PublicadorEnRedSocial` pasa de recibir una URL a recibir una lista. Con una
foto, el camino de siempre; con varias, carrusel: en Facebook cada foto se sube
con `published=false` y un solo `/feed` las reclama por `attached_media`; en
Instagram un contenedor hijo por foto, un padre `CAROUSEL` y el `media_publish`
del padre.

**Cada red dice primero cuáles admite** (`admitidasPor`) y el caso de uso
guarda en la constancia las que quedaron. Instagram rechaza lo que se sale de
4:5 a 1,91:1 y las fotos de proveedor vienen en cualquier proporción; sin este
paso, una sola foto alta tumbaría el carrusel entero. Y si el adaptador
descartara por su cuenta, la fila diría que salió una foto que nunca salió —y
esa fila es justo lo que alguien mira para saber qué vio la gente.

### La vista previa deja de ser obligatoria cuando la imagen ya la sabe leer Meta

`DifundirProducto` exigía `imagenPrincipal().urlVistaPrevia()`. Las fotos que
entran aprobando un borrador de proveedor se publican tal como llegaron —JPEG—
y nunca generan vista previa, así que **ningún producto de proveedor se podía
difundir**: ni publicar, ni siquiera proponer el pie, y el panel solo decía
«revisa que el producto esté publicado y tenga imagen principal», que era falso
en las dos mitades.

Ahora el dominio responde `ImagenProducto.urlParaTercerosQueNoNegocianFormato()`:
la vista previa si la hay y, si no, la propia imagen cuando su formato lo
permite (JPEG o PNG). AVIF y WebP siguen necesitándola, que es el caso para el
que se inventó (`ADR-0056`).

### Escribir en el muro de Facebook va con el token de la página

Lo destapó la validación real del 7 de octubre de 2026, publicando el primer
carrusel de verdad: Meta contestó `(#200) Unpublished posts must be posted to a
page as the page itself`.

Lo que hay configurado es el token de un **usuario del sistema**. Con él se lee
la página y se publica en Instagram —que es lo único que la comprobación del 29
de septiembre había ejercido, creando un contenedor— pero no se escribe en el
muro como la página. O sea que **el camino de Facebook nunca había salido de
verdad**, ni el de una foto ni el del carrusel.

El token de página se le pide a la Graph API con el que ya hay
(`GET /{page-id}?fields=access_token`) y se guarda en memoria. Configurarlo
aparte era la otra vía: dos secretos que caducan por separado y que alguien
tiene que acordarse de rotar juntos.

### Las fotos se encajan, no se recortan

La validación dejó cuatro de las nueve fotos del pantalón fuera del carrusel de
Instagram por proporción, y un producto entero sin poder publicarse allí. Desde
el 7 de octubre de 2026 se encajan antes de mandarlas (`AjustadorDeImagenes`).

**Se añade lienzo, no se corta, y eso es lo contrario de lo que se pidió.** La
petición fue «que se recorten»; recortando una foto de 0,62 para llevarla a 0,8
se va el 22 % del alto, que en un pantalón de cuerpo entero es el ruedo o la
pretina. Ensanchando no se pierde nada de lo que se vende: aparecen dos franjas
a los lados. Si algún día se prefiere cortar, el cambio está en un método.

**Las franjas llevan el color del borde de la propia foto, no blanco.** Las del
catálogo procesado sí tienen fondo blanco, pero las del proveedor llegan como
las mandó — la muestra que destapó esto tenía #DDDCD8 arriba y blanco abajo.

**Y se igualan todas a una sola proporción, la de la primera.** No es solo que
Instagram rechace lo que se sale del rango: en un carrusel **recorta las demás a
la proporción del primer elemento**, así que mandarlas distintas significa que
Instagram corta por su cuenta justo lo que aquí se cuida de no cortar.

Con `ImageIO` y `Graphics2D`, los dos del JDK: sin dependencia nueva. El
resultado se guarda bajo `productos/{id}/redes/` con la key derivada del
contenido y de la proporción, así que difundir dos veces no sube nada nuevo. El
informe de huérfanos las declara **no juzgables**, como las de `rotacion/`: las
reclama `publicacion_en_red.urls_imagenes` y ninguna API las expone.

**El encaje tiene que caer dentro del rango, no en su borde.** La proporción que
se pide suele ser justo el límite que la red admite, así que redondear al entero
más cercano deja la foto fuera por milésimas: `Math.round(1448 × 0,8)` da 1158
de ancho, o sea 0,79972, y la red la rechaza **después** de haberla encajado.
Lo midió la validación del 7 de octubre de 2026 contra la cuenta real — el
ajustador subió las dos fotos al bucket y el filtro las descartó igual, sin un
solo registro que lo explicara. Va `Math.ceil`: un píxel de más no lo ve nadie;
uno de menos cuesta el post.

## Lo que la validación real midió

Contra la cuenta del negocio, el 7 de octubre de 2026:

- **Instagram publica el carrusel.** Cinco fotos del pantalón Americanino,
  publicación `18123936253914127`. De las nueve que tiene, cuatro se quedaron
  fuera por proporción y el post salió igual — que es justo lo que
  `admitidasPor` existe para conseguir.
- **El pie se propone**, que antes era imposible para todo producto de
  proveedor.
- **La guarda de proporciones responde 409 con el motivo** en un producto cuyas
  fotos son todas 1086×1448 (0,75): «no admite fotos más altas que 4:5».
- **Facebook falla**, y de ahí salió lo del token de página.

Y en la vitrina desplegada: la ficha sirve las nueve fotos, el filtro con
`?linea=CALZADO` ofrece solo las tres hojas de calzado, y `V86` dejó el
pantalón con «Azul oscuro 1» y «Azul oscuro 2».

## Consecuencias

- **`fotosGeneralesEnCadaColor` ya no cambia nada en la vitrina.** La casilla
  sigue en el panel —en la revisión del borrador y en editar producto— y el
  dato sigue viajando en la API, pero ninguna pantalla lo lee. Lo que decidía
  —si la principal acompañaba a cada color— lo decide ahora **el color de la propia
  principal**, que es un dato de la foto y no una casilla aparte. Queda pendiente
  retirarla; no se retiró aquí porque quitar un control del panel es una decisión
  de producto, no de este cambio.
- Los productos **ya aprobados** conservan su forma: `AprobarBorrador` numera
  de ahora en adelante. El pantalón que destapó esto se arregla con `V86`, una
  migración puntual y guardada.
- Las variantes que `V86` crea **nacen sin existencias**: cuando las dos
  prendas colapsaron en una, las unidades se cargaron una sola vez por (color,
  talla) y repartirlas a ojo sería inventar un dato de negocio. Salen agotadas
  y quien administra pone la cifra real desde el panel.
- `publicacion_en_red.url_imagen` pasa a `urls_imagenes`, una URL por línea
  (`V85`). Un `rename` y no una columna nueva: una fila vieja es exactamente un
  carrusel de una foto.
