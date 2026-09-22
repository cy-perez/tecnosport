# ADR-0056 — Se publica la variante web, no la maestra

**Fecha:** 2026-09-21
**Estado:** aceptado. Aplicado sobre las 91 imágenes del catálogo real el mismo día.

## Contexto

El procesamiento de estudio deja, por cada toma, una **maestra** de archivo y seis tamaños en dos
formatos. El cargador del catálogo leía de `catalogo/fotos/estudio/{id}/maestra` y subía eso: un
JPEG de 2000 px y medio megabyte.

Nadie lo había notado porque no se podía notar. El rendimiento del sitio no era medible mientras
las tarjetas trajeran ocho peticiones a `picsum.photos`, y ese bloqueo duró desde la Fase 6 hasta
que el catálogo real quedó cargado con sus fotos en GCS. La primera medición válida, el 21 de
septiembre de 2026, puso el número delante: **el recurso más pesado de la portada y de la ficha era
la misma foto, 635 kB**, y el LCP de la ficha estaba en 8,1–8,5 s.

El total: **22,01 MiB** en 91 imágenes, para pintar tarjetas de menos de 400 px de ancho en un
teléfono.

## Decisión

**Se sube la variante web: el AVIF más grande disponible hasta 1200 px.** La maestra se queda donde
está, que es para lo que sirve — archivo, y la única fuente donde se puede leer la resolución real
de la toma.

Tres consecuencias de forma, cada una con su motivo:

- **`image/avif` entra en la lista blanca de la API** (`TiposDeImagen`). No es un detalle: la
  extensión de la key sale de esa tabla, así que sin la entrada el objeto habría quedado en el
  bucket con la extensión equivocada.
- **Juzgar y subir dejan de ser la misma lista.** `material-catalogo.mjs` sigue leyendo la maestra
  para decidir si una foto alcanza —`dimensionesJpeg` solo sabe leer JPEG— y lleva, además, la
  variante web de cada toma, resuelta por nombre de archivo y no por posición.
- **Sin variante, el cargador se niega.** Caer a la maestra sería volver al defecto sin decirlo: la
  carga terminaría "bien" y el sitio seguiría pesando diez veces lo que debe.

## Alternativas descartadas

**`srcset` con varias variantes.** Es lo correcto a largo plazo y no se hizo ahora: con una sola
variante de 1200 el peso baja un 90 %, y lo que queda por ganar afinando tamaños es una fracción
de lo que queda por ganar en otra parte —el elemento más grande de la portada resultó ser texto
retrasado 1,3–1,5 s por JavaScript—. Optimizar primero lo segundo grande es cómo se gasta una
tarde sin mover la aguja. *(Se hizo el 22 de septiembre de 2026, cuando la medición le puso
número: 211 KiB en la portada. Ver `ADR-0057`.)*

**WebP en vez de AVIF.** El procesamiento produce los dos; AVIF pesa ~3× menos al mismo tamaño
(58 kB contra 175 del JPEG de 1200). El costo es de compatibilidad, abajo.

**Subir el JPEG de 1200** (175 kB). Compatible con todo, y tres veces más pesado que el AVIF. Se
prefirió el peso; si algún día el respaldo hace falta, se pone en la plantilla.

**Convertir en el backend al confirmar la subida.** Mete procesamiento de imágenes en un servicio
que hoy no lo tiene, y una dependencia nueva, para hacer algo que el procesamiento de estudio ya
hizo antes de que la foto llegara.

## Consecuencias

- **22,01 MiB → 2,18 MiB** en las 91 imágenes del catálogo real. El LCP de la ficha bajó de
  8,1–8,5 s a 5,2–5,7 s, con la carga del recurso de 128 ms a 54 y el *element render delay* de
  1.133 ms a 209.
- **La portada no mejoró, y eso también es un resultado**: su LCP no tiene fases de recurso, ni
  antes ni después. El elemento más grande de la portada nunca fue una imagen.
- **Cuatro productos suben menos de 1200 px** porque su foto original no daba para más —el
  procesamiento no amplía—: 800 el Honor X7D, 600 el Xtreme 4 y el Boombox 4, 480 el PartyBox. Son
  los mismos que el cruce ya marcaba por debajo del mínimo de calidad.
- **No hay respaldo para navegadores sin AVIF.** El proyecto no declara una matriz de navegadores
  —no hay `browserslist` ni una línea en `docs/00`— y el sitio no envuelve las imágenes en un
  `<picture>`. Quien abra la tienda en un Safari anterior al 16 (2022) no verá la foto. Queda
  dicho aquí y en el javadoc de `TiposDeImagen`.
- **`url_webp` pasó de ser una promesa a ser un nombre falso**: guarda la URL de un AVIF. *(Se
  borró el 22 de septiembre de 2026, junto con el `srcset` que este ADR dejó fuera: `ADR-0057`.)*
  Renombrar
  la columna cruza dominio, base, DTO y contrato generado; queda anotado como deuda, no hecho.
- **Lo ya subido no se arregla solo**, de ahí `--rehacer-imagenes`. Sube lo nuevo y **después**
  borra lo viejo: al revés, una corrida cortada a la mitad deja el producto publicado y sin una
  sola foto.
