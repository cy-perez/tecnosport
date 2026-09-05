package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.infrastructure.usuario.entidad.TokenRecuperacionClaveJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRecuperacionClaveJpaRepository
    extends JpaRepository<TokenRecuperacionClaveJpaEntity, UUID> {}
