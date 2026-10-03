package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.EstadoBorrador;

/** Aprobado o renovación: es la huella con que la ingesta reconoce el producto, y se queda. */
public final class BorradorNoEliminableException extends RuntimeException {

  public BorradorNoEliminableException(EstadoBorrador estado) {
    super("Un borrador " + estado + " no se borra: solo los que están en revisión o rechazados.");
  }
}
