package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.RepositorioAcusesDeRevision;
import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.domain.envio.TipoDeRevision;
import co.tecnosport.api.infrastructure.envio.entidad.AcuseDeRevisionJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RepositorioAcusesJpa implements RepositorioAcusesDeRevision {

  private final AcuseDeRevisionJpaRepository acuses;

  public RepositorioAcusesJpa(AcuseDeRevisionJpaRepository acuses) {
    this.acuses = Objects.requireNonNull(acuses);
  }

  /**
   * Inserta, nunca actualiza. La tabla es append-only igual que {@code evento_seguimiento}: acusar
   * otra vez la misma guía son dos filas, porque el identificador lo genera el dominio en cada
   * acuse y ninguno pisa al anterior.
   */
  @Override
  public void guardar(AcuseDeRevision acuse) {
    acuses.save(
        new AcuseDeRevisionJpaEntity(
            acuse.id(),
            acuse.tipo().name(),
            acuse.tipo() == TipoDeRevision.GUIA ? acuse.referencia() : null,
            acuse.tipo() == TipoDeRevision.EMISION ? acuse.referencia() : null,
            acuse.revisadoEn(),
            acuse.actor(),
            acuse.nota().orElse(null)));
  }

  /**
   * El acuse más reciente de cada referencia, resuelto en memoria sobre lo que devolvió una sola
   * consulta. Un {@code group by} en la base daría lo mismo con más partes móviles: lo que se
   * agrupa son las pocas filas de una pantalla, no una tabla entera.
   */
  @Override
  public Map<UUID, Instant> ultimaRevisionDe(TipoDeRevision tipo, Collection<UUID> referencias) {
    if (referencias.isEmpty()) {
      return Map.of();
    }
    List<AcuseDeRevisionJpaEntity> filas =
        tipo == TipoDeRevision.GUIA
            ? acuses.findByGuiaIdIn(referencias)
            : acuses.findByEmisionIdIn(referencias);

    Function<AcuseDeRevisionJpaEntity, UUID> referencia =
        tipo == TipoDeRevision.GUIA
            ? AcuseDeRevisionJpaEntity::getGuiaId
            : AcuseDeRevisionJpaEntity::getEmisionId;

    return filas.stream()
        .collect(
            Collectors.toMap(
                referencia,
                AcuseDeRevisionJpaEntity::getRevisadoEn,
                (uno, otro) -> uno.isAfter(otro) ? uno : otro));
  }
}
