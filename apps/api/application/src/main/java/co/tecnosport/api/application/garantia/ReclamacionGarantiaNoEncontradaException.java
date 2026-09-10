package co.tecnosport.api.application.garantia;

import java.util.UUID;

public class ReclamacionGarantiaNoEncontradaException extends RuntimeException {

  public ReclamacionGarantiaNoEncontradaException(UUID id) {
    super("No existe una reclamación de garantía con el id " + id + ".");
  }
}
