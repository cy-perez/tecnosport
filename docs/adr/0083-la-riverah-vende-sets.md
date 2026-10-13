# ADR-0083: La Riverah vende sets, y la falda con short es una falda short

Fecha: 2026-10-12
Estado: aceptada
Reemplaza en parte: `ADR-0078`, punto 6 (los precios que no son del producto solo se muestran),
para los proveedores que arman sets.

## Contexto

La Riverah anuncia casi todas sus prendas con un acompañante. El chat del 9 de octubre de 2026
trae ocho anuncios así entre trece:

| Anuncio | Precios | Costo del set |
|---|---|---|
| Falda Mallatex | 49.900 · Set falda + básica 88.000 | 88.000, anunciado |
| Falda | 59.900 · Básica 48.000 | 107.900, suma |
| Falda (tiene short) | 59.900 · Set básica + falda 99.000 | 99.000, anunciado |
| Oversize (dos anuncios) | 55.000 · «las gorras tienen un valor de 35 mil» | 90.000, suma |
| Polo 1.1 | 55.000 · Gorra 35.000 · Polo + gorra 85.000 | 85.000, anunciado |
| Talla plus | 65.000 · Gorra 35.000 · Dúo 95.000 | 95.000, anunciado |
| Seda fría | 35.000 · Gorra 35.000 · Dúo 65.000 | 65.000, anunciado |

Hasta este día el borrador era la prenda sola, y el set quedaba en `precios_adicionales` para
mirarlo (`ADR-0078`, punto 6): «el catálogo no tiene combos». Las fotos sí los muestran. La falda
sale con la básica puesta, y cada camiseta del álbum con una gorra distinta.

Además, «Falda de dama ( tiene short )» salía `SHORT`, porque el prompt decía que una falda short
es un short. El catálogo tiene una hoja «Falda - Short», creada en la nube con una errata en el
slug: `ropa-dama-falta-short`.

## Decisión

Decidida por el negocio el 12 de octubre de 2026.

1. **Para La Riverah el borrador es el set, no la prenda sola.**
   - La falda con su blusa es un **conjunto**. Se publica en Conjuntos de dama con el tipo
     `CONJUNTO_FALDA` y el título «Conjunto falda y blusa». Un short o un pantalón con su blusa
     son `CONJUNTO_SHORT` y `CONJUNTO_PANTALON`.
   - La camiseta o el polo con su gorra es un **set**. **Conserva el tipo de la prenda** y se
     publica en Camisetas o en Polos, donde lo busca quien quiere una camiseta: «Set camiseta
     oversize y gorra», «Set polo importado y gorra».
   - **La gorra del set es la de la foto de ese SKU.**
   - La «básica» del proveedor es la blusa del catálogo.
2. **El costo es el precio del set si el proveedor lo anuncia.** Si no lo anuncia, es la suma de
   la prenda y su acompañante. Sobre ese costo se calcula el precio de venta con el factor y los
   topes de siempre. Una promoción por cantidad no arma nada.
3. **Solo para quien está marcado** (`Proveedor.armaSets`, `V113`). Hoy es La Riverah; a los demás
   se les sigue comprando la prenda sola.
4. **La falda que trae short es `FALDA_SHORT`**, lo diga como lo diga, para todos los
   proveedores. Su categoría es «Falda - Short». En un conjunto, la descripción dice «falda short»
   aunque la categoría sea Conjuntos. `V113` corrige la errata del slug, o crea la categoría donde
   no existe.
5. **El peso del envío** (dado por el negocio):
   - un conjunto de dama y uno de caballero pesan 700 g, el promedio de sus categorías;
   - la gorra pesa 100 g, que se suman al promedio de Camisetas o de Polos
     (`producto.peso_adicional_gramos`).

## Cómo se hace

**El modelo clasifica; el dominio decide y suma.**

- El extractor dice de cada precio adicional su `clase` (`acompanante`, `combo`, `promocion`) y
  la `prenda` que acompaña (`blusa`, `gorra`, `otra`).
- Cuando hay set, escribe aparte su descripción y su nombre en inglés (`set`).
- El título, el tipo, el precio y la descripción del producto **siguen siendo los de la prenda
  sola**. El modelo no sabe de quién es el mensaje, y no hace falta que lo sepa.

`ArmadorDeSets` hace el resto en el dominio, y `ResolverBorrador` lo llama solo si el proveedor
arma sets:

- elige el combo o, si no hay, suma la prenda y **un** acompañante: si el modelo repite la gorra,
  sumarlas todas inflaría el costo;
- toma para esa suma el precio de la prenda que el extractor leyó, si está escrito en el anuncio:
  en «Básica 48.000 / Falda 59.900» el primer precio del texto es el de la blusa;
- compone el título (`TituloDeSet`) sobre el de la prenda, que ya pasó por las reglas de réplica y
  de polo;
- deja la prenda sola entre los precios adicionales (`PRENDA_SOLA`) para que quien revisa la siga
  viendo;
- la gorra solo arma set con una camiseta o un polo, y la blusa solo con una prenda de abajo.

**El costo se contrasta con el texto.** El precio de la prenda ya pasaba por el patrón de precios.
Los del combo y del acompañante, hasta este día, solo se mostraban, y ahora son el costo. Si alguno
no está escrito en el anuncio —completo, «88.000», o en miles, «35mil»—, el borrador lleva
`PRECIO_INCONSISTENTE`.

**Un set no se confunde con su prenda sola.** Su huella es la de la prenda más el acompañante
(`HuellaProveedor.deSet`). Por la foto, cada anuncio se compara **solo con los productos de su misma
clase**: la huella visual sabe si su producto es un set y con qué acompañante. La foto de un set es
la de su prenda: sin esa separación, la camiseta RV101860 que ya se vendiera sola se renovaría con
el costo del set, y un anuncio de la falda sola le bajaría el costo al conjunto.

**Un agotado alcanza al set.** El anuncio de agotado también se arma, para que su huella sea la del
set. Y uno que no trae los precios del acompañante —«AGOTADO polo RV102376»— agota además los sets
de esa prenda, encontrados por la huella con cada acompañante o por la foto entre los sets.

## Consecuencias

- **Lo ya aprobado no cambia.** Una prenda de La Riverah publicada sola sigue así. Si el
  proveedor la vuelve a anunciar con su gorra, sale un borrador nuevo del set; la sola no se
  renueva con ese anuncio y vence con el tiempo, como cualquier producto que deja de aparecer.
- Un set anunciado otra vez se renueva: por su huella si trae SKU, y por la foto entre los sets si
  no lo trae, que es el caso de casi todas las faldas.
- Los borradores que ya estaban en revisión no se recalculan: la extracción quedó guardada, y
  para ver el set hay que volver a ingerir el chat.
- Un set renovado que cambia de precio recalcula su huella con el título y el precio
  (`Producto.actualizarPrecioProveedor`), igual que hoy le pasa a una prenda con código. Lo sigue
  reconociendo su foto.
- **Lo que queda abierto:** el peso adicional de la gorra se fija al aprobar y el panel todavía no
  lo deja corregir. Si quien aprueba pone un set con gorra en otra categoría, los 100 g van igual.
