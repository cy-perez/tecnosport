package co.tecnosport.api.application.pago;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.PedidoNoEncontradoException;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.ProveedorDePago;
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
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;

  public CrearIntentoDePago(
      RepositorioPedidos repositorioPedidos,
      RepositorioPagos repositorioPagos,
      PasarelaDePagos pasarelaDePagos,
      RepositorioInventario repositorioInventario,
      Reloj reloj) {
    this.repositorioPedidos =
        Objects.requireNonNull(repositorioPedidos, "El repositorio de pedidos no puede ser nulo.");
    this.repositorioPagos =
        Objects.requireNonNull(repositorioPagos, "El repositorio de pagos no puede ser nulo.");
    this.pasarelaDePagos =
        Objects.requireNonNull(pasarelaDePagos, "La pasarela de pagos no puede ser nula.");
    this.repositorioInventario =
        Objects.requireNonNull(
            repositorioInventario, "El repositorio de inventario no puede ser nulo.");
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

    Instant ahora = reloj.ahora();
    // Antes de crear el pago: con la reserva vencida, la unidad pudo venderse a otro y lo que se
    // cobraría es un pedido sin mercancía detrás.
    ReservasDelPedido.exigirVigentes(pedido, repositorioInventario, ahora);

    int numeroDeIntento = repositorioPagos.buscarPorPedidoId(pedido.id()).size() + 1;
    ReferenciaPago referencia =
        new ReferenciaPago(pedido.numeroPedido().valor() + "-" + numeroDeIntento);

    Pago pago = Pago.crear(pedido.id(), referencia, pedido.metodoPago(), pedido.total(), ahora);
    repositorioPagos.guardar(pago);

    String firma = pasarelaDePagos.generarFirmaIntegridad(referencia, pedido.total());
    return new IntentoDePago(referencia, pedido.total(), firma);
  }

  /**
   * Que el método lo cobre Wompi no significa que esté habilitado hoy: eso lo decide {@code
   * MetodosDePagoDisponibles} con la configuración, y {@code CrearPedido} lo exige antes de que el
   * pedido exista. Aquí solo se comprueba lo que no depende de la configuración — a un pedido de
   * contraentrega o de transferencia manual no hay intento de pago que pedirle.
   *
   * <p>Se pregunta por Wompi y no por "¿lo cobra alguna pasarela?" desde {@code adr/0048}: un
   * pedido de Sistecrédito lo cobra una pasarela, pero no esta, y tiene su propio caso de uso. Con
   * el predicado viejo habría entrado aquí y se habría ido a firmar contra Wompi.
   */
  private boolean seProcesaPorWompi(MetodoPago metodoPago) {
    return metodoPago.pasarela() == ProveedorDePago.WOMPI;
  }
}
