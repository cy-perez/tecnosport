package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.EnTransaccionPropiaFalsa;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.TextosDeCorreoFalso;
import co.tecnosport.api.application.pedido.DespacharPedido;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResolverEmisionesEnCursoTest {

  private static final Instant AHORA = Instant.parse("2026-09-16T23:45:00Z");
  private static final Direccion MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Calle 50 # 40-20", null);

  private RepositorioPedidosFalso pedidos;
  private RepositorioEnviosFalso envios;
  private RepositorioEmisionesFalso emisiones;
  private EmisorDeGuiasFalso emisor;
  private CorreosFalsos correos;
  private ResolverEmisionesEnCurso caso;

  @BeforeEach
  void preparar() {
    pedidos = new RepositorioPedidosFalso();
    envios = new RepositorioEnviosFalso();
    emisiones = new RepositorioEmisionesFalso();
    emisor = new EmisorDeGuiasFalso();
    correos = new CorreosFalsos();
    caso =
        new ResolverEmisionesEnCurso(
            emisiones,
            emisor,
            new DespacharPedido(
                pedidos,
                envios,
                correos,
                new TextosDeCorreoFalso(),
                new RelojFalso(AHORA),
                "https://tecnosport.co/es/checkout/estado"),
            new EnTransaccionPropiaFalsa(),
            new RelojFalso(AHORA),
            20);
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
                    new Sku("TS-CEL-1"),
                    "Celular de prueba",
                    1,
                    Dinero.deCop(120_000),
                    new BigDecimal("0.19"),
                    null,
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  private EmisionDeGuia emisionDe(Pedido pedido, String... envios) {
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Servientrega", "rate-de-hoy", "admin:test", AHORA);
    emision.aceptada(List.of(envios), AHORA);
    emisiones.guardar(emision);
    return emision;
  }

  private static LecturaDeEnvioEmitido.Emitido guia(String numero, long costo) {
    return new LecturaDeEnvioEmitido.Emitido(
        "servientrega",
        numero,
        Dinero.deCop(costo),
        "https://sb-pro.skydropx.com/s/s?id=" + numero);
  }

  @Test
  void con_la_guia_viva_despacha_el_pedido_y_cierra_la_emision() {
    Pedido pedido = pedidoEnPreparacion();
    EmisionDeGuia emision = emisionDe(pedido, "177d1939");
    emisor.paraElEnvio("177d1939", guia("2269401762", 8_200));

    ResultadoResolucionEmisiones resultado = caso.ejecutar();

    assertEquals(new ResultadoResolucionEmisiones(1, 1, 0, 0, 0, 0, 0, 0, List.of()), resultado);
    assertEquals(EstadoEmision.EMITIDA, emision.estado());
    assertEquals(EstadoPedido.DESPACHADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());

    Envio envio = envios.buscarPorPedidoId(pedido.id()).orElseThrow();
    GuiaEnvio unica = envio.guias().getFirst();
    assertEquals("Servientrega", unica.transportadora());
    assertEquals("servientrega", unica.codigoTransportadora().orElseThrow());
    assertEquals("2269401762", unica.numero());
    assertEquals(Dinero.deCop(8_200), unica.costo());
    assertTrue(unica.urlEtiqueta().isPresent());
    assertEquals(1, correos.enviados());
  }

  /**
   * Medido el 16 de septiembre de 2026: una tarifa multienvío de 16.400 produjo dos envíos de 8.200
   * (docs/13 §6.10). El costo del despacho es la suma, y guardar el total de la tarifa en cada guía
   * lo duplicaría.
   */
  @Test
  void el_multienvio_despacha_con_una_guia_por_envio_y_su_propio_costo() {
    Pedido pedido = pedidoEnPreparacion();
    emisionDe(pedido, "8bf880c9", "da585a66");
    emisor.paraElEnvio("8bf880c9", guia("2269401763", 8_200));
    emisor.paraElEnvio("da585a66", guia("2269401764", 8_200));

    caso.ejecutar();

    Envio envio = envios.buscarPorPedidoId(pedido.id()).orElseThrow();
    assertEquals(2, envio.guias().size());
    assertEquals(Dinero.deCop(16_400), envio.costoEnvio());
  }

  /**
   * Un envío que sigue en curso frena la emisión entera aunque su hermano ya tenga guía: {@code
   * Envio} no admite que le agreguen guías después, así que despachar con la mitad dejaría al
   * comprador esperando un paquete que no existe.
   */
  @Test
  void un_envio_todavia_en_curso_deja_la_emision_abierta() {
    Pedido pedido = pedidoEnPreparacion();
    EmisionDeGuia emision = emisionDe(pedido, "8bf880c9", "da585a66");
    emisor.paraElEnvio("8bf880c9", guia("2269401763", 8_200));
    emisor.paraElEnvio("da585a66", new LecturaDeEnvioEmitido.Sigue());

    ResultadoResolucionEmisiones resultado = caso.ejecutar();

    assertEquals(new ResultadoResolucionEmisiones(1, 0, 0, 0, 1, 0, 0, 0, List.of()), resultado);
    assertEquals(EstadoEmision.EN_CURSO, emision.estado());
    assertTrue(envios.buscarPorPedidoId(pedido.id()).isEmpty());
    assertEquals(EstadoPedido.EN_PREPARACION, pedido.estado());
  }

  /**
   * Medido: la guía de Coordinadora murió a los cuatro minutos con {@code CARRIER_RESPONSE_ERROR} y
   * el saldo volvió entero. El pedido nunca se movió, así que no hay nada que devolver a ninguna
   * cola: sigue en preparación, listo para reintentarlo.
   */
  @Test
  void una_emision_muerta_cierra_fallida_y_no_toca_el_pedido() {
    Pedido pedido = pedidoEnPreparacion();
    EmisionDeGuia emision = emisionDe(pedido, "e47c61d3");
    emisor.paraElEnvio(
        "e47c61d3", new LecturaDeEnvioEmitido.Fallido("CARRIER_RESPONSE_ERROR: código duplicado"));

    ResultadoResolucionEmisiones resultado = caso.ejecutar();

    assertEquals(new ResultadoResolucionEmisiones(1, 0, 1, 0, 0, 0, 0, 0, List.of()), resultado);
    assertEquals(EstadoEmision.FALLIDA, emision.estado());
    assertTrue(emision.detalle().orElseThrow().contains("CARRIER_RESPONSE_ERROR"));
    assertEquals(EstadoPedido.EN_PREPARACION, pedido.estado());
    assertTrue(envios.buscarPorPedidoId(pedido.id()).isEmpty());
    assertEquals(0, correos.enviados());
  }

  /**
   * El caso que ningún ADR contemplaba: en multienvío los envíos son independientes y uno puede
   * morir solo. Queda una guía viva, pagada, que alguien tiene que cancelar o usar — y su número
   * tiene que estar escrito, no perdido en un registro.
   */
  @Test
  void un_fallo_parcial_no_despacha_y_deja_anotadas_las_guias_vivas() {
    Pedido pedido = pedidoEnPreparacion();
    EmisionDeGuia emision = emisionDe(pedido, "8bf880c9", "da585a66");
    emisor.paraElEnvio("8bf880c9", guia("2269401763", 8_200));
    emisor.paraElEnvio("da585a66", new LecturaDeEnvioEmitido.Fallido("500 at LABEL_NUMBER"));

    ResultadoResolucionEmisiones resultado = caso.ejecutar();

    assertEquals(new ResultadoResolucionEmisiones(1, 0, 0, 1, 0, 0, 0, 0, List.of()), resultado);
    assertEquals(EstadoEmision.PARCIAL, emision.estado());
    assertTrue(emision.detalle().orElseThrow().contains("2269401763"));
    assertEquals(EstadoPedido.EN_PREPARACION, pedido.estado());
    assertTrue(envios.buscarPorPedidoId(pedido.id()).isEmpty());
  }

  /**
   * "No pudimos preguntar" no es "todavía no hay guía". Las dos dejan la emisión abierta, y por eso
   * se cuentan aparte: si el registro solo dijera "en curso", un proveedor caído durante horas se
   * vería igual que una transportadora lenta.
   */
  @Test
  void una_lectura_que_fallo_no_concluye_nada_y_se_cuenta_aparte() {
    Pedido pedido = pedidoEnPreparacion();
    EmisionDeGuia emision = emisionDe(pedido, "177d1939");
    emisor.paraElEnvio("177d1939", new LecturaDeEnvioEmitido.NoSeSabe());

    ResultadoResolucionEmisiones resultado = caso.ejecutar();

    assertEquals(new ResultadoResolucionEmisiones(1, 0, 0, 0, 0, 1, 0, 0, List.of()), resultado);
    assertEquals(EstadoEmision.EN_CURSO, emision.estado());
    assertEquals(EstadoPedido.EN_PREPARACION, pedido.estado());
  }

  /** Una emisión que ya se resolvió no se vuelve a mirar. */
  @Test
  void no_relee_emisiones_ya_resueltas() {
    Pedido pedido = pedidoEnPreparacion();
    emisionDe(pedido, "177d1939").resolver(EstadoEmision.FALLIDA, "ya estaba", AHORA);

    assertEquals(
        new ResultadoResolucionEmisiones(0, 0, 0, 0, 0, 0, 0, 0, List.of()), caso.ejecutar());
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  private static final class CorreosFalsos implements EnviadorDeCorreo {

    private final List<String> destinatarios = new ArrayList<>();

    @Override
    public void enviar(CorreoElectronico destinatario, String asunto, String cuerpo) {
      destinatarios.add(destinatario.valor());
    }

    int enviados() {
      return destinatarios.size();
    }
  }
}
