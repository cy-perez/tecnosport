package co.tecnosport.api.application.proveedores;

/**
 * La aprobación agrupó fotos en una prenda que no se puede convertir en variante: sin color, o con
 * fotos de dos colores. Una prenda es una variante, y una variante tiene un solo valor de Color.
 */
public final class PrendaIncoherenteException extends RuntimeException {

  public PrendaIncoherenteException(int prenda, String motivo) {
    super("La prenda " + prenda + " " + motivo + ".");
  }
}
