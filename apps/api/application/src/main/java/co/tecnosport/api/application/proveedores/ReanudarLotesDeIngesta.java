package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;

/**
 * Lo que quedó abierto cuando la aplicación se apagó, al volver a arrancar.
 *
 * <p>La cola de ingestas vive en memoria, así que un reinicio se la lleva: los lotes en {@code
 * RECIBIDO} se vuelven a encolar —nadie los tomó, no hay nada a medias— y los que estaban en {@code
 * PROCESANDO} se dan por fallidos con un motivo que dice qué hacer: volver a subir la exportación,
 * que es seguro porque los mensajes que ya se registraron no se repiten. No se reanudan a medias
 * porque no hay forma de saber en qué publicación iban.
 *
 * <p>Corre dentro de una transacción que abre quien llama: escribe los lotes que falla.
 */
public final class ReanudarLotesDeIngesta {

  static final String MOTIVO_REINICIO =
      "La aplicación se reinició mientras se procesaba. Vuelve a subir la exportación: lo que"
          + " ya se leyó no se repite.";
  static final String MOTIVO_COLA_LLENA =
      "La cola de ingestas estaba llena al reanudar. Vuelve a subir la exportación.";

  private final RepositorioLotesIngesta repositorioLotes;
  private final EjecutorDeIngestas ejecutor;
  private final Reloj reloj;

  public ReanudarLotesDeIngesta(
      RepositorioLotesIngesta repositorioLotes, EjecutorDeIngestas ejecutor, Reloj reloj) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.ejecutor = Objects.requireNonNull(ejecutor);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Resultado ejecutar() {
    int reencolados = 0;
    int fallidos = 0;
    for (LoteIngesta lote : repositorioLotes.abiertos()) {
      if (lote.estado() == EstadoLote.PROCESANDO) {
        fallar(lote, MOTIVO_REINICIO);
        fallidos++;
        continue;
      }
      try {
        ejecutor.encolar(lote.id());
        reencolados++;
      } catch (ColaDeIngestasLlenaException e) {
        fallar(lote, MOTIVO_COLA_LLENA);
        fallidos++;
      }
    }
    return new Resultado(reencolados, fallidos);
  }

  private void fallar(LoteIngesta lote, String motivo) {
    lote.fallar(motivo, reloj.ahora());
    repositorioLotes.actualizar(lote);
  }

  public record Resultado(int reencolados, int fallidos) {}
}
