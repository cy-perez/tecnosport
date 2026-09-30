package co.tecnosport.api.application.proveedores;

import java.util.UUID;

/** La aprobación nombró una foto que no es de la publicación del borrador. */
public final class FotoNoEsDelBorradorException extends RuntimeException {

  public FotoNoEsDelBorradorException(UUID mensajeId) {
    super("El mensaje " + mensajeId + " no es una foto de este borrador.");
  }
}
