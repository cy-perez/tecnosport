package co.tecnosport.api.infrastructure.pedido;

import co.tecnosport.api.infrastructure.pedido.entidad.PedidoJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoJpaRepository extends JpaRepository<PedidoJpaEntity, UUID> {

  boolean existsByCorreoAndEstado(String correo, String estado);
}
