# ADR-0052 — La galería acumula, la principal se reemplaza

**Fecha:** 2026-09-21
**Estado:** aceptado. Es el cuarto hueco de la misma familia que `adr/0047` (crear marcas),
`adr/0046` (corregir una medida) y `adr/0051` (despublicar): el dominio sabía hacerlo y no había
puerta.

## Contexto

`Producto` tiene una lista `galeria` desde la Fase 1. `MapeadorCatalogo` la lee y la ordena,
`ProductoRespuesta.galeria` la publica y `ficha.page.ts` la pinta —`[imagenPrincipal, ...galeria]`—.
La tubería pública estaba completa **y nadie podía llenarla**: en `AdminProductoControlador` solo
existían los dos endpoints de `imagen-principal`, no había caso de uso, ni pantalla, ni el cargador
la usaba.

Mientras tanto, `catalogo/fotos/estudio` tenía 48 carpetas con **cuatro tomas cada una**, en cinco
resoluciones y con AVIF, ya retocadas al estándar. A la ficha llegaba una. El Galaxy S25 Ultra, de
$4.999.900, se veía con una sola foto.

## Decisión

### 1. Agregar no borra nada del bucket; quitar borra un objeto concreto

Es la diferencia de fondo con la imagen principal, y de ella salen casi todas las demás.

| | Imagen principal | Galería |
|---|---|---|
| Qué hace una subida nueva | **reemplaza** la anterior | **suma** a las que hay |
| Limpieza del bucket | `eliminarPorPrefijo("productos/{id}/principal-")` en cada confirmación | ninguna al agregar |
| Cómo se borra un objeto | por prefijo, conservando la key recién subida | `eliminar(objectKey)`, por la key exacta |

Borrar por prefijo en la galería se llevaría las imágenes hermanas, que siguen publicadas. Pasar la
key entera como prefijo funcionaría hoy por la forma de las keys —terminan en la extensión, nada
puede extenderlas— y es exactamente la clase de casualidad que deja de ser cierta sin que nadie se
entere, así que el puerto tiene un `eliminar(objectKey)` propio que dice lo que hace.

**El precio de no limpiar al agregar**: una subida que se firma y nunca se confirma deja un objeto
que ninguna limpieza reclama. Se acepta a sabiendas —la alternativa era arriesgar borrar fotos
vivas— y por eso `SolicitarSubidaDeImagenDeGaleria` comprueba el tope **antes** de firmar: para no
invitar a subir lo que no va a caber.

### 2. El orden es uno más que el mayor, y quitar deja huecos

`siguienteOrdenDeGaleria()` devuelve `max(orden) + 1`, no `galeria.size()`. Con el tamaño, borrar la
última y subir otra repetiría un orden que ya existe. Los huecos no molestan a nadie: el mapeador
**ordena, no cuenta**, así que `0, 2, 3` se pinta igual que `0, 1, 2`.

**No hay reordenar**, y es una decisión, no un olvido. Las cuatro tomas del estudio vienen
numeradas y se suben en ese orden, así que el caso no aprieta; y hacerlo bien pide un índice único
sobre `(producto_id, orden)` que un intercambio viola a mitad de sentencia. Cuando haga falta se
paga entonces, con el problema delante.

### 3. La misma foto no entra dos veces

Comparando el hash del contenido, que es justo para lo que `docs/02` lo puso: *"para detectar
recargas duplicadas del mismo archivo"*. Cuatro tomas que se suben una por una desde un formulario
son el sitio natural para repetir una sin darse cuenta, y una galería con la misma foto dos veces no
se lee como un error del sistema: se lee como descuido del que vende.

Sale como `409 IMAGEN_DE_GALERIA_DUPLICADA`, que es accionable, y el panel lo dice con esas
palabras.

### 4. Tope de ocho, y no es un dato de negocio

`Producto.TOPE_DE_GALERIA` es una **barandilla**. Las imágenes se suben una por una desde el panel y
desde un script, y sin tope un bucle equivocado llena la ficha y el bucket sin que nada chille: el
precio de eso es espacio pagado todos los meses y una ficha que nadie puede recorrer. Ocho es holgado
para las cuatro tomas del estándar y para un producto que llegue con más.

No se consultó como dato de negocio porque no lo es: subirlo es cambiar un número, es reversible y
no se pierde nada. Un dato de negocio de verdad —una tarifa, un plazo— se pregunta; un límite de
seguridad se pone y se dice dónde está.

### 5. El detalle del panel trae la galería; la lista no

`GET /api/v1/admin/productos/{id}` devuelve `ProductoAdminDetalleRespuesta`, que es la de siempre más
`galeria`. La lista sigue devolviendo `ProductoAdminRespuesta` pelada: trae veinte productos por
página y es deliberadamente liviana —lo dice `MapeadorRespuestasProductoAdmin` desde la Fase 4—, así
que meter ahí hasta ocho imágenes por fila engordaría cada página para que la pantalla que las usa
no sea esa.

Y la galería del panel lleva `id` y `orden`, que la vitrina no expone. Sin el id no hay forma de
pedir que se quite una: sería una galería de solo escritura.

### 6. Quitar pregunta antes, dentro de la fila

