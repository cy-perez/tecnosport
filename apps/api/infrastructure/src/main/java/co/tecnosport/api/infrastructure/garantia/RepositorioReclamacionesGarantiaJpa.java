package co.tecnosport.api.infrastructure.garantia;

import co.tecnosport.api.application.garantia.RepositorioReclamacionesGarantia;
import co.tecnosport.api.domain.garantia.DesenlaceGarantia;
import co.tecnosport.api.domain.garantia.EstadoReclamacionGarantia;
import co.tecnosport.api.domain.garantia.ReclamacionGarantia;
import co.tecnosport.api.infrastructure.garantia.entidad.ReclamacionGarantiaJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Sin transacción propia: comparte la de quien lo llama, como el resto de repositorios. */
@Component
public class RepositorioReclamacionesGarantiaJpa implements RepositorioReclamacionesGarantia {

  private final ReclamacionGarantiaJpaRepository repositorio;

  public RepositorioReclamacionesGarantiaJpa(ReclamacionGarantiaJpaRepository repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  @Override
  public void guardar(ReclamacionGarantia reclamacion) {
    repositorio.save(
        new ReclamacionGarantiaJpaEntity(
            reclamacion.id(),
            reclamacion.solicitudId(),
            reclamacion.pedidoId(),
            reclamacion.varianteId(),
            reclamacion.entregadoEn(),
            reclamacion.radicadaEn(),
            reclamacion.mesesDeTermino().orElse(null),
            reclamacion.descripcionDelFallo(),
            reclamacion.estado().name(),
            reclamacion.desenlace().map(DesenlaceGarantia::name).orElse(null),
            reclamacion.resueltaEn().orElse(null),
            reclamacion.resueltaPor().orElse(null),
            reclamacion.reintegroId().orElse(null)));
  }

  @Override
  public Optional<ReclamacionGarantia> buscarPorId(UUID id) {
    return repositorio.findById(id).map(this::aReclamacion);
  }

  @Override
  public Optional<ReclamacionGarantia> buscarPorSolicitudId(UUID solicitudId) {
    return repositorio.findBySolicitudId(solicitudId).map(this::aReclamacion);
  }

  @Override
  public List<ReclamacionGarantia> buscarPorPedidoId(UUID pedidoId) {
    return repositorio.findByPedidoIdOrderByRadicadaEnDesc(pedidoId).stream()
        .map(this::aReclamacion)
        .toList();
  }

  private ReclamacionGarantia aReclamacion(ReclamacionGarantiaJpaEntity entidad) {
    return new ReclamacionGarantia(
        entidad.getId(),
        entidad.getSolicitudId(),
        entidad.getPedidoId(),
        entidad.getVarianteId(),
        entidad.getEntregadoEn(),
        entidad.getRadicadaEn(),
        entidad.getMesesDeTermino(),
        entidad.getDescripcionDelFallo(),
        EstadoReclamacionGarantia.valueOf(entidad.getEstado()),
        entidad.getDesenlace() == null ? null : DesenlaceGarantia.valueOf(entidad.getDesenlace()),
        entidad.getResueltaEn(),
        entidad.getResueltaPor(),
        entidad.getReintegroId());
  }
}
