package co.tecnosport.api.application.proveedores;

/**
 * No cabe otro lote en la cola. La cola es corta a propósito: un lote tarda minutos y el panel lo
 * usa una persona, así que una cola llena no es carga, es alguien pulsando muchas veces.
 */
public final class ColaDeIngestasLlenaException extends RuntimeException {

  public ColaDeIngestasLlenaException() {
    super("Hay demasiadas ingestas en espera. Deja que terminen y vuelve a intentarlo.");
  }
}
