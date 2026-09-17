package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.HistorialPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmitirGuiaDePedidoTest {

  private static final Instant AHORA = Instant.parse("2026-09-16T23:41:59Z");

  private static final Direccion MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Calle 50 # 40-20", "Apto. 302");

  private RepositorioPedidosFalso pedidos;
  private RepositorioProductosFalso productos;
  private RepositorioEmisionesFalso emisiones;
  private CotizadorFalso cotizador;
  private EmisorDeGuiasFalso emisor;
  private EmitirGuiaDePedido caso;
  private Variante celular;

  @BeforeEach
  void preparar() {
    pedidos = new RepositorioPedidosFalso();
    productos = new RepositorioProductosFalso();
    emisiones = new RepositorioEmisionesFalso();
    cotizador = new CotizadorFalso();
    emisor = new EmisorDeGuiasFalso();

    Producto producto =
        Producto.crear(
            "Celular de prueba",
            new Slug("celular-de-prueba"),
            "Descripción",
            Marca.crear("TecnoSport"),
            Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA));
    celular =
        Variante.crear(
            new Sku("TS-CEL-1"),
            Dinero.deCop(120_000),
            new BigDecimal("0.19"),
            5,
            null,
            new Paquete(500, 20, 15, 5),
            List.of());
    producto.agregarVariante(celular);
    // Sin publicar: publicar exige imagen principal y aquí no hace falta ninguna. Lo que se
    // despacha es el paquete de la variante, no la vitrina.
    productos.conProductos(producto);

    ArmadorDeBultos armador = new ArmadorDeBultos(productos);
    caso =
        new EmitirGuiaDePedido(
            pedidos,
            emisiones,
            armador,
            new CotizarEnvio(armador, cotizador, () -> AHORA),
            emisor,
            () -> AHORA);
  }

  private Pedido pedido(EstadoPedido estado, TipoEntrega tipoEntrega, int cantidad) {
    Direccion direccion = tipoEntrega == TipoEntrega.ENVIO_A_DOMICILIO ? MEDELLIN : null;
    TarifaEnvio congelada =
        tipoEntrega == TipoEntrega.ENVIO_A_DOMICILIO
            ? new TarifaEnvio(
                "tarifa-de-ayer",
                "Envía",
                "Terrestre",
                Dinero.deCop(7_850),
                1,
                false,
                AHORA.minusSeconds(3_600))
            : null;
    Pedido pedido =
        new Pedido(
            GeneradorIdentificador.nuevo(),
            NumeroPedido.de(2026, 1),
            null,
            new CorreoElectronico("comprador@example.com"),
            List.of(
                new LineaPedido(
                    GeneradorIdentificador.nuevo(),
                    celular.id(),
                    new Sku("TS-CEL-1"),
                    "Celular de prueba",
                    cantidad,
                    Dinero.deCop(120_000),
                    new BigDecimal("0.19"),
                    null,
                    GeneradorIdentificador.nuevo())),
            tipoEntrega,
            direccion,
            MetodoPago.TARJETA,
            estado,
            List.of(
                new HistorialPedido(
                    GeneradorIdentificador.nuevo(), estado, AHORA, "prueba", "puesto a mano")),
            AHORA,
            null,
            congelada,
            new Contacto("Comprador de prueba", "3001234567"));
    pedidos.guardar(pedido);
    return pedido;
  }

  private static TarifaEnvio tarifaDeHoy() {
    return new TarifaEnvio(
        "rate-de-hoy",
        "Servientrega",
        "Standard",
        Dinero.deCop(8_200),
        2,
        false,
        AHORA.plusSeconds(86_400));
  }

  @Test
  void emite_y_deja_la_emision_en_curso_sin_mover_el_pedido() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("177d1939")));

    EmisionDeGuia emision = caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"));

    assertEquals(EstadoEmision.EN_CURSO, emision.estado());
    assertEquals(List.of("177d1939"), emision.enviosEnPlataforma());
    assertEquals("rate-de-hoy", emision.idTarifa());
    assertSame(emision, emisiones.todas().getFirst());
    // Lo que de verdad importa: el pedido no se movió. Si la guía muere, no hay nada que devolver.
    assertEquals(
        EstadoPedido.EN_PREPARACION, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  /**
   * La tarifa que el pedido congeló vale 24 horas y aquí ya venció. Emitir con ella devolvería un
   * rechazo de la plataforma; recotizar es lo que hace que el despacho funcione al tercer día.
   */
  @Test
  void recotiza_en_vez_de_usar_la_tarifa_congelada_del_pedido() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("177d1939")));

    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"));

    assertEquals("tarifa-de-ayer", pedido.tarifaEnvio().orElseThrow().idTarifa());
    assertEquals("rate-de-hoy", emisor.solicitudes().getFirst().idTarifa());
    assertEquals("Servientrega", emisiones.todas().getFirst().transportadora());
  }

  /**
   * El contenido va uno por bulto y en el orden de los bultos, porque la plataforma empareja cada
   * paquete del envío con el bulto de la cotización por posición: un desfase declararía el
   * contenido de una caja en otra, y la guía se emitiría igual.
   */
  @Test
  void manda_un_contenido_declarado_por_bulto_y_en_orden() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 2);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("8bf880c9", "da585a66")));

    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"));

    SolicitudDeEmision solicitud = emisor.solicitudes().getFirst();
    assertEquals(
        List.of("Electrónica y accesorios", "Electrónica y accesorios"),
        solicitud.contenidoPorBulto());
    assertEquals(2, cotizador.ultima().bultos().size());
  }

  @Test
  void las_indicaciones_del_comprador_viajan_con_la_solicitud() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("177d1939")));

    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"));

    assertEquals("Apto. 302", emisor.solicitudes().getFirst().indicaciones().orElseThrow());
  }

  /** El recaudo cambia qué transportadoras responden, así que se pide igual que al crear. */
  @Test
  void un_pedido_contraentrega_recotiza_con_recaudo() {
    Pedido pedido =
        new Pedido(
            GeneradorIdentificador.nuevo(),
            NumeroPedido.de(2026, 2),
            null,
            new CorreoElectronico("comprador@example.com"),
            List.of(
                new LineaPedido(
                    GeneradorIdentificador.nuevo(),
                    celular.id(),
                    new Sku("TS-CEL-1"),
                    "Celular de prueba",
                    1,
                    Dinero.deCop(120_000),
                    new BigDecimal("0.19"),
                    null,
                    GeneradorIdentificador.nuevo())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            EstadoPedido.EN_PREPARACION,
            List.of(
                new HistorialPedido(
                    GeneradorIdentificador.nuevo(),
                    EstadoPedido.EN_PREPARACION,
                    AHORA,
                    "prueba",
                    "puesto a mano")),
            AHORA,
            null,
            tarifaDeHoy(),
            new Contacto("Comprador", "3001234567"));
    pedidos.guardar(pedido);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("177d1939")));

    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"));

    assertTrue(cotizador.ultima().conRecaudo());
  }

  /**
   * La puerta que de verdad cuesta plata: la plataforma cobra al crear, así que dos solicitudes son
   * dos cobros y el segundo juego de guías no lo usa nadie.
   */
  @Test
  void no_emite_dos_veces_el_mismo_pedido() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("177d1939")));
    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"));

    assertThrows(
        EmisionYaEnCursoException.class,
        () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));
    assertEquals(1, emisor.solicitudes().size());
    assertEquals(1, emisiones.todas().size());
  }

  /**
   * Una emisión que ya se resolvió no bloquea: un fallo se reintenta, y la plataforma reembolsó.
   */
  @Test
  void una_emision_fallida_deja_volver_a_intentarlo() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("e47c61d3")));
    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"))
        .resolver(EstadoEmision.FALLIDA, "CARRIER_RESPONSE_ERROR", AHORA.plusSeconds(260));

    emisor.responde(new ResultadoEmision.Aceptada(List.of("177d1939")));
    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"));

    assertEquals(2, emisiones.todas().size());
  }

  @Test
  void un_retiro_en_punto_no_lleva_guia() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.RETIRO_EN_PUNTO, 1);

    assertThrows(
        EmisionNoAplicableException.class,
        () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));
    assertTrue(emisor.solicitudes().isEmpty());
  }

  @Test
  void un_pedido_que_no_esta_en_preparacion_no_se_emite() {
    Pedido pedido = pedido(EstadoPedido.PAGO_PENDIENTE, TipoEntrega.ENVIO_A_DOMICILIO, 1);

    assertThrows(
        EmisionNoAplicableException.class,
        () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));
    assertTrue(emisor.solicitudes().isEmpty());
  }

  /** Un rechazo no cuesta saldo y no deja emisión: no hay nada que releer ni que reembolsar. */
  @Test
  void un_rechazo_de_la_plataforma_no_deja_emision_guardada() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(
        new ResultadoEmision.Rechazada(
            ResultadoEmision.Motivo.TARIFA_RECHAZADA, "la tarifa ya no existe"));

    EmisionRechazadaException error =
        assertThrows(
            EmisionRechazadaException.class,
            () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));

    assertEquals(ResultadoEmision.Motivo.TARIFA_RECHAZADA, error.motivo());
    assertTrue(emisiones.todas().isEmpty());
  }

  /** Sin tarifa no se emite, y el pedido se queda donde estaba. */
  @Test
  void sin_tarifa_no_hay_emision() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver();

    assertThrows(
        EnvioSinCoberturaException.class,
        () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));
    assertTrue(emisor.solicitudes().isEmpty());
    assertTrue(emisiones.todas().isEmpty());
  }

  @Test
  void un_pedido_que_no_existe_se_rechaza() {
    UUID inexistente = GeneradorIdentificador.nuevo();

    assertThrows(
        co.tecnosport.api.application.pedido.PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new EmitirGuiaDePedidoComando(inexistente, "admin:1")));
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  private static final class CotizadorFalso implements CotizadorEnvio {

    private List<TarifaEnvio> tarifas = List.of();
    private CotizacionEnvio ultima;

    void devolver(TarifaEnvio... tarifas) {
      this.tarifas = List.of(tarifas);
    }

    CotizacionEnvio ultima() {
      return ultima;
    }

    @Override
    public ResultadoCotizacion cotizar(CotizacionEnvio cotizacion) {
      this.ultima = cotizacion;
      return tarifas.isEmpty()
          ? new ResultadoCotizacion.SinCobertura()
          : new ResultadoCotizacion.ConTarifas(tarifas);
    }
  }
}
