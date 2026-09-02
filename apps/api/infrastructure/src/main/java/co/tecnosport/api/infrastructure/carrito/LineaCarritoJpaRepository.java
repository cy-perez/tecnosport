package co.tecnosport.api.infrastructure.carrito;

import co.tecnosport.api.infrastructure.carrito.entidad.LineaCarritoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineaCarritoJpaRepository extends JpaRepository<LineaCarritoJpaEntity, UUID> {

  List<LineaCarritoJpaEntity> findByCarritoId(UUID carritoId);

  void deleteByCarritoId(UUID carritoId);
}
