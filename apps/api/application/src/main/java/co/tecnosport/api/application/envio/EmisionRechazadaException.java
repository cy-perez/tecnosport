package co.tecnosport.api.application.envio;

/**
 * La plataforma no creó nada. Es la respuesta barata: un rechazo no cuesta saldo — lo caro es la
 * emisión que se acepta y después muere.
 */
public class EmisionRechazadaException extends RuntimeException {

  private final transient ResultadoEmision.Motivo motivo;

  public EmisionRechazadaException(ResultadoEmision.Motivo motivo, String detalle) {
    super(
        "La plataforma rechazó la emisión ("
            + motivo
            + ")"
            + (detalle == null ? "" : ": " + detalle));
    this.motivo = motivo;
  }

  public ResultadoEmision.Motivo motivo() {
    return motivo;
  }
}
