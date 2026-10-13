# ADR-0085: la tienda vende su talla, y al proveedor se le pide la suya

Fecha: 2026-10-13
Estado: aceptada
Amplía: `ADR-0084` (el SKU del proveedor en la variante) y `ADR-0081` (las tallas de cada prenda).

## Contexto

Dos proveedores publican tallas que no son las que le quedan a quien compra:

| Proveedor | El anuncio dice | Lo que se vende |
|---|---|---|
| Meraki | «Ojo Horma reducida pedir una talla más · Tallas M-L-XL-XXL» | S, M, L, XL |
| Imperio Wicho | «Tallas disponibles 40 a la 45 · Aumentar 2 tallas» | 38 a 43 |
| Imperio Wicho | «Tallas disponibles 39 a la 43 · Aumentar 1 talla» | 38 a 42 |
| Imperio Wicho | «Tallas disponibles 36 a la 45 eur» | 35 a 44 |
| Imperio Wicho | «Tallas disponibles en la foto · Horma normal Dama, Aumenta 1 Cab» | las de la foto para dama; una menos para caballero |

Hoy la ingesta copia las tallas del anuncio tal como vienen, y la ficha le ofrece al cliente la XXL
de un polo que le queda como una XL.

Corregir solo lo que se muestra no basta. Si la ficha dice 38 y el proveedor la llama 40, cuando
alguien compre la 38 hay que pedirle la 40. Ese dato no estaba en ninguna parte: la variante guarda
el SKU del proveedor (`V114`), pero no su talla. Ninguna prueba atraparía el error, y quien lo
pagaría es el cliente, con el tenis equivocado en la caja.

La primera versión de este cambio guardaba la talla del proveedor ya calculada en cada variante al
aprobar. Las dos revisiones adversariales la tumbaron con las pruebas en verde: una variante cambia
después de aprobar por tres caminos que no pasan por la aprobación —`CambiarTalla`, agregar un
color desde la foto principal, agregar una variante en el panel—, y en los tres la talla guardada
quedaba vieja o no existía. En el listado, la ausencia se lee «pídela como se vende».

## Decisión

Decidida por el negocio el 13 de octubre de 2026.

1. **Un número, no una tabla** (`AjusteDeTalla`). El borrador guarda cuántas tallas más arriba
   nombra el proveedor la que se vende: Meraki 1, «aumentar 2 tallas» 2, una talla EUR 1. Si un
   anuncio dice las dos cosas, se suman. Con el número se sabe qué pedirle por **cualquier** talla,
   también por una que quien revisa agregó en el panel. Una tabla fija dejaría esa sin equivalencia.
   - En letras se cuenta sobre XS…XXXL, y una agrupada parte por parte: «S-M» un paso arriba es
     «M-L».
   - En números se cuenta con el paso de la escala de la categoría donde se publica: de 1 en 1 en
     calzado, de 2 en 2 en jeans. La del proveedor puede quedar fuera de la escala de la tienda: la
     45 más una es la 46.
   - Una talla que no está en la escala, o una escala sin números —un pantalón de dama en 6, 8, 10
     bajo la escala de letras—, no dicen el paso: no se cuenta. Contar de 1 en 1 daría una talla 9
     que no existe. Una de cero o negativa tampoco existe.
   - Va de -4 a 4. Más que eso no es una horma, es un error de lectura.
2. **La talla EUR es la nacional más una.** Es la equivalencia más citada en Colombia, y el
   negocio la eligió. Si un proveedor demuestra otra, se cambia aquí y en su prueba.
3. **El producto guarda el ajuste, no la talla calculada** (`producto.ajuste_de_talla`, `V115`).
   `AprobarBorrador` copia el del borrador. La talla del proveedor se cuenta al consultar, desde la
   talla que la variante tiene **hoy**: una talla cambiada, un color agregado o una variante nueva
   quedan bien sin que esos caminos sepan que esto existe, y también el que llegue mañana.
4. **Lo que no se puede contar no se vende.** La XXXL una talla más arriba, la talla única o una
   talla que no es de la escalera: aprobar falla con `TALLA_DEL_PROVEEDOR_DESCONOCIDA` antes de
   crear nada. Sin saber qué pedirle al proveedor, esa talla no se ofrece.
5. **El listado de pedidos del panel la dice al lado de la línea**: «Pídela al proveedor en talla
   M». Viaja aparte de las líneas, en `PedidosPaginadosRespuesta.tallasAlProveedor`, porque la línea
   es la misma que ve el comprador.
   - **Cuando no se puede contar, lo dice**: una XXXL agregada a mano en un producto con ajuste 1
     sale como «pregúntale al proveedor qué talla pedir». Callarlo sería pedir la XXXL.
   - **No se congela en el pedido.** Congelarla habría tocado `CrearPedido`, que es del checkout. El
     costo: si alguien cambia la talla de una variante después de vender, el pedido viejo cuenta
     desde la nueva; y si cambia la escala de una categoría, cambia lo que dicen los pedidos viejos.
     Las tallas se corrigen antes de vender, y ninguna escala de hoy tiene un paso que vaya a
     cambiar: se aceptó.
6. **Un anuncio unisex con tallas distintas por género se parte en dos borradores**: uno para
   calzado de dama con las tallas de la foto y otro para calzado de caballero con una menos, cada
   uno con su ajuste. No se modela el género en la variante. Dos borradores de distinto ajuste no se
   unen: una talla no diría qué pedir.

## Consecuencias

- Este cambio deja la estructura; los dos siguientes la llenan:
  - el extractor lee «horma reducida», «aumentar N tallas» y «eur», y la ingesta corre las tallas
    del anuncio y anota el ajuste;
  - el lector de fotos lee las tallas impresas en la foto —el rótulo de la esquina, que no es el
    pie de La Riverah—, y el anuncio unisex se parte en dos.

  Mientras tanto, todo borrador nace con ajuste 0 y se comporta como antes.
- El aviso de `TALLA_DEL_PROVEEDOR_DESCONOCIDA` en el panel no dice cuál talla falló: los errores
  del panel se traducen por código (`core/errores/mensaje-de-error.ts`) y no llevan datos. El
  `detail` del servidor sí la nombra.
- El panel todavía no deja ver ni cambiar el ajuste de un borrador. Hace falta en cuanto la
  ingesta lo llene: si el modelo lee mal un «aumentar», quien revisa tiene que poder corregirlo.
- Los cuatro borradores de Meraki que ya estaban en revisión el 13 de octubre no se recalculan:
  hay que volver a ingerirlos o corregir sus tallas a mano.
- Lo ya aprobado no cambia: sus productos nacen con ajuste 0, y se piden como se venden.
