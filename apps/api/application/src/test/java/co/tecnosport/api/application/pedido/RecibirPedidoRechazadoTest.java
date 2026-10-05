package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.RepositorioReintegrosFalso;
import co.tecnosport.api.application.compartido.RepositorioSolicitudesReversionFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.inventario.TipoMovimientoInventario;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecibirPedidoRechazadoTest {

  private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final RepositorioReintegrosFalso reintegros = new RepositorioReintegrosFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  private UUID varianteId;

  private RecibirPedidoRechazado casoDeUso() {
    return new RecibirPedidoRechazado(
        pedidos,
        inventarios,
        reintegros,
        new TopeDeReintegro(reintegros, new RepositorioSolicitudesReversionFalso()),
        correos,
        new TextosDeCorreoFalso(),
        new RelojFalso(AHORA));
  }

  /**
   * Cinco unidades y una reservada para el pedido. En un pago en línea el pago ya confirmó la
   * reserva —hay {@code SALIDA}—, que es justo lo que hacía reventar el rechazo de antes.
   */
  private Pedido rechazado(MetodoPago metodoPago) {
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(5, "siembra", AHORA.minusSeconds(5000));
    boolean pagoEnLinea = metodoPago != MetodoPago.CONTRAENTREGA;
    // Como en CrearPedido: la reserva de contraentrega no vence, la de un pago en línea sí.
    MovimientoInventario reserva =
        inventario.reservar(
            1, pagoEnLinea ? Duration.ofMinutes(30) : null, AHORA.minusSeconds(4000));
    if (pagoEnLinea) {
      inventario.confirmar(reserva.id(), AHORA.minusSeconds(3900));
    }
    inventarios.conInventario(inventario);

    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 7),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    BigDecimal.ZERO,
                    "https://cdn.tecnosport.co/img.webp",
                    reserva.id())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            metodoPago,
            "cliente@tecnosport.co",
            AHORA.minusSeconds(4000));
    if (pagoEnLinea) {
      pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "pago aprobado", AHORA);
    }
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:1", "listo", AHORA);
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:1", "despachado", AHORA);
    pedido.transicionar(EstadoPedido.RECHAZADO_EN_ENTREGA, "skydropx", "no recibió", AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  private Inventario inventario() {
    return inventarios.buscarPorVarianteId(varianteId).orElseThrow();
  }

  private long movimientos(TipoMovimientoInventario tipo) {
    return inventario().movimientos().stream().filter(m -> m.tipo() == tipo).count();
  }

  @Test
  void unPedidoPagadoVuelveAlInventarioYSeDevuelveEntero() {
    Pedido pedido = rechazado(MetodoPago.WOMPI);
    assertEquals(4, inventario().saldoTotal());

    Pedido devuelto =
        casoDeUso()
            .ejecutar(
                new RecibirPedidoRechazadoComando(
                    pedido.id(), MedioReintegro.WOMPI, "REF-123", "admin:1"));

    assertEquals(EstadoPedido.DEVUELTO, devuelto.estado());
    // La reserva tenía salida: volver es una ENTRADA, no una liberación.
    // Dos entradas: la de la siembra y la de la vuelta.
    assertEquals(2, movimientos(TipoMovimientoInventario.ENTRADA));
    assertEquals(0, movimientos(TipoMovimientoInventario.LIBERACION));
    assertEquals(5, inventario().saldoTotal());

    List<Reintegro> guardados = reintegros.guardados();
    assertEquals(1, guardados.size());
    Reintegro reintegro = guardados.get(0);
    assertEquals(MotivoReintegro.RECHAZO_EN_ENTREGA, reintegro.motivo());
    assertEquals(pedido.total(), reintegro.monto());
    assertEquals(MedioReintegro.WOMPI, reintegro.medio());
    assertEquals(pedido.id(), reintegro.origenId());

    assertEquals(1, correos.enviados().size());
    String cuerpo = correos.enviados().get(0).cuerpoHtml();
    assertTrue(cuerpo.contains("pedido.devuelto.cuerpo"), cuerpo);
    assertTrue(cuerpo.contains("pedido.cancelacion.con_reintegro"), cuerpo);
  }

  /** Lo ya devuelto por otro camino no se devuelve dos veces: el monto es lo que falta. */
  @Test
  void seDevuelveSoloLoQueTodaviaNoVolvio() {
    Pedido pedido = rechazado(MetodoPago.WOMPI);
    reintegros.guardar(
        Reintegro.registrar(
            pedido.id(),
            MotivoReintegro.GARANTIA,
            UUID.randomUUID(),
            Dinero.deCop(20_000),
            MedioReintegro.WOMPI,
            null,
            AHORA,
            "admin:1"));

    casoDeUso()
        .ejecutar(
            new RecibirPedidoRechazadoComando(pedido.id(), MedioReintegro.WOMPI, null, "admin:1"));

    Reintegro nuevo = reintegros.guardados().get(1);
    assertEquals(
        Dinero.deCop(pedido.total().valor().subtract(BigDecimal.valueOf(20_000))), nuevo.monto());
  }

  @Test
  void sinMedioNoSeRecibeUnPedidoQueYaHabiaCobrado() {
    Pedido pedido = rechazado(MetodoPago.WOMPI);

    assertThrows(
        ReintegroRequeridoException.class,
        () ->
            casoDeUso()
                .ejecutar(new RecibirPedidoRechazadoComando(pedido.id(), null, null, "admin:1")));

    assertEquals(EstadoPedido.RECHAZADO_EN_ENTREGA, pedido.estado());
    assertEquals(4, inventario().saldoTotal());
    assertTrue(reintegros.guardados().isEmpty());
  }

  /**
   * Un contraentrega rechazado nunca cobró: no deja constancia de reintegro, y su reserva seguía
   * abierta, así que volver es liberarla.
   */
  @Test
  void unContraentregaLiberaLaReservaYNoDevuelveNada() {
    Pedido pedido = rechazado(MetodoPago.CONTRAENTREGA);
    assertEquals(4, inventario().saldoDisponible(AHORA));

    casoDeUso().ejecutar(new RecibirPedidoRechazadoComando(pedido.id(), null, null, "admin:1"));

    assertEquals(EstadoPedido.DEVUELTO, pedido.estado());
    assertEquals(1, movimientos(TipoMovimientoInventario.LIBERACION));
    assertEquals(5, inventario().saldoDisponible(AHORA));
    assertTrue(reintegros.guardados().isEmpty());
    assertTrue(
        correos.enviados().get(0).cuerpoHtml().contains("pedido.cancelacion.sin_cobro"),
        correos.enviados().get(0).cuerpoHtml());
  }

  /** Por Sistecrédito no vuelve dinero: se anula un crédito, y el correo tiene que decir eso. */
  @Test
  void porSistecreditoElCorreoHablaDeAnularElCredito() {
    Pedido pedido = rechazado(MetodoPago.SISTECREDITO);

    casoDeUso()
        .ejecutar(
            new RecibirPedidoRechazadoComando(
                pedido.id(), MedioReintegro.SISTECREDITO, null, "admin:1"));

    String cuerpo = correos.enviados().get(0).cuerpoHtml();
    assertTrue(cuerpo.contains("pedido.devuelto.reintegro_sistecredito"), cuerpo);
  }

  @Test
  void recibirDosVecesNoReingresaDosVeces() {
    Pedido pedido = rechazado(MetodoPago.WOMPI);
    RecibirPedidoRechazadoComando comando =
        new RecibirPedidoRechazadoComando(pedido.id(), MedioReintegro.WOMPI, null, "admin:1");
    casoDeUso().ejecutar(comando);

    assertThrows(TransicionDeEstadoInvalidaException.class, () -> casoDeUso().ejecutar(comando));

    assertEquals(5, inventario().saldoTotal());
    assertEquals(1, reintegros.guardados().size());
  }

  @Test
  void unPedidoQueNoFueRechazadoNoSeRecibe() {
    Pedido pedido = rechazado(MetodoPago.CONTRAENTREGA);
    Pedido otro =
        Pedido.crear(
            NumeroPedido.de(2026, 8),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            pedido.lineas(),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.guardar(otro);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () ->
            casoDeUso()
                .ejecutar(new RecibirPedidoRechazadoComando(otro.id(), null, null, "admin:1")));
    assertEquals(0, movimientos(TipoMovimientoInventario.LIBERACION));
  }
}
