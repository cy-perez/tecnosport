package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;

/**
 * Lo que quedó abierto cuando la aplicación se apagó, al volver a arrancar.
 *
 * <p>La cola de ingestas vive en memoria, así que un reinicio se la lleva: los lotes en {@code
 * RECIBIDO} se vuelven a encolar —nadie los tomó, no hay nada a medias—. Encolar uno que también
 * espera en la cola de otra instancia no hace daño: lo procesa la primera que lo toma, y la otra lo
 * encuentra tomado y lo salta.
 *
 * <p>Los que están en manos de un trabajador se cierran con error <b>solo si dejaron de latir</b>
 * ({@link CerrarIngestasAbandonadas}). Con varias instancias, el que late es de una vecina viva y
 * no se toca; hasta el 11 de octubre de 2026 se cerraba igual, y cada arranque mataba la ingesta en
 * curso de otra instancia. El que se quedó a medias en la instancia que se reinició sigue latiendo
 * unos minutos en la base: lo cierra la tarea periódica cuando venza.
 *
 * <p>Corre dentro de una transacción que abre quien llama: escribe los lotes que falla.
 */
public final class ReanudarLotesDeIngesta {

  static final String MOTIVO_COLA_LLENA =
      "La cola de ingestas estaba llena al reanudar. Vuelve a subir la exportación.";

  private final RepositorioLotesIngesta repositorioLotes;
  private final EjecutorDeIngestas ejecutor;
  private final CerrarIngestasAbandonadas cerrarAbandonadas;
  private final Reloj reloj;

  public ReanudarLotesDeIngesta(
      RepositorioLotesIngesta repositorioLotes,
      EjecutorDeIngestas ejecutor,
      CerrarIngestasAbandonadas cerrarAbandonadas,
      Reloj reloj) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.ejecutor = Objects.requireNonNull(ejecutor);
    this.cerrarAbandonadas = Objects.requireNonNull(cerrarAbandonadas);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Resultado ejecutar() {
    int fallidos = cerrarAbandonadas.ejecutar();
    int reencolados = 0;
    for (LoteIngesta leido : repositorioLotes.abiertos()) {
      if (leido.estado() != EstadoLote.RECIBIDO) {
        continue;
      }
      // Se relee bloqueado: otra instancia viva puede estar tomándolo o deteniéndolo en este
      // momento, y escribir sobre la lectura de arriba pisaría lo que haga.
      LoteIngesta lote = repositorioLotes.buscarPorIdParaActualizar(leido.id()).orElse(null);
      if (lote == null || lote.estado() != EstadoLote.RECIBIDO) {
        continue;
      }
      try {
        ejecutor.encolar(lote.id());
        reencolados++;
      } catch (ColaDeIngestasLlenaException e) {
        lote.fallar(MOTIVO_COLA_LLENA, reloj.ahora());
        repositorioLotes.actualizar(lote);
        fallidos++;
      }
    }
    return new Resultado(reencolados, fallidos);
  }

  public record Resultado(int reencolados, int fallidos) {}
}
