package co.tecnosport.api.application.reversion;

import co.tecnosport.api.domain.reversion.SolicitudReversion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioSolicitudesReversion {

  void guardar(SolicitudReversion solicitud);

  Optional<SolicitudReversion> buscarPorId(UUID id);

  Optional<SolicitudReversion> buscarPorSolicitudId(UUID solicitudId);

  List<SolicitudReversion> buscarPorPedidoId(UUID pedidoId);
}
