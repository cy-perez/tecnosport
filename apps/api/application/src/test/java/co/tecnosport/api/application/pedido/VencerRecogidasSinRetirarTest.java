package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MedioDePagoEnPunto;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Las 48 horas que una recogida espera en el punto, desde el aviso (ADR-0094). */
class VencerRecogidasSinRetirarTest {

  private static final Instant AHORA = Instant.parse("2026-10-26T12:00:00Z");
  private static final Instant HACE_UNA_SEMANA = AHORA.minus(Duration.ofDays(7));

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();
  private final Recogidas recogidas = new Recogidas(pedidos, inventarios);

  private VencerRecogidasSinRetirar casoDeUso() {
    return new VencerRecogidasSinRetirar(
        pedidos, inventarios, correos, new TextosDeCorreoFalso(), new RelojFalso(AHORA));
  }

  private int disponible(Pedido pedido) {
    return recogidas.inventarioDe(pedido).saldoDisponible(AHORA);
  }

  @Test
  void pasadoElPlazoSeCancelaLiberaLaUnidadYSeAvisa() {
    Pedido pedido =
        recogidas.lista(
            MetodoPago.PAGO_EN_PUNTO, HACE_UNA_SEMANA, AHORA.minus(Duration.ofHours(49)));
    assertEquals(0, disponible(pedido));

    assertEquals(List.of(pedido.id()), casoDeUso().vencidas());
    assertTrue(casoDeUso().ejecutar(pedido.id()));

    assertEquals(EstadoPedido.CANCELADO, pedido.estado());
    assertEquals(1, disponible(pedido));
    String cuerpo = correos.enviados().get(0).cuerpoHtml();
    assertEquals("Carlos Ruiz", correos.enviados().get(0).nombre());
    assertTrue(cuerpo.contains("[pedido.recogida_vencida.cuerpo|"), cuerpo);
    assertTrue(cuerpo.contains("[pedido.cancelacion.sin_cobro]"), cuerpo);
  }

  /** Comprada hace una semana y lista hace una hora: lo que tardó en llegar no le corre. */
  @Test
  void elPlazoCorreDesdeElAvisoYNoDesdeLaCompra() {
    Pedido pedido =
        recogidas.lista(
            MetodoPago.PAGO_EN_PUNTO, HACE_UNA_SEMANA, AHORA.minus(Duration.ofHours(1)));

    assertTrue(casoDeUso().vencidas().isEmpty());
    assertFalse(casoDeUso().ejecutar(pedido.id()));
    assertEquals(EstadoPedido.LISTO_PARA_RECOGER, pedido.estado());
    assertEquals(0, disponible(pedido));
  }

  /** Pagada en línea: cancelarla exige devolver el dinero, y eso lo hace una persona. */
  @Test
  void unaPagadaEnLineaNoSeCancelaSola() {
    Pedido pedido =
        recogidas.lista(MetodoPago.WOMPI, HACE_UNA_SEMANA, AHORA.minus(Duration.ofHours(72)));

    assertTrue(casoDeUso().vencidas().isEmpty());
    assertFalse(casoDeUso().ejecutar(pedido.id()));
    assertEquals(EstadoPedido.LISTO_PARA_RECOGER, pedido.estado());
    assertTrue(correos.enviados().isEmpty());
  }

  @Test
  void unaEntregadaMientrasTantoNoSeCancela() {
    Pedido pedido =
        recogidas.lista(
            MetodoPago.PAGO_EN_PUNTO, HACE_UNA_SEMANA, AHORA.minus(Duration.ofHours(50)));
    pedido.entregarEnPunto(
        pedido.numeroPedido().valor(), MedioDePagoEnPunto.EFECTIVO, "admin:1", AHORA);
    pedidos.guardar(pedido);

    assertFalse(casoDeUso().ejecutar(pedido.id()));
    assertEquals(EstadoPedido.ENTREGADO, pedido.estado());
  }
}
