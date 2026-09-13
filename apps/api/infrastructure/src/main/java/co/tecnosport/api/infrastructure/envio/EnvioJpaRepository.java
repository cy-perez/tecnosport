package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.infrastructure.envio.entidad.EnvioJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
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
   * meses se consultaría en cada vuelta para siempre, contra un proveedor que admite dos peticiones
   * por segundo.
   *
   * <p>Los estados terminales llegan como parámetro desde {@code EstadoEnvio.nombresTerminales()} y
   * no escritos aquí: como literales dentro de la consulta, renombrar una constante del enum
   * compilaría, pasaría las pruebas del dominio, y dejaría este filtro comparando contra un valor
   * que ya no existe.
   *
   * <p>{@code Limit} acota el lote porque cada fila que salga de aquí se convierte en una llamada
   * al proveedor. Un respaldo de mil envíos callados —tras una caída del webhook, por ejemplo— no
   * puede convertirse en mil llamadas seguidas.
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
          where f.envioId = e.id and f.estado in :terminales)
      order by e.despachadoEn asc
      """)
  List<EnvioJpaEntity> buscarSinEventosDesde(
      @Param("corte") Instant corte,
      @Param("terminales") Collection<String> terminales,
      Limit limite);
}
