package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.EnvioJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnvioJpaRepository extends JpaRepository<EnvioJpaEntity, UUID> {

  Optional<EnvioJpaEntity> findByPedidoId(UUID pedidoId);

  Optional<EnvioJpaEntity> findByGuia(String guia);

  /**
   * Despachados antes del corte, sin evento recibido después de él y sin ningún evento terminal.
   *
   * <p>Los dos {@code not exists} hacen cosas distintas y las dos hacen falta. El primero es "lleva
   * callado un rato". El segundo es "su historia ya acabó": sin él, un paquete entregado hace tres
   * meses se consultaría en cada corrida para siempre, contra un proveedor que admite dos
   * peticiones por segundo.
   */
  @Query(
      """
      select e from EnvioJpaEntity e
      where e.despachadoEn < :corte
        and not exists (
          select 1 from EventoSeguimientoJpaEntity r
          where r.envioId = e.id and r.recibidoEn >= :corte)
        and not exists (
          select 1 from EventoSeguimientoJpaEntity f
          where f.envioId = e.id
            and f.estado in ('ENTREGADO', 'EN_DEVOLUCION', 'CANCELADO', 'DESTRUIDO'))
      """)
  List<EnvioJpaEntity> buscarSinEventosDesde(@Param("corte") Instant corte);
}
