package co.tecnosport.api.application.pago;

import java.util.Optional;

/**
 * Lo que Sistecrédito cuenta de una transacción, tanto al crearla como al consultarla: las dos
 * respuestas comparten estructura (guía {@code G-SCL-21} §3.3), y la notificación al comercio trae
 * la misma, así que un solo tipo sirve para los tres caminos.
 *
 * <p>{@code estado} es el {@code transactionStatus} crudo, tal como lo nombra la pasarela. Crudo a
 * propósito, igual que {@code Pago.medioReportadoPorLaPasarela}: traducirlo aquí perdería los
 * estados que todavía no sabemos interpretar, y de esos es justo de los que uno quiere enterarse.
 *
 * <p>{@code urlRedireccion} está vacía casi siempre en la respuesta de creación y aparece durante
 * el sondeo — es la señal de que la pasarela ya habló con el medio de pago. Es de un solo uso y la
 * transacción vive unos 15 minutos (guía {@code G-ALI-12}).
 *
 * <p>{@code codigoMedioDePago} y {@code descripcion} son lo que explica un rechazo: el {@code 802}
 * ("el valor del crédito solicitado es menor al mínimo") llega por ahí, con HTTP 200 y sin error de
 * pasarela, que es justo el caso que se pierde si uno solo mira el código HTTP.
 */
public record TransaccionSistecredito(
    String id,
    String referencia,
    String estado,
    Long valor,
    String urlRedireccion,
    String codigoMedioDePago,
    String descripcion) {

  public TransaccionSistecredito {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("El id de la transacción no puede estar vacío.");
    }
    if (estado == null || estado.isBlank()) {
      throw new IllegalArgumentException("El estado de la transacción no puede estar vacío.");
    }
  }

  /**
   * Si ya hay a dónde mandar al comprador. Mientras esté vacía, hay que seguir sondeando.
   *
   * <p>Se llama distinto del componente {@link #urlRedireccion()} porque un accesor de {@code
   * record} no puede cambiarle el tipo a su componente.
   */
  public Optional<String> urlDeRedireccion() {
    return Optional.ofNullable(urlRedireccion).filter(url -> !url.isBlank());
  }

  public boolean tieneUrlDeRedireccion() {
    return urlDeRedireccion().isPresent();
  }
}
