package co.tecnosport.api.application.catalogo;

/**
 * Un producto cuelga siempre de una hoja del árbol, nunca de una rama.
 *
 * <p>"Ropa › Dama" es una rama: si un producto cuelga ahí y otro de "Ropa › Dama › Camisas",
 * entonces "lo que hay en Dama" tiene dos respuestas distintas —lo que cuelga directamente y todo
 * lo que hay debajo— y ninguna de las dos es la que espera quien hizo clic en el menú. El catálogo
 * se vuelve imposible de contar y de filtrar sin decidir esa ambigüedad en cada consulta.
 *
 * <p>Es la otra mitad de {@link CategoriaConProductosException}: aquella impide darle hijas a una
 * categoría que ya tiene productos, y esta impide darle productos a una que ya tiene hijas. Juntas,
 * ningún nodo intermedio llega a tener productos nunca.
 */
public final class CategoriaNoEsHojaException extends RuntimeException {

  public CategoriaNoEsHojaException(String nombre) {
    super(
        "La categoría '"
            + nombre
            + "' tiene subcategorías: elige una de ellas para colgar el producto.");
  }
}
