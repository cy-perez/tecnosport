package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.domain.envio.Envio;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class RepositorioEnviosDobleDePrueba implements RepositorioEnvios {

  private final List<Envio> envios = new ArrayList<>();

  @Override
  public void guardar(Envio envio) {
    envios.removeIf(e -> e.id().equals(envio.id()));
    envios.add(envio);
  }

  @Override
  public Optional<Envio> buscarPorPedidoId(UUID pedidoId) {
    return envios.stream().filter(e -> e.pedidoId().equals(pedidoId)).findFirst();
  }

  List<Envio> guardados() {
    return List.copyOf(envios);
  }
}
