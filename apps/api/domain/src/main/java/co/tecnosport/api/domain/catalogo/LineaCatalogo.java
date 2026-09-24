package co.tecnosport.api.domain.catalogo;

/**
 * Las tres líneas de negocio (docs/00-producto.md). Es el nivel <b>grueso</b> del catálogo: el
 * filtro principal del sitio pinta un control por cada una, y de aquí cuelgan las {@link
 * Categoria}, que son filas en base de datos y afinan dentro de la línea.
 *
 * <p><b>{@code CELULARES} se llamó así hasta el 14 de septiembre de 2026</b>, cuando el negocio
 * amplió lo tecnológico a relojes, audífonos, cargadores, cables, power banks, consolas, parlantes,
 * computadores, tablets y proyectores. Ninguno de esos es un celular, y colgarlos de una línea con
 * ese nombre habría sido una mentira de modelo que se paga en cada consulta y en cada URL. Son
 * <b>categorías</b> dentro de {@code TECNOLOGIA}, que es para lo que existe {@link Categoria}.
 *
 * <p>Y esa decisión ya se cobró: el 24 de septiembre de 2026 el negocio dejó de vender cables,
 * cargadores y power banks, y {@code V62} lo resolvió con un {@code delete} de tres filas. Con
 * trece valores de enum habría sido un cambio de código, de traducciones, de pruebas y del filtro
 * que ve el comprador. El nivel grueso sigue siendo de tres.
 *
 * <p>Por eso este enum sigue teniendo tres valores y no trece: <b>una línea nueva es un cambio de
 * código, de traducciones, de pruebas y del filtro que ve el comprador; una categoría nueva es una
 * fila</b>. Si algo se puede modelar como fila, no se modela como valor de enum.
 */
public enum LineaCatalogo {
  ROPA_Y_CALZADO,
  BOLSOS,
  TECNOLOGIA
}
