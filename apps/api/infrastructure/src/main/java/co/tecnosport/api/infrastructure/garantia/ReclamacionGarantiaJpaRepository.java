package co.tecnosport.api.infrastructure.garantia;

import co.tecnosport.api.infrastructure.garantia.entidad.ReclamacionGarantiaJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReclamacionGarantiaJpaRepository
    extends JpaRepository<ReclamacionGarantiaJpaEntity, UUID> {

  Optional<ReclamacionGarantiaJpaEntity> findBySolicitudId(UUID solicitudId);

  List<ReclamacionGarantiaJpaEntity> findByPedidoIdOrderByRadicadaEnDesc(UUID pedidoId);
}
