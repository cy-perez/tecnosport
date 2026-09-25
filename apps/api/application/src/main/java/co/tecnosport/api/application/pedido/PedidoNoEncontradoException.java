package co.tecnosport.api.application.pedido;

import java.util.UUID;

public final class PedidoNoEncontradoException extends RuntimeException {

  public PedidoNoEncontradoException(UUID pedidoId) {
    super("No existe un pedido con id " + pedidoId + ".");
  }

  private PedidoNoEncontradoException(String mensaje) {
    super(mensaje);
  }

  /**
   * La que lanza el seguimiento público, con un mensaje que <b>no dice cuál de las dos cosas
   * falló</b>.
   *
   * <p>El {@code detail} de la respuesta publica {@code getMessage()} (apps/api/CLAUDE.md), así que
   * este texto sale al cliente. Por eso es uno solo para los tres caminos —número mal escrito,
   * número que no existe, correo que no coincide—: cualquier diferencia entre ellos le diría a
   * quien prueba a ciegas cuándo acertó la mitad.
   */
  public static PedidoNoEncontradoException porSeguimiento() {
    return new PedidoNoEncontradoException("No encontramos un pedido con ese número y ese correo.");
  }
}
