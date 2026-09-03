package co.tecnosport.api.infrastructure.pedido;

import co.tecnosport.api.infrastructure.pedido.entidad.LineaPedidoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineaPedidoJpaRepository extends JpaRepository<LineaPedidoJpaEntity, UUID> {

  List<LineaPedidoJpaEntity> findByPedidoId(UUID pedidoId);

  void deleteByPedidoId(UUID pedidoId);
}
