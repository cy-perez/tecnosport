package co.tecnosport.api.application.reversion;

import co.tecnosport.api.domain.reversion.SolicitudReversion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioSolicitudesReversionFalso implements RepositorioSolicitudesReversion {

  private final List<SolicitudReversion> guardadas = new ArrayList<>();

  List<SolicitudReversion> guardadas() {
    return List.copyOf(guardadas);
  }

  @Override
  public void guardar(SolicitudReversion solicitud) {
    guardadas.removeIf(s -> s.id().equals(solicitud.id()));
    guardadas.add(solicitud);
  }

  @Override
  public Optional<SolicitudReversion> buscarPorId(UUID id) {
    return guardadas.stream().filter(s -> s.id().equals(id)).findFirst();
  }

  @Override
  public Optional<SolicitudReversion> buscarPorSolicitudId(UUID solicitudId) {
    return guardadas.stream().filter(s -> s.solicitudId().equals(solicitudId)).findFirst();
  }

  @Override
  public List<SolicitudReversion> buscarPorPedidoId(UUID pedidoId) {
    return guardadas.stream().filter(s -> s.pedidoId().equals(pedidoId)).toList();
  }
}
