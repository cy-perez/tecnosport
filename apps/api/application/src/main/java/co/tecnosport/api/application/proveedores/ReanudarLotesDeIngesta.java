package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que quedó abierto cuando la aplicación se apagó, al volver a arrancar.
 *
 * <p>Primero recupera lo abandonado ({@link RecuperarIngestasAbandonadas}): un lote cuyo trabajador
 * dejó de latir vuelve a la cola para que otro tramo lo retome. Uno con el latido al día es de una
 * instancia viva —con varias instancias, la que arranca no es la única— y no se toca.
 *
 * <p>Después, si la cola vive en memoria y el reinicio se la llevó, devuelve a ella lo que estaba
 * en {@code RECIBIDO}. Con Cloud Tasks no: lo encolado sigue en la cola de afuera, y volver a
 * encolarlo en cada arranque —en dev, muchos al día— solo duplicaría tareas.
 *
 * <p>No encola: devuelve lo que hay que encolar, para que quien llama lo haga después de confirmar
 * la transacción que abre, en la que esto escribe.
 */
public final class ReanudarLotesDeIngesta {

  private final RepositorioLotesIngesta repositorioLotes;
  private final RecuperarIngestasAbandonadas recuperar;
  private final boolean colaEnMemoria;

  public ReanudarLotesDeIngesta(
      RepositorioLotesIngesta repositorioLotes,
      RecuperarIngestasAbandonadas recuperar,
      EjecutorDeIngestas ejecutor) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.recuperar = Objects.requireNonNull(recuperar);
    this.colaEnMemoria = !ejecutor.sobreviveAUnReinicio();
  }

  public Resultado ejecutar() {
    RecuperarIngestasAbandonadas.Resultado recuperados = recuperar.ejecutar();
    Set<UUID> porEncolar = new LinkedHashSet<>(recuperados.porEncolar());
    if (colaEnMemoria) {
      for (LoteIngesta lote : repositorioLotes.abiertos()) {
        if (lote.estado() == EstadoLote.RECIBIDO) {
          porEncolar.add(lote.id());
        }
      }
    }
    return new Resultado(List.copyOf(porEncolar), recuperados.retomados(), recuperados.cerrados());
  }

  /**
   * @param porEncolar en el orden en que se subieron, para que la cola en memoria los tome así
   */
  public record Resultado(List<UUID> porEncolar, int retomados, int cerrados) {}
}
