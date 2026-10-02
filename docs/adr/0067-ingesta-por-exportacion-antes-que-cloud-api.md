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
- **El mismo anuncio repetido no abre dos borradores.** Mientras hay uno en
  revisión con la misma huella, la publicación repetida se descarta con ese
  motivo; y si el primero ya se aprobó, aprobar el segundo responde 409
  (`PRODUCTO_DE_PROVEEDOR_YA_EXISTE`) en vez de chocar con el índice único.
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
