package co.tecnosport.api.application.retracto;

import co.tecnosport.api.domain.retracto.MedioReembolso;
import java.math.BigDecimal;
import java.util.UUID;

public record RegistrarReembolsoComando(
    UUID solicitudId, BigDecimal monto, MedioReembolso medio, String comprobante, String actor) {

  public RegistrarReembolsoComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
