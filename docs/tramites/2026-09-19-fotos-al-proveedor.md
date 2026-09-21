# Trámite: fotos de producto al proveedor, 19 de septiembre de 2026

De los 96 productos de la lista del 12 de septiembre, **33 tienen foto y 63 no**. De los 33, solo
**21 llegan a las cuatro tomas** que pide la ficha; los otros 12 se quedaron en una o dos.

**Estado:** **enviado el 21 de septiembre de 2026**. Redactado el 19 de septiembre.
**El texto completo, con los 73 productos agrupados por marca**, lo genera el proceso del catálogo
en `catalogo/fotos/pedido-al-proveedor.txt` — esa carpeta no se versiona porque pesa 1,2 GB, así
que aquí queda lo que hay que saber para mandarlo y para no volver a levantarlo desde cero.

---

## Por qué importa más de lo que parece

**Xiaomi no tiene ni una sola foto, y son 38 de los 96 productos.** Es la marca más grande de la
lista por un factor de dos sobre la siguiente, y está entera sin material. Apple tampoco tiene
ninguna, de cinco.

| Marca | Con foto | Sin foto |
|---|---|---|
| Xiaomi | 0 | **38** |
| Samsung | 12 | 6 |
| JBL | 7 | 7 |
| Motorola | 6 | 0 |
| Honor | 4 | 2 |
| Apple | 0 | **5** |
| Lenovo | 2 | 0 |
| TCL | 1 | 1 |
| Nintendo | 1 | 0 |
| Realme, Sony, Bose | 0 | 1 cada una |

Las que sí tienen salieron de Open Icecat, que cubre bien a Samsung, Motorola y JBL y no cubre a
Xiaomi. O sea que **esto no se arregla insistiendo con la misma fuente**: hay que pedirlo.

## Qué se pide

Lo dice el propio encabezado del archivo generado, y conviene no rebajarlo al copiarlo:

> - 4 fotos: frontal, posterior, en ángulo y un detalle
> - fondo blanco, sin marcas de agua, sin textos ni precios encima
> - mínimo 1500 x 1500 píxeles, en JPG o PNG
> - si el equipo viene en varios colores, una frontal por color
>
> Si tienen el paquete de imágenes del fabricante, con eso basta.

El mínimo de 1500 px no es un capricho: por debajo de eso el retoque al estándar de estudio amplía
y se nota. Ya se midió una vez, el 15 de septiembre, con las fotos de Open Icecat en baja
resolución.

## De dónde pueden salir, y de dónde no

El `LEEME.txt` del pipeline lo deja escrito y es criterio legal, no de gusto:

- **Sí:** fotos propias del inventario; el paquete de imágenes del proveedor o distribuidor (el
  único que viene con permiso para revender); el portal de partners de la marca si la tienda está
  registrada como revendedor autorizado.
- **No:** las salas de prensa —Apple Newsroom, Samsung Mobile Press, los centros de medios de
  Xiaomi—, porque sus condiciones autorizan uso editorial o personal, **no publicar el producto en
  una tienda**. Tampoco fotos tomadas de otras tiendas ni bancos de imágenes sin licencia comprada.

Y de cada foto que entre hay que **anotar el origen antes de publicar**.

## Los que ya tienen algo

El archivo generado marca cuáles van a medias, para no pedir de más ni de menos — por ejemplo
`Charge 6 [ya tenemos 2: faltan 2]` o `Choice Earbuds X7e [ya tenemos 1: faltan 3]`. Son doce
productos en esa situación.

---

## Respuesta

_Sin respuesta todavía. Enviado el 21 de septiembre de 2026._

Cuando llegue, anotar aquí **la fecha y qué mandaron exactamente**, marca por marca: lo que decida
si esto se cierra o hay que insistir no es "contestaron", es cuántos de los 73 productos quedaron
con sus cuatro tomas. Xiaomi son 38 de los 96 y Apple 5, y las dos están enteras sin material, así
que una respuesta que no las traiga no mueve el problema.

Lo que hay que rehacer al recibirlas: el retoque al estándar de estudio
(`fotos-estudio-degradado`), `npm run cruce-catalogo` para ver qué pasa a publicable, y
`node tools/cargar-catalogo.mjs --galeria SKU` para los que ya están cargados con una sola foto —
son siete de los trece.
