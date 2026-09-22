# ADR-0057 — Una imagen se publica en varios anchos, y sus URL viajan como dato

**Fecha:** 2026-09-22
**Estado:** aceptado. Aplicado en el código; falta volver a subir las fotos del catálogo real con
`--rehacer-imagenes` para que las variantes existan en el bucket.

## Contexto

`ADR-0056` dejó de subir la maestra y empezó a subir el AVIF de 1200 px: 22,01 MiB pasaron a 2,18.
En sus alternativas descartadas anotó el `srcset` —"es lo correcto a largo plazo y no se hizo
ahora"— porque con el peso ya resuelto lo que quedaba era afinar.

La medición del 22 de septiembre le puso número a ese pendiente: la auditoría
`image-delivery-insight` pide **211 KiB** en la portada, porque las cuatro tarjetas cargan el AVIF
de 1200 px para pintarlo en un hueco de unos 180. Son bytes, así que no dependen de la máquina ni
del arnés — y el arnés ya dejó dicho que sus cifras de FCP y LCP no describen lo que ve una persona,
pero sus bytes sí.

Al lado había una segunda deuda, la 20: **`url_webp` guardaba la URL de un AVIF**. La columna nació
esperando una conversión de formato que iba a hacer el asistente de captura de la Fase 5 y que nunca
existió; siempre apuntó al mismo objeto que `url`, y desde `ADR-0056` el objeto es un AVIF, así que
el nombre decía un formato que no era.

Las dos son la misma superficie. Servir varios anchos obliga a guardar varios objetos por imagen, y
en el momento en que una imagen deja de tener una URL y pasa a tener un conjunto, la columna que
miente no se renombra: desaparece.

## Decisión

**Una imagen tiene una lista de variantes, una por ancho publicado, y cada variante lleva su URL.**

- En el dominio, `ImagenProducto` cambia `urlWebp` por `List<VarianteDeImagen>`. **La URL, el ancho
  y el peso dejan de ser campos**: salen de la variante mayor. No hay dos sitios donde escribir la
  URL de la misma imagen, así que tampoco hace falta una invariante que vigile que coincidan.
- En la base, `variante_imagen` con `on delete cascade` desde la imagen. `imagen_producto.url`,
  `ancho` y `bytes` se quedan —los lee el `og:image`, la línea del carrito y el panel— y son la
  variante mayor repetida; no pueden envejecer como envejeció `variante.existencia` (`ADR-0050`)
  porque no hay nadie que las escriba aparte: salen del mismo agregado.
- En el frontend, el `srcset` lo arma `descriptoresDe(imagen)` con **los anchos**, y la URL de cada
  ancho la resuelve el `IMAGE_LOADER`, que recibe las variantes por `loaderParams`.

**La variante lleva su URL y no solo su ancho.** Es la decisión que sostiene todo lo demás. Con solo
el ancho, el mapeador de respuestas y el loader del frontend tendrían que recomponer la URL a partir
del patrón de las keys, cada uno por su cuenta y en dos repositorios que nadie mantiene
sincronizados. Ese acoplamiento silencioso es exactamente el que produjo `url_webp`.

**Y por eso la key no lleva el ancho.** Era la idea inicial —`principal-{uuid}-800.avif`, para verlo
en el bucket— y obligaba a conocer el ancho antes de pedir la URL firmada. El panel no lo sabe hasta
leer el archivo. Como la URL viaja como dato, el ancho en la key sería decoración: el endpoint de
subida no cambió, y el cliente lo llama una vez por variante.

**Se sube además un JPEG de vista previa**, uno por imagen, en `url_vista_previa`. Tiene un solo
destinatario: el previsualizador de enlaces de WhatsApp y de Facebook, que no negocia formatos y con
AVIF no muestra nada. No es un respaldo del `<img>` del navegador —el sitio sigue sin `<picture>`,
ver `ADR-0056`— y el nombre evita que alguien lo suponga.

**La rotación 360 queda fuera.** Un set son 24 a 36 fotogramas que el visor pinta todos del mismo
tamaño: multiplicarlos por tres triplica los objetos del bucket sin darle al navegador ninguna
elección que hacer.

**El tope sigue en 1200**, como en `ADR-0056`: la ficha no llega a 700 px.

## Alternativas descartadas

**Renombrar `url_webp` y dejar el `srcset` para después.** Sería renombrar una columna para borrarla
dos semanas más tarde. Las dos deudas viven en la misma superficie.

**Guardar solo los anchos (`anchos integer[]`) y derivar la URL.** Ahorra la tabla hija, y obliga a
repetir el patrón de las keys en el mapeador de respuestas y a mantenerlo sincronizado con el
cargador a mano. Es el acoplamiento que estamos cerrando.

**JSONB en `imagen_producto`.** Una migración y ninguna tabla nueva, pero no hay **ni un** `jsonb` en
las sesenta migraciones del proyecto: estrenar un patrón de persistencia para esto es deuda
disfrazada de ahorro.

**`[attr.srcset]` armado a mano, sin `IMAGE_LOADER`.** Funciona en el navegador, y en SSR
`createPreloadLinkTag()` recibe el `srcset` *reescrito por la directiva*, que sería `undefined`: el
`<link rel=preload>` de las imágenes prioritarias saldría sin `imagesrcset` y precargaríamos la de
1200 mientras el `<img>` elige la de 480. Justo en la portada, que es donde están las prioritarias.

**Una escalera fija de anchos.** El procesamiento del estudio no amplía: de los 33 productos con
material, catorce tienen hasta 2000, seis llegan a 1200, tres solo tienen 600 y 480, y uno solo
tiene 480. Qué anchos existen es un dato de cada imagen, y se mira en disco.

## Consecuencias

- **El navegador elige.** Lo que queda por comprobar es el número: los 211 KiB de
  `image-delivery-insight` en la portada, medidos con `--etiqueta` antes y después **en la misma
  sesión**, que es la regla de uso del arnés.
- **Tres o cuatro viajes por foto donde antes había uno**, al cargar. Es el precio de tener las
  variantes en el bucket, y lo paga un script que corre de vez en cuando.
- **Las fotos ya subidas no se arreglan solas.** La V60 le dio a cada imagen una variante única con
  lo que tenía, así que el sitio sirve hoy exactamente lo que servía, con un `srcset` de una
  entrada. Los anchos de verdad llegan al correr `--rehacer-imagenes`.
- **Dos trampas de `NgOptimizedImage`, encontradas al ejecutar y no al leer.** Todas sus entradas
  salvo `ngSrc` están congeladas tras inicializar: un objeto literal en `loaderParams` cambia de
  identidad en cada ciclo de detección y revienta con NG02953 en el primer refresco —se memoiza por
  imagen—, y al cambiar de imagen el `<img>` tiene que nacer de nuevo, con un `@for` de una sola
  entrada. Las dos están en `apps/web/CLAUDE.md`.
- **Una imagen que se publica en un solo ancho lleva `disableOptimizedSrcset`** —el hero, la línea
  del carrito, los fotogramas del visor—, o Angular anuncia un 2x que es el mismo archivo.
- **El `og:image` deja de servir un AVIF.** Su comentario decía servir "el original" por
  compatibilidad con los previsualizadores; desde `ADR-0056` el original era el AVIF, así que ese
  razonamiento llevaba un mes sin proteger nada.
- **Sigue sin haber respaldo para navegadores sin AVIF.** El JPEG de vista previa existe ahora en el
  bucket, así que un `<picture>` costaría solo la plantilla — pero es otra decisión y no se toma
  aquí.
