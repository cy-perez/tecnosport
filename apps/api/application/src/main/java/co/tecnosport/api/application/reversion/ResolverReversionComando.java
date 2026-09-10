package co.tecnosport.api.application.reversion;

import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reversion.DesenlaceReversion;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code monto}, {@code medio} y {@code comprobante} solo se usan con {@code
 * REINTEGRADO_DIRECTAMENTE}: es el unico desenlace en que el dinero sale de aqui.
 */
public record ResolverReversionComando(
    UUID reversionId,
    DesenlaceReversion desenlace,
    String resumenParaElComprador,
    BigDecimal monto,
    MedioReintegro medio,
    String comprobante,
    String actor) {

  public ResolverReversionComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
