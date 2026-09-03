package co.tecnosport.api.infrastructure.usuario;

import co.tecnosport.api.infrastructure.usuario.entidad.UsuarioJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioJpaRepository extends JpaRepository<UsuarioJpaEntity, UUID> {

  Optional<UsuarioJpaEntity> findByCorreo(String correo);
}
