package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MedioDePagoEnPunto;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** La entrega en el punto, a quien muestra el número de pedido (ADR-0094). */
class EntregarEnPuntoTest {

  private static final Instant AHORA = Instant.parse("2026-10-26T12:00:00Z");
  private static final Instant CREADO = AHORA.minus(Duration.ofDays(2));
  private static final Instant LISTO = AHORA.minus(Duration.ofHours(5));

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final Recogidas recogidas = new Recogidas(pedidos, inventarios);

  private EntregarEnPunto casoDeUso() {
    return new EntregarEnPunto(pedidos, inventarios, new RelojFalso(AHORA));
  }

  private static EntregarEnPuntoComando comando(
      Pedido pedido, String numero, MedioDePagoEnPunto medio) {
    return new EntregarEnPuntoComando(pedido.id(), numero, medio, "admin:1");
  }

  /** El dinero entra en este acto, y con él sale la unidad del inventario. */
  @Test
  void conElNumeroYElPagoQuedaEntregadaHoyYLaUnidadSale() {
    Pedido pedido = recogidas.lista(MetodoPago.PAGO_EN_PUNTO, CREADO, LISTO);
    String numero = pedido.numeroPedido().valor();

    ResultadoEntrega resultado =
        casoDeUso().ejecutar(comando(pedido, numero, MedioDePagoEnPunto.EFECTIVO));

    assertTrue(resultado.inventarioConfirmado());
    assertEquals(EstadoPedido.ENTREGADO, pedido.estado());
    assertEquals(AHORA, pedido.fechaDeEntrega().orElseThrow());
    assertEquals(Dinero.deCop(50_000), pedido.dineroRecibido());
    assertEquals("entregado en el punto; pagó en efectivo", pedido.historial().getLast().motivo());
    assertTrue(recogidas.inventarioDe(pedido).estaConfirmada(pedido.lineas().get(0).idReserva()));
  }

  /** Se dicta en voz alta o se lee de un teléfono: mayúsculas y espacios no cuentan. */
  @Test
  void elNumeroSeComparaSinMayusculasNiEspaciosAlrededor() {
    Pedido pedido = recogidas.lista(MetodoPago.PAGO_EN_PUNTO, CREADO, LISTO);
    String dictado = "  " + pedido.numeroPedido().valor().toLowerCase(Locale.ROOT) + " ";

    casoDeUso().ejecutar(comando(pedido, dictado, MedioDePagoEnPunto.TRANSFERENCIA));

    assertEquals(EstadoPedido.ENTREGADO, pedido.estado());
    assertEquals(
        "entregado en el punto; pagó por transferencia", pedido.historial().getLast().motivo());
  }

  @Test
  void conOtroNumeroNoSeEntrega() {
    Pedido pedido = recogidas.lista(MetodoPago.PAGO_EN_PUNTO, CREADO, LISTO);
    Pedido otro = recogidas.lista(MetodoPago.PAGO_EN_PUNTO, CREADO, LISTO);

    assertThrows(
        NumeroDePedidoNoCoincideException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    comando(pedido, otro.numeroPedido().valor(), MedioDePagoEnPunto.EFECTIVO)));
    assertThrows(
        NumeroDePedidoNoCoincideException.class,
        () -> casoDeUso().ejecutar(comando(pedido, null, MedioDePagoEnPunto.EFECTIVO)));
    assertEquals(EstadoPedido.LISTO_PARA_RECOGER, pedido.estado());
    assertFalse(recogidas.inventarioDe(pedido).estaConfirmada(pedido.lineas().get(0).idReserva()));
  }

  @Test
  void quienPagaEnElPuntoTieneQueDecirComoPago() {
    Pedido pedido = recogidas.lista(MetodoPago.PAGO_EN_PUNTO, CREADO, LISTO);

    assertThrows(
        IllegalArgumentException.class,
        () -> casoDeUso().ejecutar(comando(pedido, pedido.numeroPedido().valor(), null)));
    assertEquals(EstadoPedido.LISTO_PARA_RECOGER, pedido.estado());
  }

  /** Ya se cobró en línea: un medio de pago en el punto sería un dato falso en el historial. */
  @Test
  void unaPagadaEnLineaSeEntregaSinMedioDePago() {
    Pedido pedido = recogidas.lista(MetodoPago.WOMPI, CREADO, LISTO);
    String numero = pedido.numeroPedido().valor();

    assertThrows(
        IllegalArgumentException.class,
        () -> casoDeUso().ejecutar(comando(pedido, numero, MedioDePagoEnPunto.EFECTIVO)));

    casoDeUso().ejecutar(comando(pedido, numero, null));

    assertEquals(EstadoPedido.ENTREGADO, pedido.estado());
    assertEquals("entregado en el punto", pedido.historial().getLast().motivo());
  }

  @Test
  void unaQueNoEstaListaNoSeEntrega() {
    Pedido pedido = recogidas.enPreparacion(MetodoPago.PAGO_EN_PUNTO, CREADO);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () ->
            casoDeUso()
                .ejecutar(
                    comando(pedido, pedido.numeroPedido().valor(), MedioDePagoEnPunto.EFECTIVO)));
  }
}
