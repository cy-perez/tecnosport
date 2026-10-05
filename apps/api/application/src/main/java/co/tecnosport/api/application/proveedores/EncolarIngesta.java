package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;

/**
 * Pone un lote ya recibido en la cola de ingestas y, si la cola está llena, lo cierra con su
 * motivo.
 *
 * <p>La compensación la hacía el controlador del panel: mutaba el lote y lo guardaba él mismo. Si
 * mañana otra entrada encola —el webhook de WhatsApp del {@code ADR-0067}—, habría tenido que
 * acordarse de repetirla, y si no, el lote se quedaba en {@code RECIBIDO} para siempre: la cola
 * vive en memoria y nadie más lo iba a tomar.
 *
 * <p>Usa {@link EnTransaccionPropia} porque no hay otra forma de escribir desde aquí sin Spring: el
 * lote ya está confirmado y el intento de encolar ocurrió fuera de cualquier transacción.
 */
public final class EncolarIngesta {

  private final EjecutorDeIngestas ejecutor;
  private final RepositorioLotesIngesta repositorioLotes;
  private final EnTransaccionPropia enTransaccionPropia;
  private final Reloj reloj;

  public EncolarIngesta(
      EjecutorDeIngestas ejecutor,
      RepositorioLotesIngesta repositorioLotes,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj) {
    this.ejecutor = Objects.requireNonNull(ejecutor);
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public void ejecutar(LoteIngesta lote) {
    Objects.requireNonNull(lote, "El lote no puede ser nulo.");
    try {
      ejecutor.encolar(lote.id());
    } catch (ColaDeIngestasLlenaException e) {
      enTransaccionPropia.ejecutar(
          () -> {
            lote.fallar(
                "La cola de ingestas estaba llena. Vuelve a subir la exportación.", reloj.ahora());
            repositorioLotes.actualizar(lote);
            return lote;
          });
      throw e;
    }
  }
}
