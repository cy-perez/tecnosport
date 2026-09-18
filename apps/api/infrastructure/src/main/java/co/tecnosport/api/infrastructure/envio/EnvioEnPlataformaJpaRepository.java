package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.EnvioEnPlataformaJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvioEnPlataformaJpaRepository
    extends JpaRepository<EnvioEnPlataformaJpaEntity, EnvioEnPlataformaJpaEntity.Llave> {

  List<EnvioEnPlataformaJpaEntity> findByEmisionIdOrderByPosicionAsc(UUID emisionId);

  /**
   * Uno solo, y quien lo garantiza es la base: {@code uq_envio_en_plataforma_externo} es único
   * (V43__emision_de_guia.sql). Por eso devuelve {@code Optional} y no una lista, y por eso no
   * necesita ni orden ni límite.
   */
  Optional<EnvioEnPlataformaJpaEntity> findByIdExterno(String idExterno);
}