Mismo criterio que publicar (`adr/0051`): borra el archivo además de la fila y no tiene vuelta, así
que un clic no puede bastar. La pregunta dice las dos cosas —que sale de la ficha y que el archivo se
borra— **antes**, no después. Sin diálogo del CDK: es una pregunta de una línea, y montar una trampa
de foco para eso es más ceremonia que la decisión.

### 7. La key que se confirma tiene que ser del prefijo de galería, y no puede repetirse

Las dos guardas nacieron de una revisión adversarial el mismo día, y las dos son la regla dura #7.

**La primera guarda miraba solo `productos/{id}/`**, copiada literal de `ConfirmarImagenPrincipal`,
donde bastaba porque había un solo prefijo por producto. Con dos, un cliente podía confirmar
`productos/{id}/principal-abc.jpg` como imagen de galería — y entonces el siguiente reemplazo de la
imagen principal, que limpia ese prefijo **entero**, borraba el objeto que la galería estaba
sirviendo. Una foto rota en una ficha publicada, causada por el propio sistema, sin una línea de
error en ningún sitio. `ClavesDeGaleria` existe para que el prefijo que se escribe y el que se
exige sean la misma cadena, como `ClavesDeRotacion`.

**La segunda: el mismo objeto no entra dos veces.** El rechazo por hash no lo cubre, porque el hash
lo calcula el cliente: dos POST con la misma key y hashes distintos creaban dos filas apuntando al
mismo archivo, y quitar una borraba el objeto por la key exacta dejando a la hermana rota. Toda la
lógica de borrado de esta rama se apoya en que una key pertenece a una sola fila, así que eso se
comprueba en vez de suponerse.

### 8. Lo que el panel numera es la posición visible, no el `orden` guardado

Consecuencia directa de §2 que la primera versión de la pantalla se saltó: como quitar deja huecos,
tras quitar la del medio de tres el panel ofrecía *"imagen 1"* e *"imagen 3"* sobre dos fotos — y
ese texto es el nombre accesible del único control que las distingue. `orden` es la clave de
ordenamiento del agregado; lo que se le enseña a una persona es el `$index`.

## Alternativas descartadas

- **Un campo `galeria` en `ProductoAdminRespuesta`, sin DTO nuevo.** Más corto de escribir y peor de
  usar: la lista del panel pagaría el peso de las imágenes de veinte productos en cada página para
  no enseñar ninguna.
- **Reordenar desde el panel.** Ver arriba. El índice único que lo haría correcto es el que hace
  difícil el intercambio, y el caso que lo pediría no existe todavía.
- **Devolver el hash en la respuesta de la galería**, para que un script pueda saber qué falta por
  subir sin subirlo. Expondría un dato que sirve para una sola cosa —rechazar duplicados— y esa cosa
  la decide el servidor. El cargador resuelve lo mismo saltándose los productos que ya tienen
  galería, que es la pregunta que de verdad le importa.
- **Borrar la galería entera al despublicar.** Ni se consideró en serio, y se escribe para que quede
  cerrado: retirar de la vitrina no destruye material (`adr/0051`).

## Lo que queda sin respaldo, dicho a propósito

- **El tope y la unicidad de `orden` solo viven en el agregado.** La invariante gemela de la
  principal sí está en la base (`ux_imagen_principal_por_producto`, `V1`). Aquí no: dos POST
  concurrentes contra el mismo producto calculan el mismo `siguienteOrdenDeGaleria()` e insertan los
  dos. Hoy no se dispara —el panel lo opera una persona y el cargador sube en serie— y un índice
  único sobre `(producto_id, orden)` es justo el que haría difícil el reordenamiento de §2. Se deja
  sin respaldar **sabiéndolo**; si algún día la galería se llena desde dos sitios a la vez, la
  decisión de §2 hay que volver a tomarla junto con esta.
- **El tope 8 vive en tres sitios**: el dominio, que manda, y sendas copias en el panel y en el
  cargador que solo evitan ofrecer lo que el servidor va a rechazar. Si sube a 12, el panel seguirá
  diciendo "hasta 8" sin que falle nada. Se acepta porque ninguna de las dos copias protege una
  invariante; la salida barata, el día que moleste, es que el detalle del panel lo traiga.
- **El panel no usa `urlPreferida` ni `NgOptimizedImage`** para las miniaturas, igual que la sección
  de imagen principal que ya estaba. Es deuda heredada y la galería la multiplica por ocho; no se
  arregla aquí porque toca la regla compartida del catálogo, no esta pantalla.

## Consecuencias

- Un producto puede llegar a la vitrina con hasta nueve imágenes, la principal incluida.
- Quedan objetos huérfanos en el bucket cuando una subida se firma y no se confirma. No hay limpieza
  automática, a diferencia del prefijo de la principal; si algún día pesa, se hace con una regla de
  ciclo de vida del bucket sobre `galeria-` por antigüedad, no borrando desde el código.
- El cargador (`tools/cargar-catalogo.mjs`) sube las tomas que sobran de cada producto y tiene
  `--galeria-todos` para rellenar lo que quedó a medias. Estrenado con los trece de la carga del 21
  de septiembre: catorce imágenes entraron a seis galerías; los otros siete llegaron con una sola
  foto de Icecat y no hay nada que rellenar.
