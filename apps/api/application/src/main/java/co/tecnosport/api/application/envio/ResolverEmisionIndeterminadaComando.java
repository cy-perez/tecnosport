package co.tecnosport.api.application.envio;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Qué emisión se resolvió, qué vio quien la miró y quién fue.
 *
 * <p>{@code enviosEnPlataforma} solo tiene sentido con {@link VeredictoDeEmision#CON_ENVIO}, y el
 * caso de uso lo exige ahí: decir "el envío está" sin decir cuál no recupera nada.
 */
public record ResolverEmisionIndeterminadaComando(
    UUID emisionId,
    VeredictoDeEmision veredicto,
    List<String> enviosEnPlataforma,
    String actor,
    String nota) {

  public ResolverEmisionIndeterminadaComando {
    Objects.requireNonNull(emisionId, "El id de la emisión no puede ser nulo.");
    Objects.requireNonNull(veredicto, "El veredicto no puede ser nulo.");
    Objects.requireNonNull(actor, "El actor no puede ser nulo.");
    enviosEnPlataforma = List.copyOf(Objects.requireNonNullElse(enviosEnPlataforma, List.of()));
  }
}
