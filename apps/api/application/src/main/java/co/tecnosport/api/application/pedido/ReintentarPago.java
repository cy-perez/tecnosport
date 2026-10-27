package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import co.tecnosport.api.domain.pedido.EnlaceDeCompraNoVigenteException;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Regresa un pedido {@code PAGO_FALLIDO} a {@code PAGO_PENDIENTE} para que el cliente pueda pedir
 * un intento de pago nuevo con {@code CrearIntentoDePago} — ese caso de uso deliberadamente no
 * acepta pedidos en {@code PAGO_FALLIDO} directo, así que hacía falta este paso aparte.
 *
 * <p>docs/02-modelo-datos.md: "el pago rechazado... la libera" — la reserva original de cada línea
 * ya no existe cuando el pedido llega aquí ({@code AplicadorDeResultadoDePago} la liberó al marcar
 * {@code PAGO_FALLIDO}). Reintentar exige una reserva nueva, revalidada contra la existencia real:
 * si ya no alcanza (se vendió mientras tanto), el reintento falla con {@code
 * ExistenciaInsuficienteException} — un caso de negocio real, no un error.
 *
 * <p>Solo un pedido procesado por Wompi llega a {@code PAGO_FALLIDO}: transferencia manual y
 * contraentrega nunca pasan por {@code AplicadorDeResultadoDePago}. El estado ya lo garantiza, así
 * que no hace falta revalidar el método de pago aparte, y la duración de reserva de pago en línea
 * siempre aplica.
 */
public final class ReintentarPago {

  private final RepositorioPedidos repositorioPedidos;
  private final RepositorioProductos repositorioProductos;
  private final RepositorioInventario repositorioInventario;
  private final Reloj reloj;
  private final Duration duracionReservaPagoEnLinea;
  private final RecogidaEnPunto recogidaEnPunto;
  private final RepositorioEnlacesDeCompra repositorioEnlaces;

  public ReintentarPago(
      RepositorioPedidos repositorioPedidos,
      RepositorioProductos repositorioProductos,
      RepositorioInventario repositorioInventario,
      Reloj reloj,
      Duration duracionReservaPagoEnLinea,
      RecogidaEnPunto recogidaEnPunto,
      RepositorioEnlacesDeCompra repositorioEnlaces) {
    this.repositorioEnlaces = Objects.requireNonNull(repositorioEnlaces);
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.repositorioInventario = Objects.requireNonNull(repositorioInventario);
    this.reloj = Objects.requireNonNull(reloj);
    this.duracionReservaPagoEnLinea = Objects.requireNonNull(duracionReservaPagoEnLinea);
    this.recogidaEnPunto = Objects.requireNonNull(recogidaEnPunto);
  }

  public Pedido ejecutar(ReintentarPagoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pedido pedido =
        repositorioPedidos
            .buscarPorIdParaModificar(comando.pedidoId())
            .orElseThrow(() -> new PedidoNoEncontradoException(comando.pedidoId()));
    // El correo autoriza, y un correo que no es el del pedido se trata como si el pedido no
    // existiera —mismo criterio que `ConsultarSeguimientoPedido`—: un 403 confirmaría que ese id
    // corresponde a una compra real, que es justo lo que no hay que confirmarle a quien prueba ids.
    String correoNormalizado =
        comando.correo() == null ? "" : comando.correo().trim().toLowerCase();
    if (!pedido.correo().valor().equals(correoNormalizado)) {
      throw new PedidoNoEncontradoException(comando.pedidoId());
    }
    // Se valida antes de reservar: si el pedido no estaba en PAGO_FALLIDO, reservar de todos modos
    // dejaría una reserva huérfana que nadie libera.
    if (!pedido.estado().puedeTransicionarA(EstadoPedido.PAGO_PENDIENTE)) {
      throw new TransicionDeEstadoInvalidaException(pedido.estado(), EstadoPedido.PAGO_PENDIENTE);
    }
    // Un pedido de recogida que falló en la pasarela antes de que la recogida se apagara no vence:
    // se puede reintentar cuando sea. Cobrarlo hoy es contratar una recogida que los términos
    // vigentes ya no ofrecen (ADR-0072), así que responde lo mismo que `CrearPedido` — y antes de
    // reservar, por lo mismo que la comprobación de arriba. Desde el 26/10/2026 eso incluye que
    // todo el pedido venga de proveedores locales: uno de antes con tecnología no se recoge.
    recogidaEnPunto.exigirDisponible(
        pedido.tipoEntrega(), pedido.lineas().stream().map(LineaPedido::varianteId).toList());
    // Lo que ya no se vende no se cobra otra vez: un modelo que el proveedor agotó o que venció
    // sigue con existencia en el libro —agotar no toca el inventario— y sin esto el reintento lo
    // reservaba y Wompi lo cobraba. Antes de reservar, por lo mismo que las de arriba.
    // Variante por variante, con las mismas dos reglas que CrearPedido: la prenda agotada por el
    // proveedor no se vende, y la que se confirma con el proveedor (ADR-0086) solo se cobra
    // mientras el enlace con el que se compró siga dentro de su plazo.
    Instant ahora = reloj.ahora();
    Map<UUID, Instant> vencimientoDelEnlacePorVariante = new LinkedHashMap<>();
    for (LineaPedido linea : pedido.lineas()) {
      Producto producto = ProductoVendible.exigir(repositorioProductos, linea.varianteId());
      Variante variante = ProductoVendible.exigirVariante(producto, linea.varianteId());
      if (variante.disponibilidadPorConfirmar()) {
        vencimientoDelEnlacePorVariante.put(
            linea.varianteId(), vencimientoDelEnlace(pedido, linea, ahora));
      }
    }
    Map<UUID, UUID> nuevasReservasPorLineaId =
        reReservar(pedido, ahora, vencimientoDelEnlacePorVariante);
    pedido.actualizarReservas(nuevasReservasPorLineaId);
    pedido.transicionar(
        EstadoPedido.PAGO_PENDIENTE, pedido.correo().valor(), "reintento de pago", ahora);
    repositorioPedidos.guardar(pedido);
    return pedido;
  }

