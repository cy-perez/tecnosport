# ADR-0084: el álbum es un producto, cada SKU una prenda, y lo que cuesta de más no se compra

Fecha: 2026-10-13
Estado: aceptada
Reemplaza: `ADR-0078`, punto 2, «un texto y un álbum: un producto por SKU» (10 de octubre de 2026),
y el «camino A» del 11 de octubre (un producto por SKU, y unirlos a mano en el panel).
Amplía: `ADR-0083` (los sets de La Riverah) y `ADR-0052` (el tope de la galería).

## Contexto

La ingesta de La Riverah del 12 de octubre de 2026 en dev se revisó borrador por borrador. Salieron
**117 borradores de 13 anuncios**:

| Anuncio | Fotos | Borradores | Lo que estaba mal |
|---|---|---|---|
| Falda 59.900 + básica 48.000 | 2 | 2 | uno por SKU |
| Oversize 55.000 (11:38) | 13 | 13 | uno por SKU, y la nota de la gorra sin leer |
| Jeans Diesel - Amiri 125.000 | 12 | 12 | uno por SKU |
| Bermudas 65.000 | 10 | 10 | uno por SKU |
| Polo 1.1 y gorra | 23 | 23 | uno por SKU |
| Jeans AA Levi's 55.000 | 4 | 4 | uno por SKU, y titulado «Jean plus» |
| Talla plus y gorra | 19 | 19 | uno por SKU |
| Jean licrado 49.000 | 10 | 10 | uno por SKU |
| Jean AA plus 55.000 | 4 | 4 | uno por SKU |
| Seda fría y gorra | 14 | 14 | uno por SKU |
| Oversize 55.000 (21:06, sin SKU) | 12 | 1 | la nota de la gorra sin leer |

Todos salvo el último seguían la regla del 10 de octubre: cada SKU impreso en el pie de una foto
era un producto. Para quien revisa, eso es aprobar veintitrés veces el mismo texto, el mismo precio
y el mismo set con otra foto, y después tener veintitrés productos que el proveedor vende como uno.

Tres cosas más:

- **La nota de la gorra.** «Nota: Las gorras tiene un valor de 35mil-» está en el prompt palabra
  por palabra como acompañante, y el modelo no la devolvió en ninguno de los dos anuncios oversize.
  Salieron como la camiseta sola a 55.000; el set cuesta 90.000.
- **El título copiado.** El prompt traía de ejemplo «Jeans AA plus para caballero», que es otro
  anuncio del mismo proveedor. «*Jeans AA para caballero* ⭕️Marca Levis» salió «Jean plus para
  caballero importado»: ni plus ni sin marca.
- **El precio.** Los jeans Diesel - Amiri a 125.000 no se le compran al proveedor.

## Decisión

Decidida por el negocio el 13 de octubre de 2026.

1. **El álbum es un solo producto, y cada SKU es una prenda** (`RepartoDeFotos.disenosDelAlbum`).
   - El borrador lleva todas las fotos, sin código —cada SKU es de su prenda— y con las tallas del
     texto más las de los pies que el texto no nombra.
   - El panel propone una prenda por SKU, con sus fotos, el color que la lectura vio y las tallas de
     su pie (`VerBorrador.PrendaDelAlbum`). Es un punto de partida: quien aprueba lo corrige.
   - El álbum de varias marcas legibles sigue partiéndose por marca (11 de octubre).
   - Sin SKU no hay prendas que proponer: lo normal es un modelo en varios colores.
2. **El SKU queda en la variante** (`variante.referencia_proveedor`, V114). La aprobación lo pone
   en las variantes de su prenda, una por talla.
