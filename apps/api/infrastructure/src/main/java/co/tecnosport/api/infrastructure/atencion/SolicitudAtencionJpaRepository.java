package co.tecnosport.api.infrastructure.atencion;

import co.tecnosport.api.infrastructure.atencion.entidad.SolicitudAtencionJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudAtencionJpaRepository
    extends JpaRepository<SolicitudAtencionJpaEntity, UUID> {

  Optional<SolicitudAtencionJpaEntity> findByNumeroRadicado(String numeroRadicado);

  List<SolicitudAtencionJpaEntity> findByPedidoIdOrderByRecibidaEnDesc(UUID pedidoId);

  List<SolicitudAtencionJpaEntity> findByEstadoOrderByRecibidaEnAsc(String estado);

  List<SolicitudAtencionJpaEntity> findByEstadoNotOrderByRecibidaEnAsc(String estado);
}
