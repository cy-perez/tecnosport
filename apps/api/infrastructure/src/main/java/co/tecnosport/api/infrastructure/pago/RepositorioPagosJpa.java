package co.tecnosport.api.infrastructure.pago;

import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.infrastructure.pago.entidad.EventoPagoJpaEntity;
import co.tecnosport.api.infrastructure.pago.entidad.PagoJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Mismo patrón que {@code RepositorioPedidosJpa}: {@code evento_pago} se reemplaza completo en cada
 * {@code guardar}, más simple que llevar la cuenta de qué evento ya se insertó a esta escala (unos
 * pocos eventos por pago).
 */
@Component
public class RepositorioPagosJpa implements RepositorioPagos {

  private final PagoJpaRepository pagos;
  private final EventoPagoJpaRepository eventos;

  public RepositorioPagosJpa(PagoJpaRepository pagos, EventoPagoJpaRepository eventos) {
    this.pagos = Objects.requireNonNull(pagos);
    this.eventos = Objects.requireNonNull(eventos);
  }

  @Override
  public Optional<Pago> buscarPorReferencia(ReferenciaPago referencia) {
    return pagos.findByReferencia(referencia.valor()).map(this::aPago);
  }

  @Override
  public List<Pago> buscarPorPedidoId(UUID pedidoId) {
    return pagos.findByPedidoId(pedidoId).stream().map(this::aPago).toList();
  }

  @Override
  public List<Pago> buscarPendientesParaConciliar(Instant creadosAntesDe) {
    return pagos
        .findByEstadoAndIdTransaccionWompiIsNotNullAndCreadoEnBefore(
            EstadoPago.PENDIENTE.name(), creadosAntesDe)
        .stream()
        .map(this::aPago)
        .toList();
  }

  @Override
  public void guardar(Pago pago) {
    pagos.save(aEntidad(pago));

    eventos.deleteByPagoId(pago.id());
    eventos.saveAll(pago.eventos().stream().map(e -> aEntidadEvento(pago.id(), e)).toList());
  }

  private Pago aPago(PagoJpaEntity entidad) {
    List<EventoPago> eventosDelPago =
        eventos.findByPagoIdOrderByRecibidoEnAsc(entidad.getId()).stream()
            .map(this::aEvento)
            .toList();
    return new Pago(
        entidad.getId(),
        entidad.getPedidoId(),
        new ReferenciaPago(entidad.getReferencia()),
        MetodoPago.valueOf(entidad.getMetodoPago()),
        Dinero.deCop(entidad.getMonto()),
        EstadoPago.valueOf(entidad.getEstado()),
        eventosDelPago,
        entidad.getCreadoEn(),
        entidad.getActualizadoEn(),
        entidad.getIdTransaccionWompi());
  }

  private EventoPago aEvento(EventoPagoJpaEntity e) {
    return new EventoPago(e.getIdEvento(), EstadoPago.valueOf(e.getEstado()), e.getRecibidoEn());
  }

  private PagoJpaEntity aEntidad(Pago pago) {
    return new PagoJpaEntity(
        pago.id(),
        pago.pedidoId(),
        pago.referencia().valor(),
        pago.metodoPago().name(),
        pago.monto().valor(),
        pago.estado().name(),
        pago.creadoEn(),
        pago.actualizadoEn(),
        pago.idTransaccionWompi().orElse(null));
  }

  private EventoPagoJpaEntity aEntidadEvento(UUID pagoId, EventoPago e) {
    return new EventoPagoJpaEntity(
        GeneradorIdentificador.nuevo(), pagoId, e.idEvento(), e.estado().name(), e.recibidoEn());
  }
}
