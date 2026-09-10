package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class RepositorioReintegrosDobleDePrueba implements RepositorioReintegros {

  private final List<Reintegro> guardados = new ArrayList<>();

  void limpiar() {
    guardados.clear();
  }

  @Override
  public void guardar(Reintegro reintegro) {
    guardados.removeIf(r -> r.id().equals(reintegro.id()));
    guardados.add(reintegro);
  }

  @Override
  public Optional<Reintegro> buscarPorId(UUID id) {
    return guardados.stream().filter(r -> r.id().equals(id)).findFirst();
  }

  @Override
  public List<Reintegro> buscarPorPedido(UUID pedidoId) {
    return guardados.stream().filter(r -> r.pedidoId().equals(pedidoId)).toList();
  }

  @Override
  public Optional<Reintegro> buscarPorOrigen(UUID origenId) {
    return guardados.stream().filter(r -> r.origenId().equals(origenId)).findFirst();
  }
}
