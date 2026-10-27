package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.infrastructure.usuario.entidad.TokenVerificacionCorreoJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenVerificacionCorreoJpaRepository
    extends JpaRepository<TokenVerificacionCorreoJpaEntity, String> {}
