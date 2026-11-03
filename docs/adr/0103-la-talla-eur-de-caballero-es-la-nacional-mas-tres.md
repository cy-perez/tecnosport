# ADR-0103: la talla EUR de caballero es la nacional más tres, y el proveedor guarda un ajuste por género

Fecha: 2026-11-03
Estado: aceptada
Enmienda: `ADR-0085` (punto 2, «la talla EUR es la nacional más una») y `ADR-0089` (las tallas
por género de Tenis Al Pie).

## Contexto

`ADR-0085` decidió que la talla EUR es la nacional más una, «la equivalencia más citada en
Colombia», y dejó escrito que se cambiaría si un proveedor demostraba otra. Tenis Al Pie mandó su
guía de tallas (`COL / USA / EURO / CM`), y dice dos cosas distintas:

| | Guía del proveedor | Lo que hacía el sitio |
|---|---|---|
| Dama | COL 35–39 = EUR 36–40 | más una: coincide |
| Caballero | COL 37–41 = EUR 40–44 | más una: dos números de diferencia |

La guía tiene errores propios —dos filas de dama con «USA 8», centímetros de caballero que saltan
de 0,5 en 0,5 y de 1 en 1—, así que no se le creyó sin medir. El 3 de noviembre de 2026 el negocio
midió por dentro un tenis de caballero marcado EUR 42: **26,5 cm**, que en la guía es la **39**
colombiana. La 41 que el sitio ofrecía por ese par era dos tallas más grande que la del pie.

El daño era silencioso: quien calza 39 compraba la «39» y el panel decía pedirla en EUR 40, que es
una 37. Ninguna prueba lo atrapaba, porque todas afirmaban la regla equivocada.

Y el modelo no podía decir la verdad: `TallasPorGenero` guardaba **un** ajuste para las dos listas.

## Decisión

1. **El proveedor guarda un ajuste por género** (`TallasPorGenero.ajusteDama`,
   `ajusteCaballero`; `V153`: `proveedor.ajuste_dama` y `ajuste_caballero`). La migración copia el
   ajuste que había a los dos; el 3 de caballero de Tenis Al Pie se pone en el panel, porque es un
   dato del negocio y no del esquema. Para tallas EUR: **1 en dama y 3 en caballero**.
2. **Un tenis unisex de ajustes distintos se parte en dos borradores**: el de dama con EUR 36–39,
   que se venden como 35–38, y su gemelo de caballero con EUR 40–44, que se venden como 37–41. Es el
   mecanismo de «Horma normal Dama, Aumenta 1 Cab» (`ADR-0085` punto 11, `delOtroGenero`): las
   mismas fotos, huella de gemelo, se renuevan y se agotan juntos. Con un solo ajuste en los dos,
   el unisex sigue siendo uno.
   - Se descartó venderlo unisex en tallas de dama: un hombre tendría que traducir su talla solo.
3. **La aprobación verifica el ajuste del género de la categoría**
   (`AjusteDeTallaDeOtroGeneroException`, `AJUSTE_DE_TALLA_DE_OTRO_GENERO`, 409). El ajuste no
   sigue a la categoría: un borrador leído de dama que quien revisa pasa a Calzado caballero se
   quedaba con el 1. Se rechaza cuando el tenis lleva **el ajuste del otro género** y no el suyo, y
   cuando va a Calzado unisex. Un ajuste que no es el de ninguno se respeta: es una horma corregida
   a propósito. El género de la categoría sale del prefijo de su slug
   (`GeneroDeCalzado.deCategoria`): una hija lleva siempre el slug de su padre delante.
4. **Imperio Wicho no cambia en este paso.** Su «36 a la 45 eur» se lee en el texto antes de saber
   el género (`AjusteDelAnuncio`, suma 1). Si la medida vale para cualquier EUR de caballero, a él
   también le aplica, pero no se mueve sin medir un par suyo. Mientras tanto, quien revisa corrige
   el ajuste en el panel.

## Consecuencias

- **Lo ya publicado no se recalcula solo.** Un tenis de caballero aprobado con ajuste 1 en la 39–43
  pasa a la 37–41 con ajuste 3 por SQL: la talla del proveedor se cuenta al consultar (`ADR-0085`
  punto 3), así que los pedidos viejos siguen pidiendo la misma EUR —el mismo par físico—. Los
  unisex publicados no se pueden arreglar en un producto: se retiran y vuelven partidos con la
  siguiente tanda. Los borradores en revisión se vuelven a ingerir.
- **El ajuste ya no se lee "1 si son EUR"**: la ayuda del panel dice 1 en dama y 3 en caballero.
- **Sigue sin haber guía de tallas para el comprador** (`ADR-0089`). Los centímetros son la única
  medida que no depende del género ni del país; mostrarlos en la ficha queda para otro cambio.
- La partición pide que el proveedor maneje los dos géneros: con una sola lista no hay nada que
  partir, y el ajuste es el de esa lista.
