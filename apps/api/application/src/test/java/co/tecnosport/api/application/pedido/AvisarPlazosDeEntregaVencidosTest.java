package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AvisarPlazosDeEntregaVencidosTest {

  /** Confirmado el 1 de agosto de 2026; el plazo se agotó al terminar el 31. */
  private static final Instant CONFIRMADO = Instant.parse("2026-08-01T15:00:00Z");

  private static final Instant PASADO_EL_PLAZO = Instant.parse("2026-09-10T12:00:00Z");

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  private AvisarPlazosDeEntregaVencidos casoDeUso(Instant ahora) {
    return new AvisarPlazosDeEntregaVencidos(
        pedidos, correos, new TextosDeCorreoFalso(), new RelojFalso(ahora));
  }

  private LineaPedido linea() {
    return new LineaPedido(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        1,
        Dinero.deCop(BigDecimal.valueOf(120_000)),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.webp",
        UUID.randomUUID());
  }

  /** Un pedido creado en {@code CONFIRMADO} y llevado hasta {@code estadoFinal}. */
  private Pedido pedidoEn(MetodoPago metodoPago, EstadoPedido... transiciones) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, pedidos.todos().size() + 1),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(linea()),
            TipoEntrega.ENVIO_A_DOMICILIO,
            new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            metodoPago,
            "cliente@tecnosport.co",
            CONFIRMADO);
    Instant momento = CONFIRMADO;
    for (EstadoPedido siguiente : transiciones) {
      momento = momento.plusSeconds(3600);
      pedido.transicionar(siguiente, "sistema", "prueba", momento);
    }
    pedidos.guardar(pedido);
    return pedido;
  }

  private static boolean contiene(
      List<EnviadorDeCorreoFalso.CorreoEnviado> enviados, TextoDeCorreo texto) {
    return enviados.stream().anyMatch(c -> c.cuerpoHtml().contains(texto.clave()));
  }

  @Test
  void avisaAlCompradorCuandoElPlazoSeAgoto() {
    Pedido pedido = pedidoEn(MetodoPago.NEQUI, EstadoPedido.PAGADO);

    ResultadoVigilanciaPlazos resultado = casoDeUso(PASADO_EL_PLAZO).ejecutar();

    assertEquals(new ResultadoVigilanciaPlazos(1, 1), resultado);
    assertEquals(1, correos.enviados().size());
    assertEquals(pedido.correo(), correos.enviados().get(0).destinatario());
    assertTrue(correos.enviados().get(0).asunto().contains(pedido.numeroPedido().valor()));
  }

  @Test
  void noCancelaNiCambiaElEstadoDelPedido() {
    // ADR-0028: terminar el contrato lo decide quien compró, no el vigilante.
    Pedido pedido = pedidoEn(MetodoPago.NEQUI, EstadoPedido.PAGADO);

    casoDeUso(PASADO_EL_PLAZO).ejecutar();

    assertEquals(EstadoPedido.PAGADO, pedido.estado());
    // Ni un registro de historial nuevo: el vigilante no transiciona nada.
    assertEquals(2, pedido.historial().size());
    assertEquals(List.of(pedido.id()), pedidos.reclamos());
  }

  @Test
  void noVuelveAEscribirEnLaSiguienteVuelta() {
    pedidoEn(MetodoPago.NEQUI, EstadoPedido.PAGADO);

    casoDeUso(PASADO_EL_PLAZO).ejecutar();
    ResultadoVigilanciaPlazos segunda =
        casoDeUso(PASADO_EL_PLAZO.plus(12, ChronoUnit.HOURS)).ejecutar();

    assertEquals(new ResultadoVigilanciaPlazos(0, 0), segunda);
    assertEquals(1, correos.enviados().size());
  }

  @Test
  void dentroDelPlazoNoAvisaNada() {
    pedidoEn(MetodoPago.NEQUI, EstadoPedido.PAGADO);

    ResultadoVigilanciaPlazos resultado =
        casoDeUso(CONFIRMADO.plus(20, ChronoUnit.DAYS)).ejecutar();

    assertEquals(new ResultadoVigilanciaPlazos(0, 0), resultado);
    assertTrue(correos.enviados().isEmpty());
  }

  @Test
  void unPedidoEntregadoNoIncumpleNada() {
    pedidoEn(
        MetodoPago.NEQUI,
        EstadoPedido.PAGADO,
        EstadoPedido.EN_PREPARACION,
        EstadoPedido.DESPACHADO,
        EstadoPedido.ENTREGADO);

    assertEquals(new ResultadoVigilanciaPlazos(0, 0), casoDeUso(PASADO_EL_PLAZO).ejecutar());
  }

  @Test
  void unPagoPendienteNoTienePlazoQueIncumplir() {
    // El plazo ni siquiera arrancó: no hay contrato perfeccionado que incumplir.
    pedidoEn(MetodoPago.NEQUI);

    assertEquals(new ResultadoVigilanciaPlazos(0, 0), casoDeUso(PASADO_EL_PLAZO).ejecutar());
    assertTrue(correos.enviados().isEmpty());
  }

  @Test
  void unDespachadoSinEntregarTambienIncumple() {
    // El plazo legal corre hasta la entrega, no hasta el despacho, y el texto de ese caso pide que
    // lo corrijan si el pedido ya llegó.
    pedidoEn(
        MetodoPago.NEQUI,
        EstadoPedido.PAGADO,
        EstadoPedido.EN_PREPARACION,
        EstadoPedido.DESPACHADO);

    casoDeUso(PASADO_EL_PLAZO).ejecutar();

    assertTrue(contiene(correos.enviados(), TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_EN_CAMINO));
  }

  /**
   * De {@code DESPACHADO} solo se sale hacia la entrega o hacia el rechazo en la entrega, así que
   * {@code CancelarPedido} lanzaría una transición inválida. Prometerle al comprador que se le
   * cancela sería prometer algo que el software no puede hacer: su párrafo lo dice de otra forma.
   */
  @Test
  void aUnDespachadoNoSeLePrometeUnaCancelacionQueElDominioNoPermite() {
    pedidoEn(
        MetodoPago.NEQUI,
        EstadoPedido.PAGADO,
        EstadoPedido.EN_PREPARACION,
        EstadoPedido.DESPACHADO);

    casoDeUso(PASADO_EL_PLAZO).ejecutar();

    assertFalse(contiene(correos.enviados(), TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_CON_DINERO));
    assertFalse(contiene(correos.enviados(), TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_SIN_COBRO));
  }

  /**
   * El reclamo es lo único que impide que dos instancias de Cloud Run le escriban dos veces al
   * mismo comprador. Si se pierde, no se escribe — y esta prueba falla si alguien vuelve a poner el
   * envío antes del reclamo.
   */
  @Test
  void siOtraInstanciaGanaElReclamoNoSeEscribeNada() {
    Pedido pedido = pedidoEn(MetodoPago.NEQUI, EstadoPedido.PAGADO);
    pedidos.queOtroGaneElReclamoDe(pedido.id());

    ResultadoVigilanciaPlazos resultado = casoDeUso(PASADO_EL_PLAZO).ejecutar();

    assertEquals(new ResultadoVigilanciaPlazos(1, 0), resultado);
    assertTrue(correos.enviados().isEmpty());
  }

  /** Y se reclama antes de escribir, no después: el orden es la garantía. */
  @Test
  void seReclamaCadaPedidoVencidoYSoloUnaVez() {
    Pedido vencido = pedidoEn(MetodoPago.NEQUI, EstadoPedido.PAGADO);
    pedidoEn(MetodoPago.NEQUI);

    casoDeUso(PASADO_EL_PLAZO).ejecutar();
    casoDeUso(PASADO_EL_PLAZO.plus(12, ChronoUnit.HOURS)).ejecutar();

    assertEquals(List.of(vencido.id()), pedidos.reclamos());
  }

  @Test
  void unPedidoSinDespacharNoDiceQueVaEnCamino() {
    pedidoEn(MetodoPago.NEQUI, EstadoPedido.PAGADO);

    casoDeUso(PASADO_EL_PLAZO).ejecutar();

    assertFalse(contiene(correos.enviados(), TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_EN_CAMINO));
  }

  @Test
  void siElDineroYaEntroElCorreoOfreceDevolverlo() {
    pedidoEn(MetodoPago.NEQUI, EstadoPedido.PAGADO);

    casoDeUso(PASADO_EL_PLAZO).ejecutar();

    assertTrue(contiene(correos.enviados(), TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_CON_DINERO));
  }

  @Test
  void enContraentregaNoSePrometeUnaDevolucionQueNoExiste() {
    // Se paga al recibir: sin entrega no entró un peso, y ofrecer un reintegro sería falso.
    pedidoEn(MetodoPago.CONTRAENTREGA, EstadoPedido.EN_PREPARACION);

    casoDeUso(PASADO_EL_PLAZO).ejecutar();

    assertEquals(1, correos.enviados().size());
    assertTrue(contiene(correos.enviados(), TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_SIN_COBRO));
  }

  @Test
  void avisaAVariosPedidosEnLaMismaVuelta() {
    pedidoEn(MetodoPago.NEQUI, EstadoPedido.PAGADO);
    pedidoEn(MetodoPago.CONTRAENTREGA, EstadoPedido.EN_PREPARACION);

    assertEquals(new ResultadoVigilanciaPlazos(2, 2), casoDeUso(PASADO_EL_PLAZO).ejecutar());
    assertEquals(2, correos.enviados().size());
  }

  @Test
  void sinPedidosVencidosNoHayCorreos() {
    assertEquals(new ResultadoVigilanciaPlazos(0, 0), casoDeUso(PASADO_EL_PLAZO).ejecutar());
    assertTrue(correos.enviados().isEmpty());
  }
}
