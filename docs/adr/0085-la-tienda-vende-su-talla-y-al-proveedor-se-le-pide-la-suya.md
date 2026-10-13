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

7. **La ingesta lee el ajuste del texto, por reglas y no por el modelo** (`AjusteDelAnuncio`,
   decidido el 13 de octubre de 2026). Las frases de los proveedores son fijas y cortas, y un ajuste
   que el texto no escribe correría todas las tallas sin que nadie lo note:
   - «pedir una talla más» suma 1, y «aumentar N talla(s)» suma N, en número o en letra;
   - «eur» pegado a las tallas suma 1;
   - las dos cosas se suman, y «horma normal» o nada no suma.

   «Horma normal Dama, Aumenta 1 Cab» no se lee aquí: no lleva la palabra «talla», y el ajuste es
   solo para caballero. Lo resuelve partir el anuncio en dos.
8. **El borrador nace con las tallas que se venden** (`BorradorProducto.conTallasCorridas`): «Tallas
   M-L-XL-XXL» con horma reducida son S, M, L, XL, y las de cada tono también. Al correrlas aún no hay
   categoría, así que el paso de las numéricas sale del tipo de prenda: de 2 en 2 en pantalón y
   short, como sus rangos, y de 1 en 1 en lo demás. La aprobación vuelve a contar sobre la escala de
   verdad.
   - Todas o ninguna. Si una no se puede correr, se quedan las del anuncio con `TALLAS_SIN_CORRER`.
   - Si se corrieron, `TALLAS_CORRIDAS` pide confirmar en el anuncio cuántas: un borrador corrido no
     se publica solo.
9. **El panel muestra el ajuste y deja corregirlo** («Cómo nombra las tallas el proveedor»), y
   dice cómo se pide cada talla con lo guardado: «S → M, XXXL → ?».
   - Cambiar el ajuste no vuelve a correr las tallas: las del formulario son siempre las que se
     venden.
   - Como las tallas, el ajuste escrito viaja con la aprobación aunque no se haya guardado.

10. **El lector de fotos lee el rótulo de tallas** (`LecturaDeFoto.tallasImpresas`, campo `tallas`
    del esquema). Es el recuadro blanco con «35 / 36 / 38 / 41 / 42» que Imperio Wicho imprime en la
    esquina cuando escribe «Tallas disponibles en la foto!», y no es el pie de La Riverah.
    - Si el texto no dice tallas, el producto llega con todas las de sus fotos, de menor a mayor.
    - El panel recibe las de cada foto, ya corridas con el ajuste del borrador, y llena con ellas la
      tabla de color y talla.
    - Las lecturas guardadas antes de este cambio no traen el campo, y se leen vacías.
11. **«Horma normal Dama, Aumenta 1 Cab» parte el anuncio** (`AjusteDelAnuncio.porGenero`,
    `BorradorProducto.delOtroGenero`). El de dama se queda con las tallas de la foto y sin ajuste.
    El de caballero queda una abajo, con ajuste 1.
    - Los dos tienen las mismas fotos, el mismo texto y el mismo costo.
    - El título dice el género («Tenis estilo Nike para dama», «… para caballero»,
      `TituloPorGenero`).
    - El de caballero lleva una huella derivada de la de dama (`HuellaProveedor.delGemelo`), para
      que aprobar los dos no choque.
    - **Los dos viven y se agotan juntos.** Cuando el anuncio vuelve, la foto reconoce a los dos.
      Manda el de dama, y la renovación o el «AGOTADO» se aplica también al de caballero, que se
      encuentra por su huella de gemelo. Lo destapó la revisión adversarial con las pruebas en verde:
      la consulta por foto no tiene orden y caía en cualquiera de los dos, y un agotado dejaba al
      otro a la venta sin existencias.
    - Si el anuncio está además en tallas EUR, las dos partes suman la de EUR.
    - Un bolso del mismo anuncio no talla: ni se corre ni se parte.

## Consecuencias

- **Una talla única no guarda ajuste**: se pide como se vende. Antes quedaba con un ajuste que
  bloqueaba la aprobación desde un campo que el panel no mostraba.
- **Lo que queda abierto del anuncio unisex:**
  - Las tallas de cada foto llegan a su prenda solo si la lectura vio en ella un único color. Una
    foto «negro con suela blanca» leída como dos colores no tiene prenda propuesta, y quien revisa
    marca sus tallas a mano.
  - Los contadores del lote cuentan el par como un borrador nuevo, no como dos.
  - Si la extracción no dio título ni precio, los dos nacen sin huella. Si quien revisa les escribe
    el mismo título, el segundo choca al aprobarse.
- El esquema del lector cambió: la prueba `LectorDeFotosClaudeContratoTest` contra la API real se
  corre antes de fusionar (`-PintegracionExterna=true` con `ANTHROPIC_API_KEY`).
- Una redacción nueva del proveedor —«horma pequeña», «pide una más»— no se lee hasta agregarla a
  `AjusteDelAnuncio` con su prueba. Mientras tanto, el borrador nace sin ajuste y quien revisa lo pone
  en el panel.
- El aviso de `TALLA_DEL_PROVEEDOR_DESCONOCIDA` en el panel no dice cuál talla falló: los errores
  del panel se traducen por código (`core/errores/mensaje-de-error.ts`) y no llevan datos. El
  `detail` del servidor sí la nombra.
- Los cuatro borradores de Meraki que ya estaban en revisión el 13 de octubre no se recalculan:
  hay que volver a ingerirlos o corregir sus tallas a mano.
- Lo ya aprobado no cambia: sus productos nacen con ajuste 0, y se piden como se venden.
