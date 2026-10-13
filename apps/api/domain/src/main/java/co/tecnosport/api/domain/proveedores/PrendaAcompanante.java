package co.tecnosport.api.domain.proveedores;

/**
 * La prenda que acompaña a la del anuncio en un acompañante o en un combo: la «básica» de las
 * faldas de La Riverah, que el catálogo llama blusa, y la gorra de sus camisetas y polos.
 *
 * <p>Los gramos son los que el set pesa de más sobre el promedio de la categoría donde se publica
 * (decidido por el negocio el 12 de octubre de 2026). La gorra pesa 100 g, y el set de camiseta o
 * polo con gorra se publica en Camisetas o en Polos: viaja en la bolsa con el promedio de esa
 * categoría más la gorra. La blusa no suma nada, porque la falda con blusa se publica en Conjuntos,
 * cuyo promedio ya es el del conjunto entero.
 */
public enum PrendaAcompanante {
  BLUSA(0),
  GORRA(100),
  /** Una que no es ninguna de las anteriores: no arma set, el borrador sigue siendo la prenda. */
  OTRA(0);

  private final int gramosAdicionales;

  PrendaAcompanante(int gramosAdicionales) {
    this.gramosAdicionales = gramosAdicionales;
  }

  public int gramosAdicionales() {
    return gramosAdicionales;
  }
}
