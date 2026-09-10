package co.tecnosport.api.application.retracto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.retracto.CalendarioHabil;
import co.tecnosport.api.domain.retracto.EstadoSolicitudRetracto;
import co.tecnosport.api.domain.retracto.PlazoDeRetracto;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecibirProductoDevueltoTest {

  private static final Instant ENTREGA =
      ZonedDateTime.of(2026, 9, 10, 15, 30, 0, 0, PlazoDeRetracto.ZONA).toInstant();
  private static final Instant RADICACION =
      ZonedDateTime.of(2026, 9, 14, 10, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant();
  private static final Instant RECEPCION =
      ZonedDateTime.of(2026, 9, 16, 10, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant();

  private final RepositorioSolicitudesRetractoFalso solicitudes =
      new RepositorioSolicitudesRetractoFalso();
  private final RepositorioPedidosParaRetractoFalso pedidos =
      new RepositorioPedidosParaRetractoFalso();
  private final RepositorioInventarioParaRetractoFalso inventarios =
      new RepositorioInventarioParaRetractoFalso();

  private RecibirProductoDevuelto casoDeUso() {
    return new RecibirProductoDevuelto(
        solicitudes, pedidos, inventarios, new RelojFalso(RECEPCION));
  }

  /**
   * Monta el escenario completo: inventario con la reserva de la línea, resuelta o no según el
   * método de pago, pedido entregado y solicitud radicada.
   */
  private SolicitudRetracto escenario(MetodoPago metodoPago) {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(5, "siembra", ENTREGA.minusSeconds(1000));
    Duration vigencia = metodoPago == MetodoPago.CONTRAENTREGA ? null : Duration.ofMinutes(30);
    MovimientoInventario reserva = inventario.reservar(1, vigencia, ENTREGA.minusSeconds(900));
    if (metodoPago != MetodoPago.CONTRAENTREGA) {
      inventario.confirmar(reserva.id(), ENTREGA.minusSeconds(800));
    }
    inventarios.sembrar(inventario);

    LineaPedido linea = PedidosDePrueba.linea(varianteId, reserva.id());
    Pedido pedido = PedidosDePrueba.entregado(metodoPago, linea, ENTREGA);
    if (metodoPago == MetodoPago.CONTRAENTREGA) {
      pedido.transicionar(EstadoPedido.RECAUDO_PENDIENTE, "admin:1", "recaudo", ENTREGA);
      pedido.transicionar(EstadoPedido.RECAUDO_CONCILIADO, "admin:1", "conciliado", ENTREGA);
    }
    pedidos.sembrar(pedido);

    SolicitudRetracto solicitud =
        SolicitudRetracto.radicar(
            pedido.id(),
            ENTREGA,
            RADICACION,
            "admin:1",
            null,
            CalendarioHabil.sinFestivosCargados());
    solicitudes.guardar(solicitud);
    return solicitud;
  }

  private Inventario inventarioDeLaLinea() {
    return inventarios
        .buscarPorVarianteId(
            pedidos
                .buscarPorId(solicitudes.todas().get(0).pedidoId())
                .orElseThrow()
                .lineas()
                .get(0)
                .varianteId())
        .orElseThrow();
  }

  /**
   * Los dos saldos, y hacen falta los dos. Con la reserva de un contraentrega todavía abierta,
   * sumar una entrada indebida deja el disponible en 5 igual —porque la reserva sigue descontando—
   * y solo el total delata la unidad contada dos veces. Una prueba que mirara únicamente el
   * disponible no distinguiría la implementación correcta de la equivocada, y así estaba escrita al
   * principio.
   */
  private void assertSaldos(int totalEsperado, int disponibleEsperado) {
    Inventario inventario = inventarioDeLaLinea();
    assertEquals(totalEsperado, inventario.saldoTotal(), "saldo total");
    assertEquals(disponibleEsperado, inventario.saldoDisponible(RECEPCION), "saldo disponible");
  }

  @Test
  void pagoEnLineaDejaElPedidoDevueltoYLaUnidadOtraVezVendible() {
    SolicitudRetracto solicitud = escenario(MetodoPago.NEQUI);

    casoDeUso().ejecutar(new RecibirProductoDevueltoComando(solicitud.id(), "admin:1"));

    assertEquals(
        EstadoPedido.DEVUELTO, pedidos.buscarPorId(solicitud.pedidoId()).orElseThrow().estado());
    assertEquals(EstadoSolicitudRetracto.PRODUCTO_RECIBIDO, solicitud.estado());
    assertSaldos(5, 5);
  }

  @Test
  void contraentregaYaRecaudadoTambienSePuedeDevolver() {
    // El camino que el grafo no tenía: RECAUDO_CONCILIADO era terminal.
    SolicitudRetracto solicitud = escenario(MetodoPago.CONTRAENTREGA);

    casoDeUso().ejecutar(new RecibirProductoDevueltoComando(solicitud.id(), "admin:1"));

    assertEquals(
        EstadoPedido.DEVUELTO, pedidos.buscarPorId(solicitud.pedidoId()).orElseThrow().estado());
    assertSaldos(5, 5);
  }

  @Test
  void laFechaDeRecepcionArrancaElPlazoDeReintegro() {
    SolicitudRetracto solicitud = escenario(MetodoPago.NEQUI);

    casoDeUso().ejecutar(new RecibirProductoDevueltoComando(solicitud.id(), "admin:1"));

    assertEquals(
        ZonedDateTime.of(2026, 10, 2, 0, 0, 0, 0, PlazoDeRetracto.ZONA).toInstant(),
        solicitud.limiteDeReintegro().orElseThrow());
  }

  @Test
  void recibirDosVecesNoMueveElInventarioDeNuevo() {
    SolicitudRetracto solicitud = escenario(MetodoPago.NEQUI);
    RecibirProductoDevuelto caso = casoDeUso();
    caso.ejecutar(new RecibirProductoDevueltoComando(solicitud.id(), "admin:1"));

    assertThrows(
        ExcepcionDeDominio.class,
        () -> caso.ejecutar(new RecibirProductoDevueltoComando(solicitud.id(), "admin:1")));
    assertSaldos(5, 5);
  }

  @Test
  void unaSolicitudInexistenteFalla() {
    assertThrows(
        SolicitudRetractoNoEncontradaException.class,
        () ->
            casoDeUso().ejecutar(new RecibirProductoDevueltoComando(UUID.randomUUID(), "admin:1")));
  }
}
