package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.EstadoBorrador;

/** El borrador ya no está en revisión: se aprobó, se rechazó o es una renovación. */
public final class BorradorNoEditableException extends RuntimeException {

  public BorradorNoEditableException(EstadoBorrador estado) {
    super("El borrador ya no está en revisión: está " + estado + ".");
  }
}
