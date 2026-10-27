package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El descuento por pagar sin pasarela (ADR-0095): qué precio cobra cada medio, cómo lo guarda la
 * línea y cómo lo suma el pedido sin tocar el flete.
 */
class DescuentoSinPasarelaTest {

  private static final Instant AHORA = Instant.parse("2026-10-27T12:00:00Z");

  private static Variante variante(long publicado, long contado) {
    return new Variante(
        UUID.randomUUID(),
        new Sku("TS-BOL-1"),
        Dinero.deCop(publicado),
        Dinero.deCop(contado),
        BigDecimal.ZERO,
        null,
        null,
        EstadoVariante.ACTIVA,
        List.of(),
        false,
        false);
  }

  private static LineaPedido linea(long cobrado, long publicado, int cantidad) {
    return new LineaPedido(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Sku("TS-BOL-" + UUID.randomUUID().toString().substring(0, 8)),
        "Bolso",
        cantidad,
        Dinero.deCop(cobrado),
        BigDecimal.ZERO,
        null,
        UUID.randomUUID(),
        null,
        Dinero.deCop(publicado));
  }

  private static Pedido pedido(MetodoPago metodo, List<LineaPedido> lineas) {
    return Pedido.crear(
        NumeroPedido.de(2026, 7),
        null,
        new CorreoElectronico("cliente@tecnosport.co"),
        lineas,
        TipoEntrega.ENVIO_A_DOMICILIO,
        Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
        metodo,
        "cliente@tecnosport.co",
        AHORA,
        new TarifaEnvio(
            "rate_1",
            "Coordinadora",
            "Standard",
            Dinero.deCop(15_000),
            2,
            true,
            Instant.parse("2026-10-28T12:00:00Z")));
  }

  @Test
  void conPasarelaSeCobraElPublicadoYSinEllaElDeContado() {
    Variante bolso = variante(239_000, 230_000);

    assertEquals(Dinero.deCop(239_000), MetodoPago.WOMPI.precioDe(bolso));
    assertEquals(Dinero.deCop(239_000), MetodoPago.SISTECREDITO.precioDe(bolso));
    assertEquals(Dinero.deCop(230_000), MetodoPago.TRANSFERENCIA_MANUAL.precioDe(bolso));
    assertEquals(Dinero.deCop(230_000), MetodoPago.PAGO_EN_PUNTO.precioDe(bolso));
    assertEquals(Dinero.deCop(230_000), MetodoPago.CONTRAENTREGA.precioDe(bolso));
  }

  @Test
  void elDescuentoDeLaLineaEsLaDiferenciaPorLaCantidad() {
    LineaPedido linea = linea(230_000, 239_000, 3);

    assertEquals(Dinero.deCop(690_000), linea.subtotal());
    assertEquals(Dinero.deCop(717_000), linea.subtotalPublicado());
    assertEquals(Dinero.deCop(27_000), linea.descuento());
  }

  @Test
  void unaLineaDeAntesNoTieneDescuento() {
    LineaPedido vieja =
        new LineaPedido(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new Sku("TS-VIEJA"),
            "De antes",
            2,
            Dinero.deCop(50_000),
            BigDecimal.ZERO,
            null,
            UUID.randomUUID(),
            null);

    assertEquals(Dinero.deCop(50_000), vieja.precioPublicado());
    assertEquals(Dinero.deCop(0), vieja.descuento());
  }

  /** El publicado es el máximo que alguien paga: cobrar más sería un recargo. */
  @Test
  void noSeCobraPorEncimaDelPublicado() {
    assertThrows(ExcepcionDeDominio.class, () -> linea(240_000, 239_000, 1));
  }

  @Test
  void elPedidoSumaElDescuentoYLoRestaDelSubtotalPeroNoDelFlete() {
    Pedido pedido =
        pedido(
            MetodoPago.TRANSFERENCIA_MANUAL,
            List.of(linea(230_000, 239_000, 1), linea(100_000, 105_000, 2)));

    assertEquals(Dinero.deCop(449_000), pedido.subtotalPublicado());
    assertEquals(Dinero.deCop(19_000), pedido.descuento());
    assertEquals(Dinero.deCop(430_000), pedido.subtotal());
    assertEquals(Dinero.deCop(15_000), pedido.costoEnvio());
    assertEquals(Dinero.deCop(445_000), pedido.total());
  }

  @Test
  void conWompiElDescuentoEsCero() {
    Pedido pedido = pedido(MetodoPago.WOMPI, List.of(linea(239_000, 239_000, 2)));

    assertEquals(Dinero.deCop(0), pedido.descuento());
    assertEquals(pedido.subtotalPublicado(), pedido.subtotal());
    assertEquals(Dinero.deCop(493_000), pedido.total());
  }

  /** Un reintento de pago rehace las líneas con otra reserva; el precio congelado no se toca. */
  @Test
  void actualizarReservasConservaElPublicado() {
    LineaPedido linea = linea(230_000, 239_000, 1);
    Pedido pedido = pedido(MetodoPago.TRANSFERENCIA_MANUAL, List.of(linea));

    pedido.actualizarReservas(java.util.Map.of(linea.id(), UUID.randomUUID()));

    assertEquals(Dinero.deCop(239_000), pedido.lineas().get(0).precioPublicado());
    assertEquals(Dinero.deCop(9_000), pedido.descuento());
  }
}
