package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.infrastructure.usuario.entidad.CodigoDeRespaldoJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodigoDeRespaldoJpaRepository
    extends JpaRepository<CodigoDeRespaldoJpaEntity, String> {

  List<CodigoDeRespaldoJpaEntity> findByUsuarioId(UUID usuarioId);
}
