package co.tecnosport.api.infrastructure.pedido;

import co.tecnosport.api.infrastructure.pedido.entidad.HistorialPedidoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistorialPedidoJpaRepository
    extends JpaRepository<HistorialPedidoJpaEntity, UUID> {

  List<HistorialPedidoJpaEntity> findByPedidoIdOrderByFechaAsc(UUID pedidoId);

  void deleteByPedidoId(UUID pedidoId);
}
