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
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Lo que a esta capa le toca demostrar: que el comprobante sale una sola vez, con los datos de la
 * compra, y solo para los pedidos que de verdad están en firme. Que el texto diga que no es una
 * factura se prueba donde vive el texto, en {@code TextosDeCorreoMessageSourceTest}.
 */
class EnviarComprobantesDeCompraTest {

  private static final Instant COMPRADO = Instant.parse("2026-09-18T15:00:00Z");
  private static final Instant AHORA = Instant.parse("2026-09-18T15:05:00Z");
  private static final String URL_ESTADO = "https://tecnosport.co/es/checkout/estado";

  private final RepositorioPedidosFalso pedidos = new RepositorioPedidosFalso();
  private final EnviadorDeCorreoFalso correos = new EnviadorDeCorreoFalso();

  private EnviarComprobantesDeCompra casoDeUso() {
    return new EnviarComprobantesDeCompra(
        pedidos, correos, new TextosDeCorreoFalso(), new RelojFalso(AHORA), URL_ESTADO);
  }

  private LineaPedido linea(String nombre, int cantidad, int precio) {
    return new LineaPedido(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Sku("TS-CAM-AZ-M"),
        nombre,
        cantidad,
        Dinero.deCop(BigDecimal.valueOf(precio)),
        BigDecimal.ZERO,
        null,
        UUID.randomUUID(),
        "Azul · M");
  }

