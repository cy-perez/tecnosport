package co.tecnosport.api.application.proveedores.tecnologia;

import java.time.LocalDate;

/**
 * Esa misma lista, con el mismo contenido, ya entró. Repetirla volvería a reponer lo que se vendió
 * desde entonces.
 */
public final class ListaDeTecnologiaYaImportadaException extends RuntimeException {
  public ListaDeTecnologiaYaImportadaException(LocalDate fechaLista) {
    super("La lista del " + fechaLista + " ya entró con este mismo contenido.");
  }
}
