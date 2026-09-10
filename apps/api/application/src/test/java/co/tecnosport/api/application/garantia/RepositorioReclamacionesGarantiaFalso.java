package co.tecnosport.api.application.garantia;

import co.tecnosport.api.domain.garantia.ReclamacionGarantia;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioReclamacionesGarantiaFalso implements RepositorioReclamacionesGarantia {

  private final List<ReclamacionGarantia> guardadas = new ArrayList<>();

  List<ReclamacionGarantia> guardadas() {
    return List.copyOf(guardadas);
  }

  @Override
  public void guardar(ReclamacionGarantia reclamacion) {
    guardadas.removeIf(r -> r.id().equals(reclamacion.id()));
    guardadas.add(reclamacion);
  }

  @Override
  public Optional<ReclamacionGarantia> buscarPorId(UUID id) {
    return guardadas.stream().filter(r -> r.id().equals(id)).findFirst();
  }

  @Override
  public Optional<ReclamacionGarantia> buscarPorSolicitudId(UUID solicitudId) {
    return guardadas.stream().filter(r -> r.solicitudId().equals(solicitudId)).findFirst();
  }

  @Override
  public List<ReclamacionGarantia> buscarPorPedidoId(UUID pedidoId) {
    return guardadas.stream().filter(r -> r.pedidoId().equals(pedidoId)).toList();
  }
}