  private Pedido pedidoEn(
      MetodoPago metodoPago, TipoEntrega tipoEntrega, EstadoPedido... transiciones) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, pedidos.todos().size() + 1),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(linea("Camiseta running Dry-Fit", 2, 89_900)),
            tipoEntrega,
            tipoEntrega == TipoEntrega.RETIRO_EN_PUNTO
                ? null
                : Direccion.sinBarrio(
                    "05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            metodoPago,
            "cliente@tecnosport.co",
            COMPRADO,
            null,
            new Contacto("Ana Pérez", "3138816711"));
    Instant momento = COMPRADO;
    for (EstadoPedido siguiente : transiciones) {
      momento = momento.plusSeconds(60);
      pedido.transicionar(siguiente, "sistema", "prueba", momento);
    }
    pedidos.guardar(pedido);
    return pedido;
  }

  private String cuerpoUnico() {
    assertEquals(1, correos.enviados().size(), "se esperaba un solo correo");
    return correos.enviados().getFirst().cuerpoHtml();
  }

  @Test
  void mandaElComprobanteDeUnContraentregaConfirmado() {
    Pedido pedido = pedidoEn(MetodoPago.CONTRAENTREGA, TipoEntrega.ENVIO_A_DOMICILIO);

    ResultadoComprobantes resultado = casoDeUso().ejecutar();

    assertEquals(new ResultadoComprobantes(1, 1, 0), resultado);
    assertEquals(pedido.correo(), correos.enviados().getFirst().destinatario());
    assertEquals("Ana Pérez", correos.enviados().get(0).nombre());
    String cuerpo = cuerpoUnico();
    assertTrue(cuerpo.contains(TextoDeCorreo.PEDIDO_COMPROBANTE_CUERPO.clave()), cuerpo);
    assertTrue(cuerpo.contains(TextoDeCorreo.PEDIDO_COMPROBANTE_VENDEDOR.clave()), cuerpo);
    assertTrue(cuerpo.contains(TextoDeCorreo.PEDIDO_COMPROBANTE_CIERRE.clave()), cuerpo);
    // La constancia de lo que se compró: con la talla y el color, no solo el nombre.
    assertTrue(cuerpo.contains("Camiseta running Dry-Fit (Azul · M)"), cuerpo);
  }

  /** Dos vueltas de la tarea, un solo comprobante: el reclamo es lo que lo impide. */
  @Test
  void noMandaDosVecesElMismoComprobante() {
    pedidoEn(MetodoPago.CONTRAENTREGA, TipoEntrega.ENVIO_A_DOMICILIO);
    EnviarComprobantesDeCompra casoDeUso = casoDeUso();

    casoDeUso.ejecutar();
    ResultadoComprobantes segunda = casoDeUso.ejecutar();

    assertEquals(new ResultadoComprobantes(0, 0, 0), segunda);
    assertEquals(1, correos.enviados().size());
  }

  /**
   * Y si otra instancia gana el reclamo, ésta no escribe nada aunque lo haya traído la consulta.
   */
  @Test
  void siOtraInstanciaGanaElReclamoNoMandaNada() {
    Pedido pedido = pedidoEn(MetodoPago.CONTRAENTREGA, TipoEntrega.ENVIO_A_DOMICILIO);
    pedidos.queOtroGaneElReclamoDe(pedido.id());

    ResultadoComprobantes resultado = casoDeUso().ejecutar();

    assertEquals(new ResultadoComprobantes(1, 0, 0), resultado);
    assertTrue(correos.enviados().isEmpty());
  }

  /**
   * Un envío que falla devuelve el reclamo, y la vuelta siguiente lo reintenta. Sin esto, la marca
   * quedaba puesta y ese comprador se quedaba sin comprobante <b>para siempre</b>, porque la
   * consulta ya no lo trae. Lo levantó una revisión adversarial.
   */
  @Test
  void siElEnvioFallaDevuelveElReclamoYLoReintenta() {
    pedidoEn(MetodoPago.CONTRAENTREGA, TipoEntrega.ENVIO_A_DOMICILIO);
    correos.hazQueFalle();
    EnviarComprobantesDeCompra casoDeUso = casoDeUso();

    ResultadoComprobantes primera = casoDeUso.ejecutar();

    assertEquals(new ResultadoComprobantes(1, 0, 1), primera);
    assertTrue(correos.enviados().isEmpty());

    // Y la vuelta siguiente lo vuelve a traer, que es lo que el reclamo devuelto hace posible.
    correos.queVuelvaAFuncionar();
    ResultadoComprobantes segunda = casoDeUso.ejecutar();

    assertEquals(new ResultadoComprobantes(1, 1, 0), segunda);
    assertEquals(1, correos.enviados().size());
  }

  /** Y un fallo no se lleva por delante a los que venían detrás en el mismo lote. */
  @Test
  void unFalloNoAbortaElRestoDelLote() {
    pedidoEn(MetodoPago.CONTRAENTREGA, TipoEntrega.ENVIO_A_DOMICILIO);
    pedidoEn(MetodoPago.CONTRAENTREGA, TipoEntrega.ENVIO_A_DOMICILIO);
    correos.hazQueFalleUnaVez();

    ResultadoComprobantes resultado = casoDeUso().ejecutar();

    assertEquals(new ResultadoComprobantes(2, 1, 1), resultado);
    assertEquals(1, correos.enviados().size());
  }

  /** Un pedido que todavía no es una compra no tiene nada que comprobar. */
  @Test
  void noMandaComprobanteDeUnPedidoConElPagoPendiente() {
    pedidoEn(MetodoPago.WOMPI, TipoEntrega.ENVIO_A_DOMICILIO);

    ResultadoComprobantes resultado = casoDeUso().ejecutar();

    assertEquals(new ResultadoComprobantes(0, 0, 0), resultado);
    assertTrue(correos.enviados().isEmpty());
  }

  /** Ni uno que se deshizo: ahí "gracias por tu compra" sería el mensaje equivocado. */
  @Test
  void noMandaComprobanteDeUnPedidoCancelado() {
    pedidoEn(MetodoPago.WOMPI, TipoEntrega.ENVIO_A_DOMICILIO, EstadoPedido.CANCELADO);

    ResultadoComprobantes resultado = casoDeUso().ejecutar();

    assertEquals(new ResultadoComprobantes(0, 0, 0), resultado);
    assertTrue(correos.enviados().isEmpty());
  }

  /**
   * El pago aprobado deja el pedido en {@code EN_PREPARACION} de una vez, sin pasar por la consulta
   * en {@code PAGADO}. Si el conjunto de estados no lo cubriera, la compra pagada en línea nunca
   * recibiría su comprobante — que es justo el camino más común.
   */
  @Test
  void mandaElComprobanteDeUnPagoEnLineaQueYaPasoAPreparacion() {
    pedidoEn(
        MetodoPago.WOMPI,
        TipoEntrega.ENVIO_A_DOMICILIO,
        EstadoPedido.PAGADO,
        EstadoPedido.EN_PREPARACION);

    ResultadoComprobantes resultado = casoDeUso().ejecutar();

    assertEquals(new ResultadoComprobantes(1, 1, 0), resultado);
    assertTrue(cuerpoUnico().contains(TextoDeCorreo.PEDIDO_COMPROBANTE_PAGO_EN_LINEA.clave()));
  }

  /** Los importes, agrupados como se escribe el peso, y el total con el flete dentro. */
  @Test
  void elComprobanteLlevaLasLineasYLosTotalesDeLaCompra() {
    pedidoEn(MetodoPago.CONTRAENTREGA, TipoEntrega.ENVIO_A_DOMICILIO);

    casoDeUso().ejecutar();

    String cuerpo = cuerpoUnico();
    // Dos unidades de 89.900: la línea lleva su propio subtotal, no el precio unitario. Sin
    // agrupar,
    // porque el doble no agrupa a propósito: cómo se escribe un importe es parte del idioma y se
    // prueba donde vive el idioma, en TextosDeCorreoMessageSourceTest.
    assertTrue(cuerpo.contains("2|Camiseta running Dry-Fit (Azul · M)|TS-CAM-AZ-M|179800"), cuerpo);
    assertTrue(
        cuerpo.contains(TextoDeCorreo.PEDIDO_COMPROBANTE_TOTALES.clave() + "|179800"), cuerpo);
  }

  /** Sin descuento el comprobante no habla de él: los totales de siempre. */
  @Test
  void sinDescuentoNoHayLineaDeDescuento() {
    pedidoEn(MetodoPago.CONTRAENTREGA, TipoEntrega.ENVIO_A_DOMICILIO);

    casoDeUso().ejecutar();

    String cuerpo = cuerpoUnico();
    assertFalse(
        cuerpo.contains(TextoDeCorreo.PEDIDO_COMPROBANTE_TOTALES_CON_DESCUENTO.clave()), cuerpo);
  }

  /**
   * ADR-0095: pagado sin pasarela, las líneas y el subtotal van al precio publicado y el descuento
   * aparece aparte, antes del total. Dos unidades anunciadas a 53.000 que se cobraron a 50.000:
   * 6.000 de descuento y 100.000 de total, sin flete.
   */
  @Test
  void conDescuentoLoMuestraEntreElEnvioYElTotal() {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-GOR-1"),
                    "Gorra",
                    2,
                    Dinero.deCop(50_000),
                    BigDecimal.ZERO,
                    null,
                    UUID.randomUUID(),
                    null,
                    Dinero.deCop(53_000))),
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.TRANSFERENCIA_MANUAL,
            "cliente@tecnosport.co",
            COMPRADO,
            null,
            new Contacto("Ana Pérez", "3138816711"));
    pedido.transicionar(EstadoPedido.PAGADO, "sistema", "prueba", COMPRADO.plusSeconds(60));
    pedidos.guardar(pedido);

    casoDeUso().ejecutar();

    String cuerpo = cuerpoUnico();
    assertTrue(cuerpo.contains("2|Gorra|TS-GOR-1|106000"), cuerpo);
    assertTrue(
        cuerpo.contains(
            TextoDeCorreo.PEDIDO_COMPROBANTE_TOTALES_CON_DESCUENTO.clave()
                + "|106000|0|6000|100000"),
        cuerpo);
    assertFalse(cuerpo.contains(TextoDeCorreo.PEDIDO_COMPROBANTE_TOTALES.clave() + "|"), cuerpo);
  }

  /** ADR-0073: el comprobante dice con qué transportadora va, que es la que el comprador eligió. */
  @Test
  void aDomicilioNombraLaTransportadoraDelFlete() {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(linea("Camiseta running Dry-Fit", 1, 89_900)),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            COMPRADO,
            new co.tecnosport.api.domain.envio.TarifaEnvio(
                "rate_1",
                "Inter Rapidísimo",
                "Standard",
                co.tecnosport.api.domain.compartido.Dinero.deCop(12_000),
                2,
                true,
                COMPRADO.plusSeconds(86_400)),
            new Contacto("Ana Pérez", "3138816711"));
    pedidos.guardar(pedido);

    casoDeUso().ejecutar();

    String cuerpo = cuerpoUnico();
    assertTrue(
        cuerpo.contains(
            TextoDeCorreo.PEDIDO_COMPROBANTE_ENTREGA_DOMICILIO_CON_TRANSPORTADORA.clave()
                + "|Inter Rapidísimo"),
        cuerpo);
  }

  /** Quien recoge en el punto no paga flete, y el comprobante no puede decir otra cosa. */
  @Test
  void elRetiroEnPuntoLlevaSuPropioTextoDeEntrega() {
    pedidoEn(MetodoPago.CONTRAENTREGA, TipoEntrega.RETIRO_EN_PUNTO);

    casoDeUso().ejecutar();

    String cuerpo = cuerpoUnico();
    assertTrue(cuerpo.contains(TextoDeCorreo.PEDIDO_COMPROBANTE_ENTREGA_RETIRO.clave()), cuerpo);
    assertFalse(
        cuerpo.contains(TextoDeCorreo.PEDIDO_COMPROBANTE_ENTREGA_DOMICILIO.clave()), cuerpo);
  }

  /** El enlace al estado lleva los dos parámetros con los que esa pantalla abre el pedido. */
  @Test
  void elComprobanteEnlazaAlEstadoDelPedido() {
    Pedido pedido = pedidoEn(MetodoPago.CONTRAENTREGA, TipoEntrega.ENVIO_A_DOMICILIO);

    casoDeUso().ejecutar();

    String cuerpo = cuerpoUnico();
    assertTrue(cuerpo.contains(URL_ESTADO + "?pedidoId=" + pedido.id()), cuerpo);
    assertTrue(cuerpo.contains("#correo=cliente%40tecnosport.co"), cuerpo);
    // Nunca en la consulta: lo que va antes del # llega al servidor y queda en sus registros.
    assertFalse(cuerpo.contains("&correo="), cuerpo);
  }
}
