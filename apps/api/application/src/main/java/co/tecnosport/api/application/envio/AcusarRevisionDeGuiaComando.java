package co.tecnosport.api.application.envio;

import java.util.Objects;

/**
 * Qué guía se miró, quién la miró y qué concluyó.
 *
 * <p>Se identifica por número y no por el id interno porque es el número el que se teclea, se busca
 * en la página de la transportadora y se pega en un correo. Es único en toda la tabla —lo impone
 * {@code uq_guia_envio_numero}—, así que nombra una sola guía.
 */
public record AcusarRevisionDeGuiaComando(String numeroGuia, String actor, String nota) {

  public AcusarRevisionDeGuiaComando {
    Objects.requireNonNull(numeroGuia, "El número de la guía no puede ser nulo.");
    Objects.requireNonNull(actor, "El actor no puede ser nulo.");
  }
}
