package co.tecnosport.api.presentation.envio.dto;

import java.time.Instant;
import java.util.List;

/**
 * Lo que pide ojo humano, en dos listas separadas porque se atienden distinto: una guía retenida se
 * le pregunta a la transportadora; una emisión indeterminada se mira en el panel de Skydropx con el
 * identificador de tarifa.
 */
public record BandejaDeRevisionRespuesta(
    List<GuiaEnRevisionRespuesta> guias, List<EmisionEnRevisionRespuesta> emisiones) {

  /**
   * Una guía que dejó de moverse.
   *
   * <p>{@code recibidoEn} no es decorativo y por eso viaja: es el instante contra el que el
   * servidor compara el acuse, y lo que permite distinguir un evento de hace dos meses que nadie
   * atendió de uno que acaba de llegar. {@code revisadaEn} viene con valor solo cuando alguien ya
   * había mirado esta guía y volvió a moverse después.
   */
  public record GuiaEnRevisionRespuesta(
      String guiaId,
      String numeroGuia,
      String transportadora,
      String pedidoId,
      String numeroPedido,
      String estado,
      String descripcion,
      Instant ocurrioEn,
      Instant recibidoEn,
      Instant revisadaEn) {}

  /**
   * Una emisión con saldo comprometido que nadie ha desenredado.
   *
   * <p>{@code idTarifa} viaja porque es lo único con lo que se puede hacer algo: es la llave con la
   * que el panel de la plataforma encuentra el envío, y la que lo recupera por idempotencia dentro
   * de las 96 horas.
   */
  public record EmisionEnRevisionRespuesta(
      String emisionId,
      String pedidoId,
      String numeroPedido,
      String transportadora,
      String idTarifa,
      String estado,
      String detalle,
      List<String> enviosEnPlataforma,
      Instant solicitadaEn,
      String actor) {}
}
