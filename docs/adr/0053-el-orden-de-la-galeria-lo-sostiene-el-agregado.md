# ADR-0053 — El orden de la galería lo sostiene el agregado, no un índice único

**Fecha:** 2026-09-21
**Estado:** aceptado. Completa `adr/0052`, que dejó la galería acumulando y sin forma de
cambiarle el orden.

## Contexto

`adr/0052` dejó escrito el pendiente con estas palabras: *"No se puede reordenar la galería. Las
cuatro tomas del estudio vienen numeradas y se suben en ese orden, así que el caso no aprieta
todavía. Hacerlo bien pide un índice único sobre `(producto_id, orden)` que un intercambio viola a
mitad de sentencia."*

Esa frase da por supuesto que el índice es el camino, y el problema es el intercambio. Al ir a
construirlo resultó ser al revés.

## Decisión

### 1. No se añade ningún índice único, y la invariante se queda en el agregado

**El índice no se puede escribir como haría falta.** Tres hechos de PostgreSQL, encadenados:

1. Un `UNIQUE` **diferible** —el que sobreviviría a un intercambio a mitad de sentencia— tiene que
   ser una *constraint*, no un índice suelto: `DEFERRABLE` solo existe para constraints.
2. Una constraint `UNIQUE` **no admite `WHERE`**. Solo un índice único puede ser parcial.
3. Sin el `WHERE tipo = 'GALERIA'`, la unicidad de `(producto_id, orden)` se lleva por delante los
   **fotogramas del set de rotación**, que comparten `producto_id` con la galería y numeran desde
   cero. Un producto con visor 360 dejaría de poder tener galería.

O sea: parcial y diferible a la vez no existe. Y sin diferir, cualquier reordenamiento que pase por
un estado intermedio lo viola.

Lo que queda es lo que ya había: la invariante vive en `Producto`. No es una renuncia — es donde
vivía desde el primer día. `agregarImagenGaleria` rechaza el orden ocupado desde `adr/0052`, y
**toda escritura de imágenes pasa por el agregado**: el repositorio no expone ninguna forma de
tocar una fila de `imagen_producto` sin cargar su producto.

Lo que se pierde es la red de seguridad para el día en que alguien escriba SQL a mano. Se acepta, y
queda anotado aquí para que ese día se sepa que no hay red.

### 2. Se manda la galería entera, no un movimiento

`PUT /api/v1/admin/productos/{id}/galeria/orden` con la lista completa de ids.

La alternativa —`POST .../galeria/{imagenId}/subir`— es más cómoda de escribir y peor: con dos
pestañas abiertas sobre el mismo producto, dos movimientos parciales se aplican uno tras otro sobre
estados distintos y el resultado es un orden que **nadie pidió**, sin que nada falle. Diciendo el
orden completo, la segunda petición habla de una galería que ya no existe —le falta o le sobra
alguna imagen— y eso se detecta: `422`, y no se graba nada.

Es `PUT` y no `PATCH` porque lo que viaja es el estado completo del orden. Mandarlo dos veces deja
lo mismo.

### 3. Reordenar renumera de 0 a n-1 y cierra los huecos

`adr/0052` decidió que quitar **no** renumera: `0, 2, 3` se pinta igual que `0, 1, 2` porque la
ficha ordena y no cuenta, y renumerar obligaría a reescribir filas que nadie tocó.

Reordenar es el caso contrario: las filas ya se están reescribiendo. Cerrar los huecos ahí no cuesta
nada y deja los números diciendo la verdad. No cambia nada de lo que se ve.

### 4. El adaptador modifica la fila; no la vuelve a guardar entera

Un `save` con una entidad nueva del mismo id también haría un `UPDATE`, pero obliga a rellenar todas
las columnas — y la única que el dominio no conoce es `creada_en`. Rehacerla con `Instant.now()`
dejaría toda la galería como recién creada cada vez que alguien mueve una foto de sitio: un dato
real, perdido por un detalle de implementación. De ahí el único mutador de
`ImagenProductoJpaEntity`, `cambiarOrden`, y la prueba de Testcontainers que mira las fechas.

### 5. En el panel, botones de un puesto; no arrastre

El arrastre no existe para quien navega con teclado, y montarlo accesible es un mecanismo entero
—`cdkDropList`, teclas, anuncios— para mover cuatro fotos. Dos botones por fila, *subir* y *bajar*.

En cada extremo, el botón que no lleva a ningún sitio **se quita, no se deshabilita**: un control
deshabilitado no es enfocable y para un lector de pantalla no está. Es la misma corrección que la
galería llena necesitó dos días antes.

Y el foco sigue a la imagen que se movió, no al botón que se pulsó: si se quedara quieto, pulsar
*subir* dos veces movería dos imágenes distintas.

## Corrección posterior (2026-09-21)

El punto 2 de arriba dice *"la segunda petición habla de una galería que ya no existe —le falta o
le sobra alguna imagen— y eso se detecta: `422`, y no se graba nada"*. La implementación no hacía
eso: partía el caso en dos respuestas según la dirección —**404** si a la lista le sobraba una
imagen (porque reutilizaba `ImagenDeGaleriaNoEncontradaException`, la del borrado), **422** si le
faltaba—. En la mitad del 404 el panel pintaba el mensaje escrito para quitar una foto.

Lo encontró la revisión adversarial del 21 de septiembre. Se corrigió el código y no el ADR, porque
el ADR tenía razón: el recurso del `PUT` es la galería del producto, y existe. El 404 se queda
donde el subrecurso de verdad no existe, que es el `DELETE` de una imagen.

## Alternativas descartadas

- **Índice único parcial y renumerado en dos fases** (órdenes negativos temporales y después los
  definitivos). Funciona, y duplica las escrituras de cada reordenamiento para proteger contra una
  escritura a mano que ninguna parte del sistema hace hoy.
- **Un `POST .../subir` por movimiento.** Descartado en el punto 2.
- **Orden fraccionario** (`0.5` entre dos) para no reescribir vecinos. Resuelve un problema de
  escala —listas largas, muchos movimientos— que una galería de ocho no tiene, y trae el suyo: los
  decimales se agotan y hay que renumerar igual, solo que más tarde y con menos avisos.
- **Que la ficha ordene por `creada_en`.** Es lo que ya hacía de facto al subirlas en orden, y no es
  un orden: es el rastro de en qué orden se subieron. Cambiarlo obligaría a volver a subir la foto.
