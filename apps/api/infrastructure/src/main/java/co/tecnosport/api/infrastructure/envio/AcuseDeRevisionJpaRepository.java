package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.AcuseDeRevisionJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcuseDeRevisionJpaRepository
    extends JpaRepository<AcuseDeRevisionJpaEntity, UUID> {

  /**
   * Todos los acuses de esas guías. Se piden de golpe y el más reciente de cada una lo escoge quien
   * llama: la alternativa —una consulta por guía— convierte una pantalla en tantas idas a la base
   * como guías quietas haya.
   */
  List<AcuseDeRevisionJpaEntity> findByGuiaIdIn(Collection<UUID> guiaIds);

  List<AcuseDeRevisionJpaEntity> findByEmisionIdIn(Collection<UUID> emisionIds);
}
