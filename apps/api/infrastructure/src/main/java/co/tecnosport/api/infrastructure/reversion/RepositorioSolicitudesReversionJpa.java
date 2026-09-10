package co.tecnosport.api.infrastructure.reversion;

import co.tecnosport.api.application.reversion.RepositorioSolicitudesReversion;
import co.tecnosport.api.domain.compartido.VerdictoPlazo;
import co.tecnosport.api.domain.reversion.CausalReversion;
import co.tecnosport.api.domain.reversion.DesenlaceReversion;
import co.tecnosport.api.domain.reversion.EstadoSolicitudReversion;
import co.tecnosport.api.domain.reversion.SolicitudReversion;
import co.tecnosport.api.infrastructure.reversion.entidad.SolicitudReversionJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Sin transacción propia: comparte la de quien lo llama, como el resto de repositorios. */
@Component
public class RepositorioSolicitudesReversionJpa implements RepositorioSolicitudesReversion {

  private final SolicitudReversionJpaRepository repositorio;

  public RepositorioSolicitudesReversionJpa(SolicitudReversionJpaRepository repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  @Override
  public void guardar(SolicitudReversion solicitud) {
    repositorio.save(
        new SolicitudReversionJpaEntity(
            solicitud.id(),
            solicitud.solicitudId(),
            solicitud.pedidoId(),
            solicitud.causal().name(),
            solicitud.fechaDelHecho(),
            solicitud.radicadaEn(),
            solicitud.verdictoAlRadicar().name(),
            solicitud.estado().name(),
            solicitud.gestionadaEn().orElse(null),
            solicitud.gestionadaPor().orElse(null),
            solicitud.gestion().orElse(null),
            solicitud.desenlace().map(DesenlaceReversion::name).orElse(null),
            solicitud.resueltaEn().orElse(null),
            solicitud.reintegroId().orElse(null)));
  }

  @Override
  public Optional<SolicitudReversion> buscarPorId(UUID id) {
    return repositorio.findById(id).map(this::aSolicitud);
  }

  @Override
  public Optional<SolicitudReversion> buscarPorSolicitudId(UUID solicitudId) {
    return repositorio.findBySolicitudId(solicitudId).map(this::aSolicitud);
  }

  @Override
  public List<SolicitudReversion> buscarPorPedidoId(UUID pedidoId) {
    return repositorio.findByPedidoIdOrderByRadicadaEnDesc(pedidoId).stream()
        .map(this::aSolicitud)
        .toList();
  }

  private SolicitudReversion aSolicitud(SolicitudReversionJpaEntity entidad) {
    return new SolicitudReversion(
        entidad.getId(),
        entidad.getSolicitudId(),
        entidad.getPedidoId(),
        CausalReversion.valueOf(entidad.getCausal()),
        entidad.getFechaDelHecho(),
        entidad.getRadicadaEn(),
        VerdictoPlazo.valueOf(entidad.getVerdictoPlazo()),
        EstadoSolicitudReversion.valueOf(entidad.getEstado()),
        entidad.getGestionadaEn(),
        entidad.getGestionadaPor(),
        entidad.getGestion(),
        entidad.getDesenlace() == null ? null : DesenlaceReversion.valueOf(entidad.getDesenlace()),
        entidad.getResueltaEn(),
        entidad.getReintegroId());
  }
}
