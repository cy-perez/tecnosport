package co.tecnosport.api.application.retracto;

import co.tecnosport.api.domain.reintegro.MedioReintegro;
import java.math.BigDecimal;
import java.util.UUID;

public record RegistrarReintegroComando(
    UUID solicitudId, BigDecimal monto, MedioReintegro medio, String comprobante, String actor) {

  public RegistrarReintegroComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
