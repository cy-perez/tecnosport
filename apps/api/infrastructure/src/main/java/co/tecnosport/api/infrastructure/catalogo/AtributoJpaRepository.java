package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.AtributoJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AtributoJpaRepository extends JpaRepository<AtributoJpaEntity, UUID> {

  /** Por nombre y sin mayúsculas: el sembrador reutiliza lo que una migración ya dejó. */
  Optional<AtributoJpaEntity> findFirstByNombreIgnoreCase(String nombre);
}
