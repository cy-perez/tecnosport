package co.tecnosport.api.application.retracto;

import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble escrito a mano, sin Mockito (docs/06-testing.md). */
final class RepositorioSolicitudesRetractoFalso implements RepositorioSolicitudesRetracto {

  private final Map<UUID, SolicitudRetracto> solicitudes = new LinkedHashMap<>();

  @Override
  public Optional<SolicitudRetracto> buscarPorId(UUID id) {
    return Optional.ofNullable(solicitudes.get(id));
  }

  @Override
  public List<SolicitudRetracto> buscarPorPedidoId(UUID pedidoId) {
    List<SolicitudRetracto> encontradas = new ArrayList<>();
    for (SolicitudRetracto solicitud : solicitudes.values()) {
      if (solicitud.pedidoId().equals(pedidoId)) {
        encontradas.add(solicitud);
      }
    }
    return encontradas;
  }

  @Override
  public void guardar(SolicitudRetracto solicitud) {
    solicitudes.put(solicitud.id(), solicitud);
  }

  List<SolicitudRetracto> todas() {
    return List.copyOf(solicitudes.values());
  }
}
