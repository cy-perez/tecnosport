# ADR-0101: los ajustes de la ingesta del 29 de octubre y la ganancia de tecnología en pesos

Fecha: 2026-10-29
Estado: aceptada.
Relacionados: `adr/0067` (la exportación del chat), `adr/0078` (la ingesta lee las fotos),
`adr/0083` (los sets), `adr/0092` (el calzado sin tenis), `adr/0095` (la decisión 8, que esta
corrige), `adr/0100` (la foto decide la prenda).

## Contexto

La revisión de los borradores de Imperio Wicho, Meraki, Violeta y D'Osman del 28 de octubre de
2026 dejó estos problemas, y la aprobación de un equipo de tecnología uno más:

1. **Un borrador de tenis con tres fotos de otro tenis.** El Onitsuka de las 17:16 traía su álbum,
   y a las 17:30 el proveedor mandó tres fotos más —que WhatsApp no exportó— y «Colección
   Caballero 👨🏻🔥», sin precio. Ese texto no abre publicación, y por la ventana de 15 minutos
   las tres fotos cayeron en el Onitsuka.
2. **Un tenis Adidas con cuatro fotos de sandalias.** A las 11:08 Imperio Wicho publicó dos
   álbumes de sandalias con el mismo texto. El id de un mensaje de la exportación es proveedor,
   minuto y contenido, así que el segundo texto colapsaba con el primero como si fuera un
   reenvío; su álbum se quedaba sin precio y la regla del «álbum entre dos precios» se lo daba al
   tenis de las 11:14.
3. **Fotos que no son de ningún producto**: el banner de crédito con Addi y Sistecrédito, el de
   contraentrega, un meme, un «buenos días» y un sticker, metidos entre los anuncios.
4. **Pijamas y correas**, que la tienda no vende.
5. **Marcas sin «estilo»**: «Polo bordado Tommy», «Bermuda Boss». El prompt solo lo escribía en
   las réplicas (1.1, AAA), y todo lo de un proveedor es de marca Genérica (`adr/0100`).
6. **Sets que salían como una sola prenda**: «Set Blusa y falda (VY3155) 💲94» quedaba «Blusa»;
   «Conjunto DRI FIT ✅Pantaloneta + Camiseta» quedaba «Conjunto pantalón»; «Pantaloneta $39.990 /
   Gorra 35.000 / Dúo $69.900» quedaba la pantaloneta sola, porque la gorra solo armaba set con
   una camiseta o un polo.
7. **La ganancia mínima de tecnología** era el 10 % del costo con tope de 400.000: a un equipo de
   1.900.000 le pedía 190.000, y el negocio lo quería vender con 50.000.

## Decisión

Decidida por el negocio el 29 de octubre de 2026.

1. **El álbum que cierra un texto sin precio es otro anuncio.** Para quien manda primero las
   fotos (`FOTOS_PRIMERO`): si un precio ya tiene su álbum delante, las fotos que llegan después
   de su minuto y que cierra un texto sin precio no son suyas; quedan sueltas con su texto
   (`AgrupadorDePublicaciones`). La foto del mismo minuto que el precio sí es suya.
2. **El mismo texto dos veces en el mismo minuto, con un álbum en medio, deja un separador.** La
   segunda aparición se registra como un mensaje que no es de nadie (`OTRO`): no abre otra
   publicación —tendría la misma huella que la primera y chocaría al aprobarla—, pero corta el
   álbum, que se queda con el anuncio igual de al lado y no salta al siguiente. La primera
   aparición conserva su id; volver a subir una exportación vieja crea, a lo sumo, ese separador,
   que queda suelto. Dos seguidos, sin nada en medio, siguen siendo un reenvío
   (`RegistrarMensajesDeProveedor`).
3. **El sticker no es una foto**, exportado (`STK-….webp`) ni omitido, y conserva el id de
   siempre. Lo demás omitido —«Multimedia omitido», un video— sigue contando como foto: marca
   dónde empieza y termina un álbum.
4. **El lector de fotos dice si cada foto muestra algo que se vende** (`muestra_producto`). La que
   no, sale del producto y el borrador nace con ella descartada: **no se muestra**. Si el lector
   lo dice de todas las fotos de un producto, no se le cree. Una lectura de antes no lo dice, y
   vale `true`.
5. **La pijama y la correa no se venden, de ningún proveedor** (`ArticuloQueNoSeVende`, motivo
   `ARTICULO_QUE_NO_SE_VENDE`). Se mira el título que dejó la extracción: la pijama, la nombre
   como la nombre; la correa o el cinturón, solo si el título empieza por ahí, porque «correa»
   también es la tira de un bolso.
6. **Toda marca que el título nombre va detrás de «estilo»**, sea réplica o no: lo dice el prompt
   y lo asegura `ProductoExtraido.conEstiloAntesDeLaMarca`, que lo inserta delante de la marca de
   la extracción —entera o una de sus palabras, nunca la primera del título, que es la prenda—.
7. **Los sets:**
   - el que el anuncio vende entero, a un solo precio, es el producto: su título nombra las
     piezas con la palabra del proveedor y en su orden («Set blusa y falda», «Conjunto pantaloneta
     y camiseta»), y su tipo es el del conjunto. El extractor gana el tipo `conjunto_falda`;
   - la pantaloneta es un `short` que se titula «Pantaloneta»;
   - la gorra arma set también con el short y la bermuda;
   - el título de un set armado conserva la palabra del combo y el orden de las piezas: «Dúo
     pantaloneta… y gorra», «Set blusa y falda short» para «Set básica + falda». Sin palabra, las
     de `adr/0083`;
   - la foto no decide un conjunto (corrige `adr/0100`, decisión 3): no vuelve conjunto la prenda
     que se vende sola, ni prenda sola el conjunto o la prenda con su acompañante.
8. **Tecnología se aprueba con una ganancia entre 50.000 y 400.000 sobre el contado**, en pesos y
   sin porcentaje: `PROVEEDORES_TECNOLOGIA_GANANCIA_MINIMA` (50.000) y
   `PROVEEDORES_TECNOLOGIA_GANANCIA_MAXIMA` (400.000, la misma del tope de la sugerencia). Por
   debajo de la mínima la aprobación lo rechaza y la lista agota la variante, como antes; por
   encima de la máxima solo la aprobación lo rechaza (`PRECIO_SOBRE_GANANCIA_MAXIMA`). La regla 18
   de la skill `listas-de-proveedor` descarta lo que no le gana 50.000 al costo frente al precio
   de mercado.

## Consecuencias

- **Lo ya ingerido no se recalcula.** Los borradores en revisión de estos proveedores se
  corrigen a mano o se purgan y se vuelve a subir el zip.
- **El título de un set cambia de palabra** («Set falda y blusa» donde decía «Conjunto falda y
  blusa»). La huella del anuncio lleva la fecha, así que entre días el set ya publicado se sigue
  reconociendo por su foto o su código; solo un reproceso del mismo día no lo reconocería por el
  título.
- **Lo que el 10 % agotó sigue agotado hasta la próxima lista**: `agotada_por_margen` se
  recalcula al importar una lista, no al desplegar.
- **En lo barato, la regla de tecnología es más estricta**: un audífono de 100.000 necesitaba
  10.000 de ganancia y ahora 50.000. Con la próxima lista, algunas variantes baratas se agotarán
  por margen.
- El cambio de esquema del extractor y del lector se prueba contra la API real antes de fusionar
  (`ExtractorClaudeContratoTest`, `LectorDeFotosClaudeContratoTest`).
