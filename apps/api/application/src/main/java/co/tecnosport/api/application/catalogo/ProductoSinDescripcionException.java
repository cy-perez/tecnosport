package co.tecnosport.api.application.catalogo;

/**
 * Editar un producto deja la descripción vacía. Es lo que la ficha dice del producto, y desde el 3
 * de octubre de 2026 es obligatoria aquí igual que al aprobar un borrador de proveedor.
 */
public final class ProductoSinDescripcionException extends RuntimeException {

  public ProductoSinDescripcionException() {
    super("La descripción del producto no puede quedar vacía.");
  }
}
