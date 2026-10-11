package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.time.Duration;
import java.util.Objects;

/**
 * Cierra con error los lotes cuyo trabajador dejó de latir: su instancia se apagó o se reinició con
 * el lote en las manos, y nadie lo va a soltar.
 *
 * <p>Solo los abandonados ({@link LoteIngesta#abandonado}). Uno con el latido al día es de una
 * instancia viva, aunque no sea la que pregunta, y se deja en paz: cerrarlo era lo que pasaba antes
 * del 11 de octubre de 2026, cuando cada instancia que arrancaba cerraba lo que su vecina estaba
 * leyendo. Lo que iba a medias no se reanuda, porque no hay forma de saber en qué publicación iba;
 * se vuelve a subir la exportación.
 *
 * <p>Corre al arrancar, dentro de {@link ReanudarLotesDeIngesta}, y cada pocos minutos después: un
 * lote que su instancia soltó al apagarse —un despliegue, la escala a cero— no puede esperar al
 * arranque siguiente, que en producción puede tardar días. Corre dentro de una transacción que abre
 * quien llama: escribe los lotes que cierra.
 */
public final class CerrarIngestasAbandonadas {

  static final String MOTIVO_ABANDONADO =
      "La aplicación se reinició mientras se procesaba o estaba en pausa. Para procesarla entera,"
          + " elimina la ingesta y vuelve a subir la exportación: subirla sin eliminarla no vuelve"
          + " a leer lo que ya se registró.";

  private final RepositorioLotesIngesta repositorioLotes;
  private final Reloj reloj;
  private final Duration tolerancia;

  /**
   * @param tolerancia cuánto puede pasar sin latir antes de darlo por abandonado; varias veces el
   *     intervalo del latido, para que una pausa del recolector o una escritura lenta no lo maten
   */
  public CerrarIngestasAbandonadas(
      RepositorioLotesIngesta repositorioLotes, Reloj reloj, Duration tolerancia) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.reloj = Objects.requireNonNull(reloj);
    this.tolerancia = Objects.requireNonNull(tolerancia);
    if (tolerancia.isNegative() || tolerancia.isZero()) {
      throw new IllegalArgumentException("La tolerancia del latido tiene que ser positiva.");
    }
  }

  /**
   * @return cuántos cerró
   */
  public int ejecutar() {
    int cerrados = 0;
    for (LoteIngesta leido : repositorioLotes.abiertos()) {
      if (!leido.enManosDelTrabajador()) {
        continue;
      }
      // Se relee bloqueado: su trabajador puede estar soltándolo, o latiendo, en este momento.
      LoteIngesta lote = repositorioLotes.buscarPorIdParaActualizar(leido.id()).orElse(null);
      if (lote == null || !lote.abandonado(reloj.ahora(), tolerancia)) {
        continue;
      }
      lote.fallar(MOTIVO_ABANDONADO, reloj.ahora());
      repositorioLotes.actualizar(lote);
      cerrados++;
    }
    return cerrados;
  }
}
