package co.tecnosport.api.application.garantia;

import co.tecnosport.api.domain.garantia.ReclamacionGarantia;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioReclamacionesGarantia {

  void guardar(ReclamacionGarantia reclamacion);

  Optional<ReclamacionGarantia> buscarPorId(UUID id);

  /** Por la solicitud de atención que la contiene: es como la encuentra el panel. */
  Optional<ReclamacionGarantia> buscarPorSolicitudId(UUID solicitudId);

  List<ReclamacionGarantia> buscarPorPedidoId(UUID pedidoId);
}
