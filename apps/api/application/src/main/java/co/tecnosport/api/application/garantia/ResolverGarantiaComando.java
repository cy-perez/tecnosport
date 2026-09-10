package co.tecnosport.api.application.garantia;

import co.tecnosport.api.domain.garantia.DesenlaceGarantia;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code monto}, {@code medio} y {@code comprobante} solo se usan con desenlace {@code REINTEGRO}.
 * Van en el mismo comando y no en otro endpoint porque devolver el dinero es una de las tres
 * salidas de la garantía, no un trámite aparte que alguien pueda olvidar después de resolver.
 */
public record ResolverGarantiaComando(
    UUID reclamacionId,
    DesenlaceGarantia desenlace,
    String resumenParaElComprador,
    BigDecimal monto,
    MedioReintegro medio,
    String comprobante,
    String actor) {

  public ResolverGarantiaComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
