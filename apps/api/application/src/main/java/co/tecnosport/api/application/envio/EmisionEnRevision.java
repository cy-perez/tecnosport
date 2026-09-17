package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.EstadoEmision;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Una emisión con plata comprometida que nadie ha desenredado: {@link EstadoEmision#INDETERMINADA}
 * —puede haberse cobrado y no tenemos identificador— o {@link EstadoEmision#PARCIAL} —se cobró, y
 * hay guías vivas sin usar—.
 *
 * <p>Trae el {@code idTarifa} porque es lo único con lo que se puede hacer algo: es la llave con la
 * que el panel de Skydropx encuentra el envío y la que, dentro de 96 horas, lo recupera por
 * idempotencia. Una bandeja que dijera "hubo un problema" sin ese dato mandaría a quien atiende a
 * buscar a mano en la base.
 */
public record EmisionEnRevision(
    UUID emisionId,
    UUID pedidoId,
    String numeroPedido,
    String transportadora,
    String idTarifa,
    EstadoEmision estado,
    String detalle,
    List<String> enviosEnPlataforma,
    Instant solicitadaEn,
    String actor) {

  public EmisionEnRevision {
    enviosEnPlataforma = List.copyOf(enviosEnPlataforma);
  }
}
