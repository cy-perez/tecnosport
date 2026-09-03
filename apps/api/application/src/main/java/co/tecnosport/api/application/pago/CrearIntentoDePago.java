package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Instant;
import java.util.Objects;

/**
 * Crea un nuevo intento de pago contra Wompi para un pedido en {@code PAGO_PENDIENTE}
 * (docs/11-pagos-y-envios.md, docs/03-api.md). El monto nunca lo trae el cliente: sale de {@link
 * Pedido#total()}, que ya congeló precios reales al crear el pedido.
 *
 * <p>Un pedido en {@code PAGO_FALLIDO} no genera un intento nuevo todavía: falta decidir quién lo
 * regresa a {@code PAGO_PENDIENTE} antes del reintento (docs/02-modelo-datos.md sí contempla esa
 * transición). Queda fuera de este caso de uso a propósito, para el caso de uso de reintento.
 */
public final class CrearIntentoDePago {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioPagos repositorioPagos;
  private final PasarelaDePagos pasarelaDePagos;
  private final Reloj reloj;

  public CrearIntentoDePago(
      RepositorioPedidos repositorioPedidos,
      RepositorioPagos repositorioPagos,
      PasarelaDePagos pasarelaDePagos,
      Reloj reloj) {
    this.repositorioPedidos =
        Objects.requireNonNull(repositorioPedidos, "El repositorio de pedidos no puede ser nulo.");
    this.repositorioPagos =
        Objects.requireNonNull(repositorioPagos, "El repositorio de pagos no puede ser nulo.");
    this.pasarelaDePagos =
        Objects.requireNonNull(pasarelaDePagos, "La pasarela de pagos no puede ser nula.");
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
  }

  public IntentoDePago ejecutar(CrearIntentoDePagoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorId(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    if (pedido.estado() != EstadoPedido.PAGO_PENDIENTE) {
      throw new PedidoNoEstaEnPagoPendienteException(pedido.estado());
    }
    if (!seProcesaPorWompi(pedido.metodoPago())) {
      throw new MetodoDePagoNoSoportadoPorWompiException(pedido.metodoPago());
    }

    int numeroDeIntento = repositorioPagos.buscarPorPedidoId(pedido.id()).size() + 1;
    ReferenciaPago referencia =
        new ReferenciaPago(pedido.numeroPedido().valor() + "-" + numeroDeIntento);

    Instant ahora = reloj.ahora();
    Pago pago = Pago.crear(pedido.id(), referencia, pedido.metodoPago(), pedido.total(), ahora);
    repositorioPagos.guardar(pago);

    String firma = pasarelaDePagos.generarFirmaIntegridad(referencia, pedido.total());
    return new IntentoDePago(referencia, pedido.total(), firma);
  }

  private boolean seProcesaPorWompi(MetodoPago metodoPago) {
    return switch (metodoPago) {
      case TARJETA, PSE, NEQUI, BANCOLOMBIA, ADDI -> true;
      case TRANSFERENCIA_MANUAL, CONTRAENTREGA -> false;
    };
  }
}
