package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.LoteIngesta;
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
}
