package co.tecnosport.api.application.retracto;

import co.tecnosport.api.domain.reintegro.MedioReintegro;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code medioPreferido} es para el caso corriente: el comprador dice por dónde quiere el dinero en
 * el mismo mensaje en que manda los datos de la cuenta, o sea después de radicar. Se anota aquí si
 * la solicitud no lo traía; si ya lo traía y no coincide, el dominio se niega — cambiarlo borraría
 * la constancia de lo que pidió.
 */
public record RegistrarReintegroComando(
    UUID solicitudId,
    BigDecimal monto,
    MedioReintegro medio,
    MedioReintegro medioPreferido,
    String comprobante,
    String actor) {

  public RegistrarReintegroComando {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("El actor no puede estar vacío.");
    }
  }
}
