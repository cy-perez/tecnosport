package co.tecnosport.api.application.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.RepositorioReintegrosFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** La bandeja de pagos sin pedido y su salida: la constancia de que el dinero volvió. */
class RegistrarReintegroDePagoSinPedidoTest {

  private static final Instant AHORA = Instant.parse("2026-10-04T12:00:00Z");

  private final RepositorioPagosFalso pagos = new RepositorioPagosFalso();
  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final RepositorioReintegrosFalso reintegros = new RepositorioReintegrosFalso();
  private final List<String> asuntos = new ArrayList<>();
  private final List<String> cuerpos = new ArrayList<>();

  private RegistrarReintegroDePagoSinPedido registrar() {
    return new RegistrarReintegroDePagoSinPedido(
        pagos,
        pedidos,
        reintegros,
        (destinatario, asunto, cuerpo) -> {
          asuntos.add(asunto);
          cuerpos.add(cuerpo);
        },
        new TextosDeCorreoFalso(),
        new RelojFalso(AHORA));
  }

  private ListarPagosSinPedido listar() {
    return new ListarPagosSinPedido(pagos, pedidos, reintegros);
  }

  private Pedido pedido() {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 9),
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
            MetodoPago.WOMPI,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.conPedido(pedido);
    return pedido;
  }

  private Pago pagoAprobado(Pedido pedido, int intento, boolean sinPedido) {
    Pago pago =
        Pago.crear(
            pedido.id(),
            new ReferenciaPago("TS-2026-000009-" + intento),
            MetodoPago.WOMPI,
            Dinero.deCop(61_200),
            AHORA);
    pago.aplicarEvento(new EventoPago("evt-" + intento, EstadoPago.APROBADO, AHORA));
    if (sinPedido) {
      pago.marcarSinPedidoQueLoEspere(AHORA);
    }
    pagos.guardar(pago);
    return pago;
  }

  @Test
  void devuelveElPagoEnteroConSuPropioMotivoYAvisaAlComprador() {
    Pedido pedido = pedido();
    Pago pago = pagoAprobado(pedido, 2, true);

    Reintegro reintegro =
        registrar()
            .ejecutar(
                new RegistrarReintegroDePagoSinPedidoComando(
                    pago.id(), MedioReintegro.WOMPI, "REF-9", "admin:1"));

    assertEquals(MotivoReintegro.PAGO_SIN_PEDIDO, reintegro.motivo());
    assertEquals(pago.id(), reintegro.origenId());
    assertEquals(pedido.id(), reintegro.pedidoId());
    assertEquals(Dinero.deCop(61_200), reintegro.monto());
    assertEquals(1, reintegros.guardados().size());
    assertTrue(asuntos.get(0).contains("pago.sin_pedido.asunto"), asuntos.toString());
    assertTrue(cuerpos.get(0).contains("pedido.cancelacion.con_reintegro"), cuerpos.toString());
  }

  @Test
  void porSistecreditoElCorreoHablaDeAnularElCredito() {
    Pago pago = pagoAprobado(pedido(), 2, true);

    registrar()
        .ejecutar(
            new RegistrarReintegroDePagoSinPedidoComando(
                pago.id(), MedioReintegro.SISTECREDITO, null, "admin:1"));

    assertTrue(cuerpos.get(0).contains("pedido.devuelto.reintegro_sistecredito"));
  }

  @Test
  void unPagoQueSiEsDeSuPedidoNoSeDevuelvePorAqui() {
    Pago pago = pagoAprobado(pedido(), 1, false);

    assertThrows(
        PagoConPedidoQueLoEsperaException.class,
        () ->
            registrar()
                .ejecutar(
                    new RegistrarReintegroDePagoSinPedidoComando(
                        pago.id(), MedioReintegro.WOMPI, null, "admin:1")));
    assertTrue(reintegros.guardados().isEmpty());
  }

  @Test
  void dosVecesNoSeDevuelveDosVeces() {
    Pago pago = pagoAprobado(pedido(), 2, true);
    RegistrarReintegroDePagoSinPedidoComando comando =
        new RegistrarReintegroDePagoSinPedidoComando(
            pago.id(), MedioReintegro.WOMPI, null, "admin:1");
    registrar().ejecutar(comando);

    assertThrows(PagoSinPedidoYaDevueltoException.class, () -> registrar().ejecutar(comando));
    assertEquals(1, reintegros.guardados().size());
  }

  @Test
  void laBandejaMuestraSoloLosQueFaltanPorDevolver() {
    Pedido pedido = pedido();
    pagoAprobado(pedido, 1, false);
    Pago pendiente = pagoAprobado(pedido, 2, true);
    Pago yaDevuelto = pagoAprobado(pedido, 3, true);
    registrar()
        .ejecutar(
            new RegistrarReintegroDePagoSinPedidoComando(
                yaDevuelto.id(), MedioReintegro.WOMPI, null, "admin:1"));

    List<PagoSinPedido> bandeja = listar().ejecutar();

    assertEquals(1, bandeja.size());
    assertEquals(pendiente.id(), bandeja.get(0).pago().id());
    assertEquals(pedido.id(), bandeja.get(0).pedido().id());
  }
}
