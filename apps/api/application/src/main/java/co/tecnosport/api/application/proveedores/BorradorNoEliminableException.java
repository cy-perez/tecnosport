package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.EstadoBorrador;

/**
 * Aprobado o renovación con su producto vivo: es la huella con que la ingesta reconoce el producto,
 * y se queda. Sin producto sí se borra; ver {@code EliminarBorrador}.
 */
public final class BorradorNoEliminableException extends RuntimeException {

  public BorradorNoEliminableException(EstadoBorrador estado) {
    super(
        "Un borrador "
            + estado
            + " no se borra mientras su producto exista: solo los que están en revisión, los"
            + " rechazados y los que ya no tienen producto.");
  }
}
