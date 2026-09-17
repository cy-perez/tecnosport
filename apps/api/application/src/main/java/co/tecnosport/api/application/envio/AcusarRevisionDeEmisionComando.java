package co.tecnosport.api.application.envio;

import java.util.Objects;
import java.util.UUID;

/**
 * Qué emisión se miró, quién la miró y qué concluyó.
 *
 * <p>Por identificador y no por pedido: un pedido puede tener varias emisiones —un fallo se
 * reintenta— y decir "la del pedido" no nombra ninguna en concreto.
 */
public record AcusarRevisionDeEmisionComando(UUID emisionId, String actor, String nota) {

  public AcusarRevisionDeEmisionComando {
    Objects.requireNonNull(emisionId, "El id de la emisión no puede ser nulo.");
    Objects.requireNonNull(actor, "El actor no puede ser nulo.");
  }
}
