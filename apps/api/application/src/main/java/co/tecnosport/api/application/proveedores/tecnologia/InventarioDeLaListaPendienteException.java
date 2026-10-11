package co.tecnosport.api.application.proveedores.tecnologia;

import java.time.LocalDate;

/**
 * Los borradores de una lista cuyo inventario no es el último que entró (ADR-0079): o el inventario
 * de esa lista no se importó, o ya entró uno más nuevo.
 */
public final class InventarioDeLaListaPendienteException extends RuntimeException {

  public InventarioDeLaListaPendienteException(LocalDate fechaLista) {
    super(
        "Los borradores de la lista del "
            + fechaLista
            + " van después de su inventario, y el último inventario importado es de otra lista.");
  }
}
