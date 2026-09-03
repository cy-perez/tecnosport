package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioPedidos {

  Optional<Pedido> buscarPorId(UUID id);

  void guardar(Pedido pedido);
}
