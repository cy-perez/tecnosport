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

  /**
   * Despachados antes del corte y con <strong>al menos una guía</strong> callada y viva: sin evento
   * recibido después del corte y sin ningún evento terminal.
   *
   * <p>Los dos {@code not exists} hacen cosas distintas y las dos hacen falta. El primero es "lleva
   * callada un rato". El segundo es "su historia ya acabó": sin él, un paquete entregado hace tres
   * meses se consultaría en cada vuelta para siempre, contra un proveedor que admite dos peticiones
   * por segundo.
   *
   * <p><strong>Los dos se preguntan por guía, no por envío</strong> (adr/0031), y esa es la
   * diferencia con la versión anterior: con dos guías, una entregada y otra en tránsito, medir el
   * envío entero lo daría por terminado y dejaría la segunda sin conciliar para siempre. Por eso el
   * {@code exists} de afuera — basta una guía viva y callada para que valga la pena preguntar.
   *
   * <p>Los estados terminales llegan como parámetro desde {@code EstadoEnvio.nombresTerminales()} y
   * no escritos aquí: como literales dentro de la consulta, renombrar una constante del enum
   * compilaría, pasaría las pruebas del dominio, y dejaría este filtro comparando contra un valor
   * que ya no existe.
   *
   * <p>{@code Limit} acota el lote porque cada guía que salga de aquí se convierte en una llamada
   * al proveedor. Un respaldo de mil envíos callados —tras una caída del webhook, por ejemplo— no
   * puede convertirse en mil llamadas seguidas.
   */
  @Query(
      """
      select e from EnvioJpaEntity e
      where e.despachadoEn < :corte
        and exists (
          select 1 from GuiaEnvioJpaEntity g
          where g.envioId = e.id
            and not exists (
              select 1 from EventoSeguimientoJpaEntity r
              where r.guiaId = g.id and r.recibidoEn >= :corte)
            and not exists (
              select 1 from EventoSeguimientoJpaEntity f
              where f.guiaId = g.id and f.estado in :terminales))
      order by e.despachadoEn asc
      """)
  List<EnvioJpaEntity> buscarSinEventosDesde(
      @Param("corte") Instant corte,
      @Param("terminales") Collection<String> terminales,
      Limit limite);

  /**
   * Los envíos con alguna guía cuyo <em>último</em> movimiento la dejó quieta: los candidatos de la
   * bandeja de revisión.
   *
   * <p>"Último" se escribe como "ninguno posterior" y no como un {@code max}, porque un {@code max}
   * dentro de un {@code exists} no dice de qué fila salió. La consecuencia está asumida: si dos
   * eventos de la misma guía comparten instante y solo uno pide revisión, este filtro la trae
   * igual. Trae de más, nunca de menos, y quien decide de verdad es {@code
   * GuiaEnvio.ultimoEstado()} sobre el agregado ya cargado — ahí "último" es uno solo.
   *
   * <p>Los estados llegan por parámetro desde {@code EstadoEnvio.nombresQueExigenRevisionManual()},
   * por lo mismo que en la consulta de arriba: un literal aquí sobrevive al renombre de la
   * constante y deja de encontrar lo que busca sin que falle nada.
   *
   * <p>No filtra por acuse. Podría —sería un {@code not exists} más— y no lo hace a propósito: la
   * regla que devuelve una guía a la bandeja compara dos instantes de dos tablas distintas y es
   * exactamente el tipo de cosa que hay que poder probar sin base de datos. Vive en el caso de uso.
   */
  @Query(
      """
      select e from EnvioJpaEntity e
      where exists (
        select 1 from GuiaEnvioJpaEntity g
        where g.envioId = e.id
          and exists (
            select 1 from EventoSeguimientoJpaEntity ev
            where ev.guiaId = g.id
              and ev.estado in :estados
              and not exists (
                select 1 from EventoSeguimientoJpaEntity posterior
                where posterior.guiaId = g.id and posterior.ocurrioEn > ev.ocurrioEn)))
      order by e.despachadoEn asc
      """)
  List<EnvioJpaEntity> buscarConGuiasEnRevision(
      @Param("estados") Collection<String> estados, Limit limite);
}
