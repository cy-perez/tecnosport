package co.tecnosport.api.domain.envio;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un movimiento del paquete, tal como lo contó la transportadora. Se guardan todos, en orden, y no
 * se sobrescriben (adr/0022) — lo mismo que hace {@code HistorialPedido} con las transiciones del
 * pedido y por el mismo motivo: el día de la reclamación hay que poder decir qué se supo y cuándo.
 *
 * <p>Por eso hay <strong>dos</strong> instantes y no uno. {@code ocurrioEn} es cuando la
 * transportadora dice que pasó; {@code recibidoEn} es cuando nos enteramos. Se separan porque no
 * coinciden: un webhook perdido y recuperado por la conciliación llega días después del hecho, y
 * confundirlos haría parecer que el paquete se movió cuando en realidad solo llegó la noticia.
 *
 * <p>{@code idExterno} es el identificador del evento en la plataforma. Es lo que hace idempotente
 * el webhook: el mismo evento reintentado no se guarda dos veces.
 */
public record EventoSeguimiento(
    UUID id,
    EstadoEnvio estado,
    String descripcion,
    Instant ocurrioEn,
    Instant recibidoEn,
    String idExterno) {

  public EventoSeguimiento {
    Objects.requireNonNull(id, "El id del evento no puede ser nulo.");
    Objects.requireNonNull(estado, "El estado del evento no puede ser nulo.");
    Objects.requireNonNull(ocurrioEn, "El momento del evento no puede ser nulo.");
    Objects.requireNonNull(recibidoEn, "El momento de recepción no puede ser nulo.");
    if (idExterno == null || idExterno.isBlank()) {
      throw new ExcepcionDeDominio("El identificador externo del evento no puede estar vacío.");
    }
    descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
  }
}
