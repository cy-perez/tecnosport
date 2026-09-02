package co.tecnosport.api.infrastructure.carrito;

import co.tecnosport.api.infrastructure.carrito.entidad.CarritoJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarritoJpaRepository extends JpaRepository<CarritoJpaEntity, UUID> {}
