package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.EstadoEnvio;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Una guía que se quedó quieta, con lo que hace falta para decidir qué hacer con ella.
 *
 * <p>Trae el número del pedido y no solo su identificador: quien atiende va a buscarlo en la lista
 * de pedidos y en el correo del comprador, y ahí el pedido se llama {@code TS-2026-000123}.
 *
 * <p>{@code recibidoEn} no es decorativo: es el instante contra el que se compara el acuse, y por
 * eso viaja hasta la pantalla. Quien mira la bandeja tiene que poder distinguir un evento de hace
 * dos meses que nadie atendió de uno que acaba de llegar.
 */
public record GuiaEnRevision(
    UUID guiaId,
    String numeroGuia,
    String transportadora,
    UUID pedidoId,
    String numeroPedido,
    EstadoEnvio estado,
    String descripcion,
    Instant ocurrioEn,
    Instant recibidoEn,
    Instant revisadaEn) {

  /**
   * Cuándo se miró por última vez, si se miró alguna vez y le llegó un evento después. Una guía sin
   * acuse nunca la ha visto nadie; una con acuse anterior a su último evento ya volvió a la
   * bandeja, y saber que alguien la había mirado antes cambia lo que hay que hacer con ella.
   */
  public Optional<Instant> revisadaAntesEn() {
    return Optional.ofNullable(revisadaEn);
  }
}
