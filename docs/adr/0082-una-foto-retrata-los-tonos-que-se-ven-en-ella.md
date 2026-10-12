# ADR-0082: una foto retrata los tonos que se ven en ella, y la principal puede pedir entrar en la ficha

Fecha: 2026-10-12
Estado: aceptada
Amplía: `ADR-0081` (una prenda puede no tener foto propia), cuya primera consecuencia dejaba esto
fuera, y `ADR-0069` (la ficha enseña todas las fotos), en la regla de la principal sin color.

## Contexto

Cuatro borradores de Violeta en dev, revisados el 12 de octubre de 2026:

- **La blusa licrada**: dos fotos, cada una con el negro, el cocoa y el verde juntos. Ya aprobada,
  la ficha enseña **una** foto. La principal vale para todos los tonos y por `ADR-0069` no entra
  en la galería de la ficha: es la portada del catálogo. Aquí eso dejaba sin verse la foto que
  quien aprobó eligió de principal.
- **La camiseta manualidad**: la foto 1 con el blanco y el negro, la foto 2 con el café, el crema
  y el cocoa. Con `ADR-0081` se aprueba con cinco variantes sin foto propia, pero elegir el negro
  no lleva a la foto 1. Una foto colgaba de una sola variante (`imagen_producto.variante_id`).
- **La blusa y el croptop** salían con un color **Rosado** que el producto no tiene. El texto
  decía «negro, cocoa, verde». La lectura de fotos vio «negro, rosado, verde», porque a su ojo el
  cocoa es rosado. La propuesta de prendas busca cada nombre en la paleta, y la paleta no tenía
  «cocoa».
- **El enterizo** dice «ivory», y la propuesta no lo encontraba. La paleta sí lo tiene: se llama
  Marfil, y su nombre en inglés es Ivory.

## Decisión

### Una foto retrata, además de su tono, las prendas sin foto que se ven en ella

Una prenda sin foto propia puede decir en cuál foto se ve (`PrendaAprobada.seVeEn`). Esa foto
guarda los ids de esas variantes en `imagen_producto.muestra_variantes` (`V112`). La ficha, al
elegir un color, se para en la foto propia de ese tono; si no tiene, en la que lo retrata; y si
tampoco, en la primera. La foto del carrito y la de la tarjeta siguen el mismo orden.

- **Texto y no tabla**, por lo mismo que la muestra de color (`V80`): se lee entera con la imagen y
  nunca se filtra por una de sus partes. Una variante borrada deja su id sin nada que lo encuentre,
  y la ficha lo ignora.
- La foto que retrata un tono **no lo elige** al tocarla en la tira (`colorDeImagen`): retrata
  varios, y elegir uno a ciegas cambiaría lo que se compra sin que nadie lo pidiera.
- Se rechaza con 422 `PRENDA_INCOHERENTE` una prenda con foto propia que además dice verse en otra,
  y una que dice verse en una foto que no se publica.

El panel lo propone. Un tono que se ve en **una sola** de las fotos de varios tonos llega visto en
ella. Si se ve en varias, como la blusa de frente y de espalda, ninguna lo distingue y llega sin
foto. Quien aprueba lo cambia con un selector, «Se ve en», en cada color sin foto.

### La principal sin color puede pedir entrar en la ficha

Una casilla, «La foto principal también se ve en la ficha», que solo aparece cuando la principal
vale para todos los tonos y hay más de una foto. Se guarda en la foto, `imagen_producto.en_la_ficha`,
y no en el producto. `V88` dejó escrito por qué: es la foto la que decide si sale.

**Por omisión no se marca.** La excepción: si alguna prenda sin foto se ve en la principal, llega
marcada. En ese caso la principal es la foto de ese tono, y sin entrar en la ficha elegirlo no
llevaría a ninguna parte. Eso incluye la foto de grupo que encabeza fotos de detalle sin color:
la casilla llega marcada ahí también, donde antes la portada no entraba. Quien aprueba la puede
desmarcar. Marcar otra foto como principal vuelve a proponerla.

La casilla es del **puesto** de principal, no de la foto. Intercambiar la principal en el editor
del producto pasa la casilla a la que sube, si no tiene color (`Producto.usarImagenDeGaleriaComoPrincipal`).
Si la casilla se quedara atrás, la foto que sube desaparecería de la ficha, y el editor no tiene
cómo volver a marcarla. La que baja a la galería la suelta, porque la galería entra siempre. La
revisión adversarial del mismo día encontró el caso contrario cuando la regla era «la que sube
llega sin casilla».

### La propuesta de colores habla como el proveedor

- **Cocoa entra a la paleta** (`V111`, `#875F42`).
- La propuesta busca el tono también por el nombre en inglés: «ivory» propone Marfil. Si un
  nombre en inglés coincide con el español de otro color, gana el español.
- **Cuando una foto ve los tonos del texto salvo uno de cada lado, ese es el mismo con otro
  nombre, y manda el del texto.** La lectura vio «negro, rosado, verde» y el proveedor escribió
  «negro, cocoa, verde»: el rosado es el cocoa. La regla compara nombres y no colores, a
  propósito, porque decidir que un rosado es un cocoa pediría saber de color. Por eso exige al menos
  un tono en común y una sola diferencia. Una foto sin ningún tono del texto es de otros tonos (el
  texto dice «blanco, negro» y la segunda foto trae café y crema). Con dos diferencias no se sabe
  cuál corresponde a cuál. En los dos casos se proponen los de la lectura y los del texto, como
  antes. La primera versión, «si son tantos como el texto, mandan los del texto», borraba café y
  crema en ese ejemplo, y la revisión lo encontró antes de mezclar.
- Una foto en que se ve un tono se cuenta por el color de la paleta y no por el nombre: «ivory» en
  una foto y «marfil» en otra son el mismo color visto en las dos.
- El prompt del lector de fotos lo pide también: usar los nombres del anuncio aunque a la vista el
  color parezca otro, y contar cada prenda aunque esté debajo de otra. En la camiseta leyó solo el
  blanco de la foto 1.

## Consecuencias

- La blusa ya aprobada no cambia sola: su principal tiene `en_la_ficha` en falso, y un borrador
  aprobado no se vuelve a aprobar. Para verla en la ficha hay que poner la columna en verdadero
  en la base.
- Una foto puede retratar tonos sin colgar de ninguno. La difusión en redes y el editor del
  producto no leen `muestra_variantes`: lo que publican y lo que dejan cambiar sigue siendo el
  tono de la foto.
- El editor del panel no deja cambiar todavía en qué foto se ve un tono después de aprobar. Hoy
  solo lo decide la aprobación.
- **Subir una principal nueva desde el editor** (`ConfirmarImagenPrincipal`) crea otra foto: sin
  la casilla y sin los tonos que retrataba la anterior. Esos tonos dejan de llevar a una foto y la
  ficha se para en la primera, como con un tono sin foto. Es otra foto, y no se sabe qué retrata.
- Si al intercambiar la principal anterior ya estaba repetida en la galería, no vuelve (regla
  anterior a este ADR), y con ella se van los tonos que retrataba.
