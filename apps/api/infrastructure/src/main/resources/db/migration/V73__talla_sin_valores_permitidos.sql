-- La talla de ropa deja de tener valores permitidos (30 de septiembre de 2026).
--
-- `SembradorCatalogo` daba de alta `Talla` con S, M, L y XL, y `ValorAtributo` rechaza cualquier
-- valor fuera de la lista. La primera exportacion real del proveedor de ropa trajo polos en
-- M, L, XL y XXL: aprobar el borrador habria fallado con VALOR_NO_PERMITIDO por la XXL, y lo
-- mismo con un XS o con una talla numerica. La lista de tallas la dicta cada prenda, no el
-- catalogo; con la lista vacia el valor es libre, que es como V72 la crea en una base nueva.
-- Las variantes que ya existen con S, M, L o XL no se tocan: siguen siendo validas.
delete from atributo_valor_permitido v
using atributo a
where v.atributo_id = a.id
  and lower(a.nombre) = 'talla';
