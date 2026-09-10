package co.tecnosport.api.infrastructure.retracto;

import co.tecnosport.api.application.retracto.RepositorioSolicitudesRetracto;
import co.tecnosport.api.domain.retracto.EstadoSolicitudRetracto;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import co.tecnosport.api.domain.retracto.VerdictoPlazo;
import co.tecnosport.api.infrastructure.retracto.entidad.SolicitudRetractoJpaEntity;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sin transacción propia, mismo criterio que {@code RepositorioPedidosJpa}: comparte la de quien lo
 * llama, para que la solicitud y el pedido de {@code RecibirProductoDevuelto} no puedan quedar uno
 * guardado sin el otro. Lo mismo vale ahora para la constancia del reintegro, que es otra fila y
 * otra tabla desde V23.
 */
@Component
public class RepositorioSolicitudesRetractoJpa implements RepositorioSolicitudesRetracto {

  private final SolicitudRetractoJpaRepository repositorio;

  public RepositorioSolicitudesRetractoJpa(SolicitudRetractoJpaRepository repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  @Override
  public Optional<SolicitudRetracto> buscarPorId(UUID id) {
    return repositorio.findById(id).map(this::aSolicitud);
  }

  @Override
  public List<SolicitudRetracto> buscarPorPedidoId(UUID pedidoId) {
    return repositorio.findByPedidoIdOrderByRadicadaEnDesc(pedidoId).stream()
        .map(this::aSolicitud)
        .toList();
  }

  @Override
  public void guardar(SolicitudRetracto solicitud) {
    repositorio.save(
        new SolicitudRetractoJpaEntity(
            solicitud.id(),
            solicitud.pedidoId(),
            solicitud.radicadaEn(),
            solicitud.radicadaPor(),
            solicitud.motivo().orElse(null),
            solicitud.verdictoAlRadicar().name(),
            solicitud.estado().name(),
            solicitud.productoRecibidoEn().orElse(null),
            solicitud.reintegroId().orElse(null)));
  }

  private SolicitudRetracto aSolicitud(SolicitudRetractoJpaEntity entidad) {
    return new SolicitudRetracto(
        entidad.getId(),
        entidad.getPedidoId(),
        entidad.getRadicadaEn(),
        entidad.getRadicadaPor(),
        entidad.getMotivo(),
        VerdictoPlazo.valueOf(entidad.getVerdictoPlazo()),
        EstadoSolicitudRetracto.valueOf(entidad.getEstado()),
        entidad.getProductoRecibidoEn(),
        entidad.getReintegroId());
  }
}
