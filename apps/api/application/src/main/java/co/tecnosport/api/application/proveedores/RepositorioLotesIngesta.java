package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Los lotes de ingesta, de cualquier origen. */
public interface RepositorioLotesIngesta {

  void guardar(LoteIngesta lote);

  void actualizar(LoteIngesta lote);

  Optional<LoteIngesta> buscarPorId(UUID id);

  /**
   * Del más reciente al más antiguo, paginado por página como todo el panel. {@code proveedorId}
   * nulo lista los de todos los proveedores.
   */
  LotesPaginados listar(UUID proveedorId, int pagina, int tamanoPagina);

  /** Los que están en la cola o a medio procesar, del más antiguo al más reciente. */
  List<LoteIngesta> abiertos();

  /** Lo que cuelga del lote: ver {@link EliminarLoteDeIngesta}. */
  DependenciasDeLote dependenciasDe(UUID loteId);

  /**
   * Borra el lote con sus borradores, publicaciones y mensajes. Los productos ya se resolvieron
   * antes: los que quedan pierden el vínculo con su borrador por el {@code on delete set null}.
   */
  void eliminarConSuHistorial(UUID loteId);
}
