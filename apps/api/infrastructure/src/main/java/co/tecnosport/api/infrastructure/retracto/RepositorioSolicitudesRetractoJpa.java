package co.tecnosport.api.infrastructure.retracto;

import co.tecnosport.api.application.retracto.RepositorioSolicitudesRetracto;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.retracto.EstadoSolicitudRetracto;
import co.tecnosport.api.domain.retracto.MedioReembolso;
import co.tecnosport.api.domain.retracto.Reembolso;
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
 * guardado sin el otro.
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
    Optional<Reembolso> reembolso = solicitud.reembolso();
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
            reembolso.map(r -> r.monto().valor()).orElse(null),
            reembolso.map(r -> r.medio().name()).orElse(null),
            reembolso.flatMap(Reembolso::comprobanteOpcional).orElse(null),
            reembolso.map(Reembolso::registradoEn).orElse(null),
            reembolso.map(Reembolso::registradoPor).orElse(null)));
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
        aReembolso(entidad));
  }

  private Reembolso aReembolso(SolicitudRetractoJpaEntity entidad) {
    if (entidad.getReembolsoMonto() == null) {
      return null;
    }
    return new Reembolso(
        Dinero.deCop(entidad.getReembolsoMonto()),
        MedioReembolso.valueOf(entidad.getReembolsoMedio()),
        entidad.getReembolsoComprobante(),
        entidad.getReembolsoRegistradoEn(),
        entidad.getReembolsoRegistradoPor());
  }
}
