package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El plazo de 48 horas para verificar un contraentrega, decidido por el negocio. */
class VencerContraentregaSinVerificarTest {

  private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");
  private static final Duration PLAZO = Duration.ofHours(48);

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();
  private int secuencial;

  private VencerContraentregaSinVerificar casoDeUso() {
    return new VencerContraentregaSinVerificar(
        pedidos, inventarios, correos, new TextosDeCorreoFalso(), new RelojFalso(AHORA), PLAZO);
  }

  private Pedido contraentregaCreadoHace(Duration hace) {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(1, "siembra", AHORA.minus(Duration.ofDays(10)));
    MovimientoInventario reserva = inventario.reservar(1, null, AHORA.minus(hace));
    inventarios.conInventario(inventario);
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, ++secuencial),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta",
                    1,
                    Dinero.deCop(50_000),
                    BigDecimal.ZERO,
                    null,
                    reserva.id())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA.minus(hace));
    pedidos.guardar(pedido);
    return pedido;
  }

  private int disponible(Pedido pedido) {
    return inventarios
        .buscarPorVarianteId(pedido.lineas().get(0).varianteId())
        .orElseThrow()
        .saldoDisponible(AHORA);
  }

  @Test
  void pasadoElPlazoSeCancelaLiberaLaUnidadYSeAvisa() {
    Pedido pedido = contraentregaCreadoHace(Duration.ofHours(49));
    assertEquals(0, disponible(pedido));

    assertEquals(List.of(pedido.id()), casoDeUso().vencidos());
    assertTrue(casoDeUso().ejecutar(pedido.id()));

    assertEquals(EstadoPedido.CANCELADO, pedido.estado());
    assertEquals(1, disponible(pedido));
    assertEquals(1, correos.enviados().size());
    assertTrue(correos.enviados().get(0).cuerpoHtml().contains("contraentrega_vencida"));
  }

  @Test
  void dentroDelPlazoNoSeToca() {
    Pedido pedido = contraentregaCreadoHace(Duration.ofHours(47));

    assertTrue(casoDeUso().vencidos().isEmpty());
    assertFalse(casoDeUso().ejecutar(pedido.id()));
    assertEquals(EstadoPedido.CONFIRMADO_CONTRAENTREGA, pedido.estado());
  }

  /** Verificado entre la lista y la cancelación: la reserva sigue, hasta el despacho. */
  @Test
  void unoQueSeVerificoMientrasTantoNoSeCancela() {
    Pedido pedido = contraentregaCreadoHace(Duration.ofHours(60));
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:1", "verificado por WhatsApp", AHORA);
    pedidos.guardar(pedido);

    assertFalse(casoDeUso().ejecutar(pedido.id()));
    assertEquals(EstadoPedido.EN_PREPARACION, pedido.estado());
    assertEquals(0, disponible(pedido));
  }
}