3. **Un SKU agotado retira su prenda, no el producto.** «AGOTADO polo RV102376» marca las
   variantes de ese SKU como **agotadas por el proveedor** (`variante.agotada_por_proveedor`,
   `Variante.seVende`): la ficha las muestra sin disponibilidad, y el carrito y el pedido no las
   venden. Las demás siguen a la venta. Si no queda ninguna, el producto se agota. Si el proveedor
   vuelve a anunciar ese SKU —solo o en el álbum—, la prenda vuelve a la venta y el producto se
   renueva. Decidido por el negocio: es lo que pasaba antes con un producto por SKU, y no vende lo
   que no hay.
   - **No es `INACTIVA`.** Una variante inactiva no se hidrata en ninguna parte: un pedido ya
     pagado de ella no podría emitir su guía (`ArmadorDeBultos` no la encontraría), y el guarda de
     ventas no la vería al borrar el producto. La primera versión de este cambio hacía eso, y la
     revisión adversarial lo atrapó con las pruebas en verde.
   - **El anuncio de un SKU no cambia el costo.** Puede ser la prenda sola de un álbum que se
     aprobó como set, o al revés. Un cambio de precio del álbum lo trae el álbum.
   - **Un anuncio más viejo que el último visto no cambia la disponibilidad**, como en el producto.
   - **El álbum que vuelve con los mismos SKU es el mismo producto**, empiece por la foto que
     empiece: se renueva y no deja otro borrador.
4. **Diez fotos por producto**: la principal y nueve de galería (`Producto.TOPE_DE_GALERIA = 9`).
   Un álbum trae más diseños que fotos caben; el panel elige las diez primeras y quien aprueba
   cambia cuáles. La prenda del álbum que se queda fuera no se crea, y eso no es un error.
5. **El acompañante y el combo escritos se leen del texto** (`AcompananteEscrito`), para todos los
   proveedores, si el modelo no los devolvió:
   - la gorra o la básica con su precio en la misma línea —«Gorra 🧢 $35.000», «Básica 48.000»,
     «las gorras tiene un valor de 35mil»— es un acompañante;
   - la línea que nombra un set, un dúo o un combo, o que suma dos prendas —«Polo+ gorra $ 85.000»,
     «Dúo $95.000»— es un combo, y el set se arma a ese precio y no con la suma. Si no dice la
     prenda, es la del único acompañante del texto;
   - la línea con diez cifras o más es el teléfono de pedidos, no un precio.

   Con eso el oversize es un set de 90.000, y sobre ese costo se calcula el precio de venta.
6. **El título no promete lo que el anuncio no escribe** (`CalificativosDelTitulo`): «plus»,
   «oversize», «slim», «licrado» y los de su lista salen del título si el anuncio no los dice. Y la
   réplica que nombra su marca en el texto lleva «estilo» y la marca aunque el modelo la haya
   olvidado: «Jean estilo Levi's para caballero». El ejemplo del prompt se cambió por uno que no se
   puede copiar a otro anuncio.
7. **Lo que cuesta de más no se compra** (`TopeDeCostoPorTipo`), para todos los proveedores: un
   pantalón —jean—, una sudadera —jogger— o un short —bermuda— de más de 90.000 se descarta con
   `PRECIO_SOBRE_TOPE`. Exactamente 90.000 pasa. Se compara el costo de la prenda sola —el mismo con
   que se armaría el set: en «Básica 48.000 / Pantalón 95.000» es el del pantalón—, y nunca en un
   agotado. Con varios productos en el anuncio, solo se descarta ese.

## Consecuencias

- **Lo ya aprobado no cambia.** Los productos de un SKU que ya existían siguen siendo uno por SKU,
  con su huella de referencia.
- Los borradores que estaban en revisión no se recalculan: el reparto se decide al ingerir. Para
  ver un álbum como un borrador hay que volver a ingerir el chat.
- «Unir borradores» y la sugerencia «posible mismo diseño» se quedan para lo que todavía sale
  partido —los borradores viejos—, pero un álbum nuevo ya no los necesita.
- Un álbum renovado más tarde, con SKU nuevos para los mismos diseños —el pie trae dos bloques con
  dos fechas—, se reconoce por la foto, como cualquier producto sin código. Un SKU nuevo que no
  está en ninguna variante no retira nada.
- El tope de 90.000 vive en el código del dominio, no en la configuración del proveedor: es una
  regla del negocio para todos. Cambiarlo es cambiar un número y su prueba.
- **Lo que queda abierto:**
  - El panel todavía no deja ver ni cambiar a mano qué prendas agotó el proveedor: vuelven a la
    venta cuando el proveedor las vuelve a anunciar. Si un agotado llegó por error, hoy se corrige
    en la base.
  - Las fotos de una prenda agotada siguen en la galería de la ficha.
  - Una prenda del álbum que no cupo en las diez fotos no tiene variante, y su agotado no encuentra
    nada.
