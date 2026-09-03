package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Sku;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PedidoTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");
  private static final CorreoElectronico CORREO = new CorreoElectronico("cliente@tecnosport.co");
  private static final NumeroPedido NUMERO = NumeroPedido.de(2026, 1);

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private LineaPedido linea(BigDecimal precioUnitario, int cantidad) {
    return new LineaPedido(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        cantidad,
        Dinero.deCop(precioUnitario),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.webp",
        UUID.randomUUID());
  }

  private Pedido crearAlDomicilio(MetodoPago metodoPago) {
    return Pedido.crear(
        NUMERO,
        null,
        CORREO,
        List.of(linea(BigDecimal.valueOf(50_000), 2)),
        TipoEntrega.ENVIO_A_DOMICILIO,
        DIRECCION_MEDELLIN,
        metodoPago,
        "cliente@tecnosport.co",
        AHORA);
  }

  @Test
  void crearConMetodoEnLineaQuedaEnPagoPendiente() {
    Pedido pedido = crearAlDomicilio(MetodoPago.NEQUI);

    assertEquals(EstadoPedido.PAGO_PENDIENTE, pedido.estado());
  }

  @Test
  void crearConContraentregaQuedaConfirmadoSinPasarPorPagoPendiente() {
    Pedido pedido = crearAlDomicilio(MetodoPago.CONTRAENTREGA);

    assertEquals(EstadoPedido.CONFIRMADO_CONTRAENTREGA, pedido.estado());
  }

  @Test
  void conservaElMetodoDePagoElegido() {
    Pedido pedido = crearAlDomicilio(MetodoPago.NEQUI);

    assertEquals(MetodoPago.NEQUI, pedido.metodoPago());
  }

  @Test
  void crearRegistraUnPrimerHistorialConElActorYElMotivo() {
    Pedido pedido = crearAlDomicilio(MetodoPago.NEQUI);

    assertEquals(1, pedido.historial().size());
    HistorialPedido registro = pedido.historial().get(0);
    assertEquals(EstadoPedido.PAGO_PENDIENTE, registro.estado());
    assertEquals("cliente@tecnosport.co", registro.actor());
    assertEquals(AHORA, registro.fecha());
  }

  @Test
  void crearSinLineasLanzaExcepcionDeDominio() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            Pedido.crear(
                NUMERO,
                null,
                CORREO,
                List.of(),
                TipoEntrega.ENVIO_A_DOMICILIO,
                DIRECCION_MEDELLIN,
                MetodoPago.NEQUI,
                "cliente@tecnosport.co",
                AHORA));
  }

  @Test
  void envioADomicilioSinDireccionSeRechaza() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            Pedido.crear(
                NUMERO,
                null,
                CORREO,
                List.of(linea(BigDecimal.valueOf(50_000), 1)),
                TipoEntrega.ENVIO_A_DOMICILIO,
                null,
                MetodoPago.NEQUI,
                "cliente@tecnosport.co",
                AHORA));
  }

  @Test
  void retiroEnPuntoConDireccionSeRechaza() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            Pedido.crear(
                NUMERO,
                null,
                CORREO,
                List.of(linea(BigDecimal.valueOf(50_000), 1)),
                TipoEntrega.RETIRO_EN_PUNTO,
                DIRECCION_MEDELLIN,
                MetodoPago.NEQUI,
                "cliente@tecnosport.co",
                AHORA));
  }

  @Test
  void retiroEnPuntoSinDireccionEsValido() {
    Pedido pedido =
        Pedido.crear(
            NUMERO,
            null,
            CORREO,
            List.of(linea(BigDecimal.valueOf(50_000), 1)),
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            AHORA);

    assertTrue(pedido.direccion().isEmpty());
  }

  @Test
  void totalSumaElSubtotalDeCadaLinea() {
    Pedido pedido =
        Pedido.crear(
            NUMERO,
            null,
            CORREO,
            List.of(linea(BigDecimal.valueOf(50_000), 2), linea(BigDecimal.valueOf(30_000), 1)),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            AHORA);

    assertEquals(Dinero.deCop(130_000), pedido.total());
  }

  @Test
  void transicionarAUnEstadoValidoActualizaEstadoYAgregaHistorial() {
    Pedido pedido = crearAlDomicilio(MetodoPago.NEQUI);

    pedido.transicionar(
        EstadoPedido.PAGADO, "webhook-wompi", "pago aprobado", AHORA.plusSeconds(60));

    assertEquals(EstadoPedido.PAGADO, pedido.estado());
    assertEquals(2, pedido.historial().size());
    assertEquals("pago aprobado", pedido.historial().get(1).motivo());
  }

  @Test
  void unPedidoEntregadoNuncaVuelveAPagado() {
    Pedido pedido = crearAlDomicilio(MetodoPago.NEQUI);
    pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "pago aprobado", AHORA);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin", "alistado", AHORA);
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin", "despachado", AHORA);
    pedido.transicionar(EstadoPedido.ENTREGADO, "admin", "entregado", AHORA);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () -> pedido.transicionar(EstadoPedido.PAGADO, "admin", "reintento indebido", AHORA));
  }

  @Test
  void transicionarSaltandoEstadosSeRechaza() {
    Pedido pedido = crearAlDomicilio(MetodoPago.NEQUI);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () -> pedido.transicionar(EstadoPedido.DESPACHADO, "admin", "salto inválido", AHORA));
  }
}
