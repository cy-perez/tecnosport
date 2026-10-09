package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteAtributoValorJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VarianteAtributoValorJpaRepository
    extends JpaRepository<VarianteAtributoValorJpaEntity, UUID> {

  List<VarianteAtributoValorJpaEntity> findByVarianteIdIn(Collection<UUID> varianteIds);

  /** Una variante tiene a lo sumo un valor por atributo: lo garantiza {@code Variante}. */
  Optional<VarianteAtributoValorJpaEntity> findByVarianteIdAndAtributoId(
      UUID varianteId, UUID atributoId);
}
