package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.proveedores.EsperaDeIngesta;
import co.tecnosport.api.application.proveedores.IngestaInterrumpidaException;
import java.time.Duration;
import java.util.Objects;

/**
 * Duerme el hilo de la ingesta un rato fijo. Si el hilo se interrumpe —{@code
 * EjecutorDeIngestasEnHilo#destroy} llama a {@code shutdownNow} tras esperar 30 segundos—, se
 * restituye la marca y se sale con una excepción que el trabajador escribe en el lote, en vez de
 * dormir otra vez.
 *
 * <p>En Cloud Run casi nunca llega a pasar: el contenedor recibe el SIGKILL antes de esos 30
 * segundos, y el lote pausado lo cierra el arranque siguiente con su motivo de reinicio ({@code
 * ReanudarLotesDeIngesta}). <b>Una pausa no sobrevive a un reinicio</b>, y el panel lo dice.
 */
final class EsperaDeIngestaConPausa implements EsperaDeIngesta {

  private final Duration pausa;

  EsperaDeIngestaConPausa(Duration pausa) {
    this.pausa = Objects.requireNonNull(pausa);
  }

  @Override
  public void esperar() {
    try {
      Thread.sleep(pausa);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IngestaInterrumpidaException();
    }
  }
}
