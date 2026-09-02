package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteAtributoValorJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VarianteAtributoValorJpaRepository
    extends JpaRepository<VarianteAtributoValorJpaEntity, UUID> {

  List<VarianteAtributoValorJpaEntity> findByVarianteIdIn(Collection<UUID> varianteIds);
}