  /**
   * Hasta cuándo vale el enlace con el que se compró esa línea. Decisión del 27/10/2026: el enlace
   * se usó al crear el pedido, pero lo que el proveedor confirmó vale lo que dura el enlace —24
   * horas—, no lo que tarde el comprador en reintentar. Vencido, no se cobra: el comprador pide
   * otro por WhatsApp, igual que si llegara con él vencido a {@code CrearPedido}, y con la misma
   * excepción. Sin enlace —la variante pasó a «por confirmar» después de comprarse— responde lo
   * mismo que {@code CrearPedido} sin enlace.
   */
  private Instant vencimientoDelEnlace(Pedido pedido, LineaPedido linea, Instant ahora) {
    EnlaceDeCompra enlace =
        repositorioEnlaces
            .buscarUsadoEnPedido(pedido.id(), linea.varianteId())
            .orElseThrow(
                () ->
                    new DisponibilidadPorConfirmarException(
                        linea.nombre(), linea.detalleVariante()));
    if (!enlace.expiraEn().isAfter(ahora)) {
      throw new EnlaceDeCompraNoVigenteException();
    }
    return enlace.expiraEn();
  }

  private Map<UUID, UUID> reReservar(
      Pedido pedido, Instant ahora, Map<UUID, Instant> vencimientoDelEnlacePorVariante) {
    Map<UUID, UUID> nuevasReservasPorLineaId = new LinkedHashMap<>();
    // En orden de `varianteId`, como CrearPedido: cada `buscarPorVarianteId` toma un bloqueo
    // pesimista, y dos transacciones que bloquean las mismas variantes en órdenes distintos se
    // interbloquean (40P01). El comprador lo veía como un 500 al reintentar el pago.
    List<LineaPedido> enOrden =
        pedido.lineas().stream().sorted(Comparator.comparing(LineaPedido::varianteId)).toList();
    for (LineaPedido linea : enOrden) {
      Inventario inventario =
          repositorioInventario
              .buscarPorVarianteId(linea.varianteId())
              .orElseThrow(() -> new VarianteNoEncontradaException(linea.varianteId()));
      // La unidad que apartó el enlace sigue apartada hasta que el enlace habría vencido, como en
      // CrearPedido: si no, las 24 horas confirmadas se volvían los 30 minutos del pago en línea.
      Duration vigencia = duracionReservaPagoEnLinea;
      Instant venceElEnlace = vencimientoDelEnlacePorVariante.get(linea.varianteId());
      if (venceElEnlace != null) {
        Duration hastaQueVence = Duration.between(ahora, venceElEnlace);
        if (hastaQueVence.compareTo(vigencia) > 0) {
          vigencia = hastaQueVence;
        }
      }
      MovimientoInventario reserva = inventario.reservar(linea.cantidad(), vigencia, ahora);
      repositorioInventario.guardar(inventario);
      nuevasReservasPorLineaId.put(linea.id(), reserva.id());
    }
    return nuevasReservasPorLineaId;
  }
}
