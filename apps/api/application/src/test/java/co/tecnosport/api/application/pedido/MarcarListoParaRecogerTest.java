package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EntregaNoAplicaException;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El despacho de la recogida: el pedido está en el punto y el comprador se entera (ADR-0094). */
class MarcarListoParaRecogerTest {

  private static final Instant AHORA = Instant.parse("2026-10-26T12:00:00Z");
  private static final String URL_ESTADO = "https://tecnosport.co/pedido";

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();
  private final Recogidas recogidas = new Recogidas(pedidos, inventarios);

  private MarcarListoParaRecoger casoDeUso() {
    return new MarcarListoParaRecoger(
        pedidos, correos, new TextosDeCorreoFalso(), new RelojFalso(AHORA), URL_ESTADO);
  }

  /**
   * Las 48 horas corren desde este aviso, y el correo dice hasta cuándo en la hora de Colombia:
   * 12:00 UTC del 26 más 48 horas son las 7:00 del 28 en Medellín.
   */
  @Test
  void quedaListaYElCompradorSabeCuantoPagaYHastaCuando() {
    Pedido pedido =
        recogidas.enPreparacion(MetodoPago.PAGO_EN_PUNTO, AHORA.minus(Duration.ofDays(3)));

    casoDeUso().ejecutar(new MarcarListoParaRecogerComando(pedido.id(), "admin:1"));

    assertEquals(EstadoPedido.LISTO_PARA_RECOGER, pedido.estado());
    assertEquals(AHORA.plus(Duration.ofHours(48)), pedido.recogidaVenceEn().orElseThrow());
    assertEquals(1, correos.enviados().size());
    String cuerpo = correos.enviados().get(0).cuerpoHtml();
    assertEquals("Ana Pérez", correos.enviados().get(0).nombre());
    assertTrue(
        cuerpo.contains("[pedido.listo_para_recoger.cuerpo|" + pedido.numeroPedido().valor()));
    assertTrue(cuerpo.contains("[pedido.listo_para_recoger.pago_en_punto|50000]"), cuerpo);
    assertTrue(
        cuerpo.contains("[pedido.listo_para_recoger.plazo_sin_cobro|28/10/2026 07:00]"), cuerpo);
  }

  @Test
  void unaPagadaEnLineaAvisaQueNoPagaNadaYQueSeLeDevuelveSiNoViene() {
    Pedido pedido = recogidas.enPreparacion(MetodoPago.WOMPI, AHORA.minus(Duration.ofDays(1)));

    casoDeUso().ejecutar(new MarcarListoParaRecogerComando(pedido.id(), "admin:1"));

    String cuerpo = correos.enviados().get(0).cuerpoHtml();
    assertTrue(cuerpo.contains("[pedido.listo_para_recoger.pagado]"), cuerpo);
    assertTrue(cuerpo.contains("[pedido.listo_para_recoger.plazo_con_reintegro|"), cuerpo);
  }

  @Test
  void unaSinVerificarNoSeMarcaLista() {
    Pedido pedido = recogidas.confirmada(MetodoPago.PAGO_EN_PUNTO, AHORA);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () -> casoDeUso().ejecutar(new MarcarListoParaRecogerComando(pedido.id(), "admin:1")));
    assertTrue(correos.enviados().isEmpty());
  }

  @Test
  void unEnvioADomicilioNoSeMarcaListoParaRecoger() {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 90),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta",
                    1,
                    Dinero.deCop(50_000),
                    BigDecimal.ZERO,
                    null,
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA,
            null,
            new Contacto("Ana Pérez", "3138816711"));
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:1", "verificado", AHORA);
    pedidos.guardar(pedido);

    assertThrows(
        EntregaNoAplicaException.class,
        () -> casoDeUso().ejecutar(new MarcarListoParaRecogerComando(pedido.id(), "admin:1")));
    assertEquals(EstadoPedido.EN_PREPARACION, pedido.estado());
  }
}
