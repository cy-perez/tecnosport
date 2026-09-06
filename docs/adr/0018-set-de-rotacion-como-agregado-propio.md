# ADR 0018. El set de rotación como agregado propio, y qué se verifica de verdad al completarlo

Fecha: 2026-09-06. Estado: aceptada.

## Contexto

`SetRotacion` existía en el dominio desde la Fase 1, pero **nadie lo escribía**:
solo lo leía la ficha pública y lo sembraba el sembrador de desarrollo. El
asistente de captura de la Fase 5 necesita lo contrario — crear un set antes de
tener una sola foto, subir N imágenes, cerrarlo y publicarlo.

`docs/03-api.md` ya documentaba cuatro endpoints (`abrir`, `subidas`,
`completar`, `DELETE`) que no existían en ninguna capa, y prometía que al
completar el set el servidor verifica "que los N objetos existan, que tengan el
tamaño y la proporción esperados, y que ninguno esté vacío".

## Decisión

**El set de rotación es un agregado propio, con su propio puerto
`RepositorioSetsRotacion`**, en vez de cuatro métodos más en
`RepositorioProductos`. Tiene id, tabla y ciclo de vida propios, y quien lo mueve
es el asistente de captura, no la edición del producto. Mismo criterio que llevó
a `Envio` a separarse de `Pedido` (`ADR-0013`). El agregado gana `productoId`: es
suyo, no del producto que lo contiene.

**Se abre vacío, prometiendo cuántos fotogramas va a tener** (`set_rotacion.fotogramas`,
migración `V18`; la columna ya estaba en `docs/02-modelo-datos.md` pero nunca en
la tabla). Sin la promesa, un set de 8 que termina con 4 fotogramas contiguos
—cuatro subidas perdidas— pasaría como un set de 4 perfectamente válido. Cuántas
URL firmadas se emiten también sale de ahí, no del cliente.

**Publicar es un paso aparte de completar**, con su endpoint
`POST /api/v1/admin/sets-rotacion/{id}/publicar`, que este ADR agrega a la lista
documentada. Sin él un set COMPLETO no se vería nunca: la ficha pública solo
expone la rotación cuando está `PUBLICADO`. Y entre los dos pasos está la
revisión del set entero en el asistente (`docs/10-captura-360.md`, paso 6), que
es donde se caza el fotograma torcido.

**Un producto tiene a lo sumo un set publicado.** Publicar sobre uno que ya lo
está se rechaza con 409: hay que borrar el anterior primero. No hay reemplazo en
caliente, así que el producto se queda unos segundos sin visor — se acepta a
cambio de no tener que decidir, con dos sets publicados, cuál se ve.

**Qué se verifica al completar, exactamente:**

| Se verifica | Cómo |
|---|---|
| Que el objeto exista en el bucket | `AlmacenDeImagenes.tamanoBytes`, contra el almacén real |
| Que no esté vacío | El mismo tamaño, `> 0` |
| Que pertenezca al set | La key tiene que empezar por el prefijo que el servidor emitió |
| Que llegaron todos | Contra los fotogramas prometidos al abrir |
| Que sea cuadrado y de 1000 px | Contra las dimensiones **declaradas** por el cliente |

**Lo que no se verifica**: que los bytes sean de verdad una imagen, y que sus
dimensiones reales sean las declaradas. Comprobarlo exigiría descargar y
decodificar el archivo en el backend, que es justo lo que la subida directa a
Cloud Storage evita (`docs/07-infra-gcp.md`). Es el mismo riesgo que ya aceptó
`ADR-0016` para la imagen principal, y se acepta por el mismo motivo: el panel lo
usa solo el administrador del negocio.

Un set que no pasa la verificación **se queda en BORRADOR entero**, no a medias:
medio set publicado es un visor roto.

## Alternativas

**Métodos nuevos en `RepositorioProductos`.** Menos piezas de entrada, pero mete
en el repositorio del catálogo un ciclo de vida que no es del catálogo, y obliga
a cargar el producto completo para tocar un set que ya sabe a qué producto
pertenece.

**Que `completar` publique de una.** Un endpoint menos y un paso menos para el
asistente, pero borra la revisión del set completo, que es la única oportunidad
de ver la rotación armada antes de que la vea un cliente.

**Verificar los bytes reales en el backend** (descargar cada objeto, decodificar,
medir). Cumpliría la promesa literal del documento, pero convierte al backend en
intermediario de 8 a 16 imágenes por set, que es exactamente lo que el diseño de
URL firmadas evita, y agrega una dependencia de decodificación de imágenes.
Cuando el volumen o el número de personas con acceso al panel lo justifique, el
lugar natural es una función aparte disparada por el evento de subida del bucket,
no la petición de completar.

## Consecuencias

- El asistente de captura tiene contra qué construirse: los cinco endpoints
  existen y están probados.
- La promesa de `docs/03-api.md` sobre la verificación queda ajustada a lo que el
  servidor hace de verdad, con el límite escrito en el documento y en el javadoc
  de `CompletarSetRotacion`.
- Reemplazar un set publicado son dos pasos (borrar, publicar) y una ventana sin
  visor. Si eso molesta en la operación real, la salida es una transición de
  reemplazo atómica, no publicar dos.
- El set por variante (`set_rotacion.variante_id`) sigue soportado por el esquema
  y sin ningún caso de uso que lo escriba. Es deliberado: el set del producto
  cubre el caso de hoy.
