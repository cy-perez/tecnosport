package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.infrastructure.catalogo.entidad.ColorPaletaJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ColorPaletaJpaRepository extends JpaRepository<ColorPaletaJpaEntity, UUID> {

  List<ColorPaletaJpaEntity> findAllByOrderByOrdenAsc();
}
