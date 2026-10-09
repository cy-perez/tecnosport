# ADR-0067 — La ingesta de proveedores empieza por la exportación del chat, no por la Cloud API

**Fecha:** 2026-09-30
**Estado:** aceptado.

## Contexto

Los proveedores de bolsos y de ropa publican su catálogo en WhatsApp: una foto
o varias, un texto con el precio, a veces las tallas y los tonos, y otra vez
mañana. Hasta ahora ese catálogo entraba a la tienda a mano, uno por uno, o no
entraba. El objetivo es que entre solo: leer los mensajes, agrupar los que
forman una publicación, sacar de cada una un producto con su precio y sus
variantes, detectar cuándo un mensaje es el mismo bolso de la semana pasada, y
dejar todo como borrador para aprobarlo desde el panel.

La pregunta que decide la arquitectura es **por dónde entran los mensajes**.
WhatsApp ofrece dos caminos:

- **La Cloud API de WhatsApp Business.** Los mensajes llegan por webhook, en
  tiempo real, a un número de la empresa. El proveedor tendría que escribirle a
  ese número —o incluirlo en su lista de difusión—, la empresa necesita la
  verificación de Meta Business, y los mensajes que el número recibe se cobran
  por conversación. Y hay una limitación que no se resuelve con dinero: la
  Cloud API **no lee grupos** ni listas de difusión de terceros; solo lo que le
  escriben directo.
- **La exportación del chat.** Desde el teléfono, "Exportar chat, incluir
  archivos" genera un `.zip` con el `.txt` de la conversación y las fotos. Es
  manual —alguien lo hace y lo sube— y trae todo lo que el chat tiene, incluidos
  los grupos.

## Decisión

**La ingesta arranca por la exportación del chat.** El panel pide una URL
firmada, sube el `.zip` derecho al bucket privado de proveedores y avisa al
servidor con la `objectKey`; el servidor encola el lote y lo procesa en segundo
plano: lee el `.txt` (Android e iOS, con las marcas invisibles que WhatsApp
mete en las fechas), filtra los mensajes del remitente que el proveedor tiene
registrado, los deduplica por huella para que volver a subir el mismo chat sea
seguro, los agrupa en publicaciones, y le pide a la extracción lo demás.

Y es el único de los dos que llega a donde publican estos proveedores: el de
bolsos en un grupo y el de ropa en el canal de avisos de dos comunidades, siempre
desde el número del administrador. Un grupo exportado trae a todos los miembros,
y el filtro por remitente se queda solo con el proveedor: por el nombre del
contacto, por el apodo con virgulilla que WhatsApp pone a quien no está guardado,
o por el número.

Se elige por tres razones, en este orden:

1. **Funciona con lo que los proveedores ya hacen.** No hay que pedirles que
   escriban a otro número ni que cambien su lista de difusión. Se exporta el
   chat que ya existe, con su historia, y el primer lote trae el catálogo
   entero.
2. **No cuesta nada ni pide nada de Meta** hasta que el flujo demuestre que
   sirve. La verificación de negocio y el número de empresa son trámites que
   toman semanas y no hay razón para arrancarlos antes de saber si la
   extracción entiende a estos proveedores.
3. **El contrato interno no depende del camino.** Todo lo que sigue después de
   "tengo una lista de mensajes de este proveedor" es el mismo código para los
   dos caminos: `FuenteDeMensajes` es un puerto, la exportación es su primera
   implementación, y `MensajeProveedor` guarda un `origen` (`EXPORTACION_CHAT`
   hoy) y un `id_externo` por mensaje. El día que un webhook traiga mensajes,
   escribe la segunda implementación y el resto no se entera.

Lo que se deja preparado para ese día, sin usarlo: el secreto
`whatsapp-cloud-api-token` existe en Secret Manager del ambiente de dev, vacío,
para que el recipiente esté cuando haga falta cargarle un valor.

