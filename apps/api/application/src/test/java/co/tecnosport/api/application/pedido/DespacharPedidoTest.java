package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DespacharPedidoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private static final String URL_ESTADO = "https://tecnosport.co/es/checkout/estado";

  private RepositorioPedidosFalso pedidos;
  private RepositorioEnviosFalso envios;
  private EnviadorDeCorreoFalso correos;

  private DespacharPedido crear() {
    pedidos = new RepositorioPedidosFalso();
    envios = new RepositorioEnviosFalso();
    correos = new EnviadorDeCorreoFalso();
    return new DespacharPedido(
        pedidos, envios, correos, new TextosDeCorreoFalso(), new RelojFalso(AHORA), URL_ESTADO);
  }

  private Pedido pedidoEnPreparacion() {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  private DespacharPedidoComando comando(UUID pedidoId) {
    return new DespacharPedidoComando(
        pedidoId,
        List.of(new GuiaDespachada("Servientrega", "SE123456", Dinero.deCop(15_000))),
        "admin:test");
  }

  @Test
  void despachaUnPedidoEnPreparacion() {
    DespacharPedido caso = crear();
    Pedido pedido = pedidoEnPreparacion();

    Pedido despachado = caso.ejecutar(comando(pedido.id()));

    assertEquals(EstadoPedido.DESPACHADO, despachado.estado());
    assertEquals(3, despachado.historial().size());
    assertEquals(1, envios.guardados().size());
    Envio envio = envios.guardados().get(0);
    assertEquals(pedido.id(), envio.pedidoId());
    assertEquals(1, envio.guias().size());
    assertEquals("Servientrega", envio.guias().getFirst().transportadora());
    assertEquals("SE123456", envio.guias().getFirst().numero());
    assertEquals(Dinero.deCop(15_000), envio.costoEnvio());
  }

  /**
   * El caso de adr/0031: dos variantes, dos paquetes, dos guías. El costo del despacho es la suma,
   * y el historial nombra las dos transportadoras.
   */
  @Test
  void despachaUnPedidoConDosGuias() {
    DespacharPedido caso = crear();
    Pedido pedido = pedidoEnPreparacion();

    caso.ejecutar(
        new DespacharPedidoComando(
            pedido.id(),
            List.of(
                new GuiaDespachada("Servientrega", "SE123456", Dinero.deCop(8_200)),
                new GuiaDespachada("Coordinadora", "CO987", Dinero.deCop(5_991))),
            "admin:test"));

    Envio envio = envios.guardados().get(0);
    assertEquals(2, envio.guias().size());
    assertEquals(Dinero.deCop(14_191), envio.costoEnvio());
    assertTrue(
        pedido.historial().getLast().motivo().contains("Servientrega, Coordinadora"),
        "El historial tiene que decir con quién salió cada paquete.");
  }

  @Test
  void unPedidoInexistenteLanzaPedidoNoEncontrado() {
    DespacharPedido caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class, () -> caso.ejecutar(comando(UUID.randomUUID())));
    assertTrue(correos.enviados().isEmpty());
  }

  @Test
  void unPedidoQueNoEstaEnPreparacionNoSePuedeDespacharYNoDejaUnEnvioHuerfano() {
    DespacharPedido caso = crear();
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 2),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.guardar(pedido);

    assertThrows(
        TransicionDeEstadoInvalidaException.class, () -> caso.ejecutar(comando(pedido.id())));
    assertTrue(envios.guardados().isEmpty());
    // Ni envío huérfano, ni correo huérfano: avisar de un despacho que no ocurrió es peor que no
    // avisar, porque el comprador se queda esperando un paquete que nadie entregó a nadie.
    assertTrue(correos.enviados().isEmpty());
  }

  @Test
  void avisaAlCompradorConLaTransportadoraLaGuiaYElEnlaceASuPedido() {
    DespacharPedido caso = crear();
    Pedido pedido = pedidoEnPreparacion();

    caso.ejecutar(comando(pedido.id()));

    assertEquals(1, correos.enviados().size());
    EnviadorDeCorreoFalso.CorreoEnviado enviado = correos.enviados().get(0);
    assertEquals(pedido.correo(), enviado.destinatario());
    // La llave y sus datos, no la prosa: que la frase nombre la transportadora con sus tildes se
    // afirma donde vive el texto, en TextosDeCorreoMessageSourceTest.
    assertEquals(
        "["
            + TextoDeCorreo.PEDIDO_DESPACHO_ASUNTO.clave()
            + "|"
            + pedido.numeroPedido().valor()
            + "]",
        enviado.asunto());
    assertTrue(
        enviado.cuerpoHtml().startsWith("[" + TextoDeCorreo.PEDIDO_DESPACHO_CUERPO.clave() + "|"));
    assertTrue(enviado.cuerpoHtml().contains("|Servientrega|SE123456|"));
  }

  /**
   * La afirmacion en negativo, y es la que de verdad protege algo. {@link DespacharPedido} es la
   * unica clase del sistema que tiene las dos cifras a la vez en el mismo metodo: {@code
   * comando.costoEnvio()} es lo que la transportadora nos cobra —lo teclea un administrador en el
   * panel— y {@code pedido.costoEnvio()} es lo que pago el comprador. Hoy el correo lleva el
   * segundo y ninguno mas.
   *
   * <p>Sin esta prueba, agregar un quinto argumento con el costo real pasaba todo lo demas: el
   * {@code contains} de la prueba de arriba, la de rastreo —que solo mira "rastre" y "evento"— y la
   * comprobacion de arranque de los textos, porque {@code RELLENO} ya tiene cuatro. El margen del
   * negocio habria acabado en la bandeja de entrada del comprador, en un correo que se reenvia y se
   * guarda para siempre.
   */
  @Test
  void elCorreoDeDespachoNoLlevaElCostoRealDelFlete() {
    DespacharPedido caso = crear();
    Pedido pedido = pedidoEnPreparacion();

    caso.ejecutar(comando(pedido.id()));

    EnviadorDeCorreoFalso.CorreoEnviado enviado = correos.enviados().get(0);
    // 15.000 es el costo que el comando registra para la transportadora. No puede salir ni en el
    // asunto ni en el cuerpo, con ningun formato.
    assertFalse(enviado.cuerpoHtml().contains("15000"), enviado.cuerpoHtml());
    assertFalse(enviado.cuerpoHtml().contains("15.000"), enviado.cuerpoHtml());
    assertFalse(enviado.asunto().contains("15000"), enviado.asunto());
  }

  @Test
  void elEnlaceDelCorreoLlevaElIdYElCorreoCodificados() {
    DespacharPedido caso = crear();
    Pedido pedido = pedidoEnPreparacion();

    caso.ejecutar(comando(pedido.id()));

    String cuerpo = correos.enviados().get(0).cuerpoHtml();
    // La pantalla de estado no pide sesión: se abre con estos dos datos y ningunos más. La arroba
    // va codificada — sin eso, el enlace se rompe en los clientes de correo que lo reescriben.
    assertTrue(
        cuerpo.contains(
            URL_ESTADO + "?pedidoId=" + pedido.id() + "&correo=cliente%40tecnosport.co"),
        cuerpo);
  }
}
