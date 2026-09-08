package co.tecnosport.api.infrastructure.legal;

import co.tecnosport.api.infrastructure.legal.entidad.AutorizacionDatosJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutorizacionDatosJpaRepository
    extends JpaRepository<AutorizacionDatosJpaEntity, UUID> {

  List<AutorizacionDatosJpaEntity> findByCorreoOrderByOtorgadaEnDesc(String correo);
}