**La extracción la hace la API de Claude**, con salida estructurada
(`output_config.format` con un esquema JSON) y un modelo pequeño: cada
publicación es un texto corto y un puñado de fotos, y lo que se le pide es
rellenar un esquema fijo —título, tipo, precio, tallas, tonos, material,
características, confianza—. El precio **no se le cree**: si el texto trae un
precio reconocible por la expresión regular del dominio, ese gana, y si los dos
difieren el borrador lleva la alerta `PRECIO_INCONSISTENTE`. Sin clave de API
—en las pruebas, o en una máquina que no la tiene— el extractor es uno sembrado
que devuelve confianza cero, y el lote termina con todo en revisión: el flujo
se puede probar de punta a punta sin gastar una llamada.

## Consecuencias

- Hay un paso manual —exportar y subir— y una persona tiene que hacerlo con la
  frecuencia que quiera tener el catálogo al día. Para dos proveedores es
  aceptable; con diez dejará de serlo, y ese es el momento de la Cloud API.
- El zip pesa lo que pesen las fotos: decenas de megas. Por eso no pasa por la
  API sino derecho al bucket, y por eso hay dos topes —el del zip y el de lo
  descomprimido— que responden 413 y 422 antes de intentar nada.
- La ingesta corre en un solo hilo dentro de la aplicación. Dos lotes del mismo
  proveedor no se pisan porque van en fila. Un lote que no cabe en la cola
  responde 503 y **queda cerrado en `ERROR`** con su motivo: la cola vive en
  memoria y solo entra lo que se encola, así que dejarlo en `RECIBIDO` era
  prometer un trabajador que no iba a llegar. Se vuelve a subir la exportación.
- **La cola no sobrevive a un reinicio, y eso está resuelto al arrancar.**
  `ReanudadorDeIngestas` devuelve a la cola lo que quedó en `RECIBIDO` y cierra
  en `ERROR` lo que estaba en `PROCESANDO`, con un motivo que pide volver a
  subir el archivo. No se reanuda a medias porque no hay forma de saber en qué
  publicación iba, y repetir la subida es seguro por la deduplicación.
- **El mismo anuncio repetido no abre dos borradores**, y desde el 9 de octubre
  de 2026 «el mismo» pide el texto **y la foto** (ver «El mismo texto no es la
  misma prenda», abajo). Mientras hay uno en revisión, el repetido se descarta
  con ese motivo; y si dos borradores con la misma huella llegan a quedar en
  revisión, aprobar el segundo responde 409 (`PRODUCTO_DE_PROVEEDOR_YA_EXISTE`)
  en vez de chocar con el índice único.
- **Un producto aprobado se ve al aprobar, no en la fecha del mensaje.** Entre
  exportar y aprobar pasan días; con la fecha del mensaje nacía ya vencido para
  la ventana de `ADR-0066` y el job lo ocultaba en su primera vuelta.

## Varios productos en un mensaje (2 de octubre de 2026)

La primera exportación de Violeta trajo lo que el diseño no esperaba: un pie de
foto con dos productos, cada uno con su código y su precio —«Chaqueta Denim
corta (Q377) 💲108 … Jean Mom Fit Licrado (Q343) 💲119900»— sobre la foto del
conjunto puesto. Cinco de sus diez publicaciones eran así. Con un borrador por
publicación, el segundo producto se perdía.

- **Parte el mensaje el extractor, no el agrupador.** El esquema devuelve
  `productos: [...]`, en el orden del mensaje, y vacía cuando no anuncia
  ninguno. El agrupador no sabe dónde termina el texto de un producto y empieza
  el del otro; el modelo sí. Lo que no se le cree es el precio: cada producto se
  contrasta con el precio **de su misma posición** en el texto, y solo cuando
  hay exactamente uno por producto. Con más o con menos no hay forma honesta de
  emparejarlos, y todos llevan `PRECIO_INCONSISTENTE`.
