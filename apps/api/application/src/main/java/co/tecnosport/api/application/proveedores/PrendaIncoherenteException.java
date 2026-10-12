package co.tecnosport.api.application.proveedores;

/**
 * La aprobación agrupó fotos en una prenda que no se puede convertir en variante: sin color, con
 * fotos de dos colores, con el nombre de otra y sin foto que las distinga, o con tallas que el
 * producto no tiene. Una prenda es una variante, y una variante tiene un solo valor de Color.
 */
public final class PrendaIncoherenteException extends RuntimeException {

  public PrendaIncoherenteException(int prenda, String motivo) {
    this("La prenda " + prenda + " " + motivo + ".");
  }

  private PrendaIncoherenteException(String mensaje) {
    super(mensaje);
  }

  /** La foto, no una prenda, es la incoherente: dice un color y no dice de qué prenda es. */
  static PrendaIncoherenteException deLaFoto(int posicion, String motivo) {
    return new PrendaIncoherenteException("La foto " + posicion + " " + motivo + ".");
  }
}
