package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.Envio;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioEnvios {

  void guardar(Envio envio);

  Optional<Envio> buscarPorPedidoId(UUID pedidoId);
}
