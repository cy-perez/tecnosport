package co.tecnosport.api.application.proveedores;

/**
 * El tenis se aprueba en la rama de un género con el ajuste de talla del otro (ADR-0103). Tenis Al
 * Pie nombra las de dama una talla más arriba y las de caballero tres: un borrador leído de dama y
 * publicado en Calzado caballero vendería cada talla dos números corrida. Tampoco se publica en
 * Calzado unisex, donde el mismo par se llamaría de dos maneras.
 */
public final class AjusteDeTallaDeOtroGeneroException extends RuntimeException {

  public AjusteDeTallaDeOtroGeneroException(String mensaje) {
    super(mensaje);
  }
}
