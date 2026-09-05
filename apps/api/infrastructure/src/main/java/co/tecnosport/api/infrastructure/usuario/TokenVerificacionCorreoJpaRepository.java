package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.infrastructure.usuario.entidad.TokenVerificacionCorreoJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenVerificacionCorreoJpaRepository
    extends JpaRepository<TokenVerificacionCorreoJpaEntity, UUID> {}
