package co.tecnosport.api.infrastructure.retracto;

import co.tecnosport.api.infrastructure.retracto.entidad.SolicitudRetractoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudRetractoJpaRepository
    extends JpaRepository<SolicitudRetractoJpaEntity, UUID> {

  List<SolicitudRetractoJpaEntity> findByPedidoIdOrderByRadicadaEnDesc(UUID pedidoId);
}