- **Cinco por mensaje, como mucho.** Si el extractor devuelve más, entran los
  cinco primeros con `CONFIANZA_BAJA`: un mensaje de catálogo con treinta
  líneas no son treinta borradores. El tope lo decidió el negocio.
- **Las fotos son de todos, y lo dice `FOTOS_COMPARTIDAS`.** Cada borrador ve
  todas las fotos de la publicación, y la persona deja las suyas al aprobar.
- **Sin huella visual hasta aprobar.** La primera foto del conjunto puede ser
  de cualquiera de los productos, y con su pHash el próximo anuncio del jean se
  habría reconocido como renovación de la chaqueta. Esos borradores nacen sin
  pHash y no se reconocen por foto; al aprobarse reciben el de la foto que la
  persona **marcó como principal**, y el panel no deja aprobarlos sin esa
  marca. Desde entonces el panel deja marcar la principal en cualquier
  borrador, no solo en estos.
- **La publicación queda extraída si algún producto quedó.** Se descarta solo
  cuando se descartan todos; el descarte de uno —el jean que ya estaba en
  revisión por otro mensaje— se cuenta en el resumen del lote, no en la
  publicación.

El mismo día, el patrón de precio aprendió las formas de La Riverah
(`🤑🤑*55.000*`, `🎽55.000~~`) y de Violeta (`💲124` por 124.000): sin ellas,
tres de los ocho productos de La Riverah no abrían publicación y sus fotos
terminaban en el producto vecino, y de Violeta no salía ninguno.

El 9 de octubre aprendió además a **no leer lo tachado**: La Riverah tachó el
precio por mayor (`~~ PRECIO x MAYOR🤑99.900🥳~~~`) y escribió debajo el que vale
(`Súper descuento $69.900`), y el borrador habría salido con el tachado. Un tramo
que abre con virgulillas al principio de la línea o tras un espacio, y cierra en
la misma línea, se borra antes de buscar; el cierre `55.000~~` va pegado al
número y no abre nada.

## El álbum que llega lejos de su precio (9 de octubre de 2026)

La exportación de D'Osman del 7 de octubre trajo el álbum del bolso ejecutivo a
las 14:22–14:24 y el texto con el precio a las 15:01. Con la ventana de quince
minutos las cinco fotos quedaban sueltas y el borrador salía con `SIN_FOTOS`,
aunque en el chat no había nada más entre las dos cosas.

- **Un precio que termina el reparto sin una sola foto recoge el álbum suelto que
  tiene pegado**: fotos que nadie se quedó, seguidas, sin otro mensaje en medio,
  justo antes o justo después de él. Si hay uno a cada lado decide el
  `OrdenDePublicacion` del proveedor.
- **El álbum tiene que llegar a una hora o menos del precio**, y sus fotos siguen
  pidiendo la ventana entre ellas. Es un parámetro técnico y no se configura:
  cubre los 37 minutos de D'Osman con holgura, y más allá ya no es un álbum que se
  demoró.
- **Un precio que ya tiene fotos no recoge nada.** La ventana sigue siendo la
  regla; esto solo rescata lo que la ventana dejaba sin dueño y sin competencia.

## El mismo texto no es la misma prenda (9 de octubre de 2026)

La exportación de La Riverah del 9 de octubre trajo el «Busito manga larga» a
58.000 dos veces, a las 12:06 y a las 19:32 del día anterior, con el mismo texto
letra por letra: el primero azul y el segundo gris. La «Chaqueta Cuerina» a
95.000, igual: negra a mediodía, beige en la noche. La huella era proveedor,
título y precio, así que el segundo de cada par se descartaba como repetido, y
si se hubiera aprobado el primero, el segundo se habría tomado por su renovación.
El negocio lo dijo así: un anuncio repetido a otra hora es otro producto.

- **Sin código, la huella del producto lleva la fecha del mensaje**
  (`HuellaProveedor.deAnuncio`). Dos anuncios con el mismo texto a distinta hora
  son dos borradores, y los dos pueden aprobarse sin chocar con el índice único.
  Leer otra vez el mismo mensaje da la misma huella.
