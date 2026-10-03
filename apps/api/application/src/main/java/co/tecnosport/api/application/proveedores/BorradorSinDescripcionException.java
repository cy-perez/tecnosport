package co.tecnosport.api.application.proveedores;

/**
 * Ni el extractor la redactó ni la aprobación la trae. La descripción es lo que la ficha dice del
 * producto, y es obligatoria desde el 3 de octubre de 2026.
 */
public final class BorradorSinDescripcionException extends RuntimeException {

  public BorradorSinDescripcionException() {
    super("El borrador no tiene descripción. Escríbela antes de aprobar.");
  }
}
