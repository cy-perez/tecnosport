package co.tecnosport.api.application.envio;

/**
 * La transportadora que eligió el comprador ya no cotiza este envío: entre la pantalla donde la
 * eligió y la confirmación, el proveedor dejó de ofrecerla —o nunca la ofreció, si el nombre lo
 * escribió un cliente a mano—. No se cambia por otra en silencio, porque el comprador eligió una;
 * se le dice y vuelve a elegir (ADR-0073).
 */
public final class TransportadoraNoDisponibleException extends RuntimeException {

  private final String transportadora;

  public TransportadoraNoDisponibleException(String transportadora) {
    super("La transportadora '" + transportadora + "' ya no cotiza este envío.");
    this.transportadora = transportadora;
  }

  public String transportadora() {
    return transportadora;
  }
}