- **Se descarta solo el anuncio repetido con la misma foto.** Meraki repite el
  «Buso navideño» con el texto y las fotos de la vez anterior, y eso sigue
  siendo un solo borrador: hay uno en revisión con el mismo título y precio, y
  alguna foto del anuncio nuevo está a la distancia de Hamming del umbral de la
  principal de aquel. Las fotos se comparan todas contra esa, porque el álbum
  repetido no siempre llega en el mismo orden. Si alguno de los dos no tiene foto
  con que comparar, no se descarta: un borrador de más se elimina en el panel, y
  una prenda descartada no vuelve.
- **Con código de referencia, el código manda** (`HuellaProveedor.deReferencia`).
  Violeta marca cada prenda —«Jean costuras contrastadas (Q339)»— y la repite en
  varios conjuntos al día: ahí el mismo código es la misma prenda a cualquier hora
  y a cualquier precio, y se descarta o se renueva como antes. El código lo lee
  el extractor (`codigo_referencia`, uno por producto, porque en un mensaje de
  dos prendas solo el modelo sabe cuál es de cuál), y como el precio, **no se le
  cree**: si no está escrito en el texto, no hay código.
- **La renovación de un producto sin código es por la foto.** El mismo texto
  sin la misma foto ya no renueva nada. Los productos aprobados antes conservan
  su huella vieja: nada los vuelve a encontrar por el texto, y la foto los sigue
  renovando.

## Los diminutivos se escriben con el nombre de la categoría (9 de octubre de 2026)

El mismo día el negocio pidió que «Busito manga larga» se publique como «Buzo
manga larga»: una prenda se nombra como la categoría en la que se vende, no con
el diminutivo del proveedor. La categoría del catálogo se llama «Buzos», así que
el «BUSO NAVIDEÑO» de Meraki también pasa a «Buzo».

- **El prompt lo pide** para el título, la descripción, el texto en inglés y el
  tipo («busito» es `buso`). El valor `buso` del enumerado es interno y no se
  renombra; lo que se ve —la etiqueta del panel— dice «Buzo».
- **Y no depende de que el modelo lo recuerde**: `NombreDeCategoria` corrige el
  título (dentro de `CorrectorDeTitulo`) y la descripción con una lista
  **explícita** de diminutivos de las prendas que se venden. Explícita a
  propósito: quitar un «-ito» suelto convertiría «bonito» en «bon». Un diminutivo
  nuevo se agrega a la lista con su ejemplo.

## Falda y sudadera (9 de octubre de 2026)

El lote de La Riverah dejó dos borradores que había que corregir a mano:

- **La «Falda plisada» salió con tipo `OTRO`** y la **«Sudadera jogger» como
  buzo**. Los dos tienen categoría en el catálogo, y el tipo no tenía dónde
  ponerlos. Entran `FALDA` y `SUDADERA`. En Colombia la sudadera es el pantalón
  deportivo —jogger o de sudadera—, no la prenda de arriba, y el prompt lo dice.
  La falda propone «Dama › Faldas» en el panel. La sudadera no propone nada,
  porque hay de dama y de caballero. Una falda short sigue siendo `short`.

## Pendientes que este ADR deja escritos

- **La confirmación con el proveedor en los pedidos.** Aprobar un borrador
  registra una existencia inicial por variante, pero nadie la cuenta después:
  antes de despachar un pedido con productos de proveedor habría que
  confirmarle al proveedor que todavía lo tiene. Es un paso del flujo de
  pedidos que no existe y que este trabajo no abre.
- **La publicación automática.** El proveedor tiene un indicador
  `publicacion_automatica` que el panel muestra y guarda, y que hoy no hace
  nada: todo borrador pasa por revisión. Se encenderá para un proveedor cuando
  la extracción lleve semanas sin alertas con él.
