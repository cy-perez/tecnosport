package co.tecnosport.api.application.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.EnTransaccionPropiaFalsa;
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
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Calle 50 # 40-20", "Apto. 302");

  private RepositorioPedidosFalso pedidos;
  private RepositorioProductosFalso productos;
  private RepositorioEmisionesFalso emisiones;
  private CotizadorFalso cotizador;
  private EmisorDeGuiasFalso emisor;
  private EnTransaccionPropiaFalsa transacciones;
  private EmitirGuiaDePedido caso;
  private Variante celular;

  @BeforeEach
  void preparar() {
    pedidos = new RepositorioPedidosFalso();
    productos = new RepositorioProductosFalso();
    emisiones = new RepositorioEmisionesFalso();
    cotizador = new CotizadorFalso();
    emisor = new EmisorDeGuiasFalso();
    transacciones = new EnTransaccionPropiaFalsa();

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

    ArmadorDeBultos armador = new ArmadorDeBultos(productos, Dinero.deCop(10_000));
    caso =
        new EmitirGuiaDePedido(
            pedidos,
            emisiones,
            armador,
            new CotizarEnvio(armador, cotizador, () -> AHORA),
            emisor,
            transacciones,
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
                    // Distinto del catálogo a propósito: es lo que el comprador pagó, y es lo que
                    // tiene que viajar como valor declarado.
                    Dinero.deCop(99_000),
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

  /** La segunda transportadora, para poder comprobar que el reintento cambia de una a otra. */
  private static TarifaEnvio otraTarifa() {
    return new TarifaEnvio(
        "rate-alternativa",
        "Envía",
        "Terrestre",
        Dinero.deCop(9_900),
        3,
        false,
        AHORA.plusSeconds(86_400));
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
   * Una emisión resuelta no bloquea —un fallo se reintenta y la plataforma reembolsó—, pero el
   * reintento <strong>no repite transportadora</strong>. El fallo más caro que se ha medido es
   * determinista: el contador de remisiones de Coordinadora está atascado y falla siempre, y es la
   * tarifa más barata, o sea la que el selector elige sola. Sin excluirla, cada reintento repetiría
   * el mismo fracaso.
   */
  @Test
  void el_reintento_no_vuelve_a_elegir_la_transportadora_que_fallo() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy(), otraTarifa());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("e47c61d3")));
    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"))
        .resolver(EstadoEmision.FALLIDA, "CARRIER_RESPONSE_ERROR", AHORA.plusSeconds(260));

    emisor.responde(new ResultadoEmision.Aceptada(List.of("177d1939")));
    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"));

    assertEquals(2, emisiones.todas().size());
    assertEquals("Servientrega", emisiones.todas().get(0).transportadora());
    assertEquals("Envía", emisiones.todas().get(1).transportadora());
  }

  /** Si la única que queda es la que ya falló, no hay con qué emitir y se dice así. */
  @Test
  void si_no_queda_transportadora_sin_intentar_no_hay_cobertura() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("e47c61d3")));
    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"))
        .resolver(EstadoEmision.FALLIDA, "CARRIER_RESPONSE_ERROR", AHORA.plusSeconds(260));

    assertThrows(
        EnvioSinCoberturaException.class,
        () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));
  }

  /**
   * El valor declarado es el <strong>precio congelado del pedido</strong>, no el del catálogo: es
   * el monto que la transportadora paga si pierde el paquete, y tiene que coincidir con la factura
   * contra la que se reclama. Aquí el catálogo vale 120.000 y la línea del pedido, 99.000.
   */
  @Test
  void el_valor_declarado_es_el_que_pago_el_comprador() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("177d1939")));

    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"));

    assertEquals(Dinero.deCop(99_000), cotizador.ultima().bultos().getFirst().valorDeclarado());
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

  /**
   * Un rechazo <strong>deja fila</strong>. Antes no la dejaba, y esa prueba fijaba como deseado lo
   * que era el defecto: sin fila no queda escrito el {@code idTarifa}, que es lo único que recupera
   * un envío que la plataforma pudo haber creado.
   */
  @Test
  void un_rechazo_de_la_plataforma_deja_la_emision_anotada_como_fallida() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(
        new ResultadoEmision.Rechazada(
            ResultadoEmision.Motivo.DATOS_RECHAZADOS, "la tarifa ya no resuelve"));

    EmisionRechazadaException error =
        assertThrows(
            EmisionRechazadaException.class,
            () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));

    assertEquals(ResultadoEmision.Motivo.DATOS_RECHAZADOS, error.motivo());
    EmisionDeGuia anotada = emisiones.todas().getFirst();
    assertEquals(EstadoEmision.FALLIDA, anotada.estado());
    assertEquals("rate-de-hoy", anotada.idTarifa());
    assertFalse(anotada.estado().abierta());
  }

  /**
   * El caso caro: el proveedor no contestó y <strong>pudo haber cobrado igual</strong> —el {@code
   * 408} medido creó la guía y descontó 19.465—. No se da por fallida, porque reintentar sobre eso
   * paga dos veces; queda indeterminada, bloqueando, con la tarifa escrita para poder recuperarla.
   */
  @Test
  void si_el_proveedor_no_contesto_la_emision_queda_indeterminada_y_bloquea() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(
        new ResultadoEmision.Rechazada(
            ResultadoEmision.Motivo.PROVEEDOR_NO_DISPONIBLE, "se agotaron los intentos"));

    assertThrows(
        EmisionRechazadaException.class,
        () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));

    EmisionDeGuia anotada = emisiones.todas().getFirst();
    assertEquals(EstadoEmision.INDETERMINADA, anotada.estado());
    assertEquals("rate-de-hoy", anotada.idTarifa());
    assertTrue(anotada.estado().abierta());
    // Y por eso el pedido no admite otra: la siguiente pagaría encima de algo que pudo cobrarse.
    assertThrows(
        EmisionYaEnCursoException.class,
        () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));
  }

  /**
   * La fila se escribe antes de la llamada y el desenlace después: dos transacciones propias, no
   * una. Si fueran la misma, entre el cobro y la respuesta no habría nada escrito.
   */
  @Test
  void la_emision_se_guarda_antes_de_llamar_y_otra_vez_despues() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy());
    emisor.responde(
        new ResultadoEmision.Rechazada(
            ResultadoEmision.Motivo.PROVEEDOR_NO_DISPONIBLE, "se cayó la llamada"));

    assertThrows(
        EmisionRechazadaException.class,
        () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));

    assertEquals(2, transacciones.veces());
  }

  /**
   * La caché de idempotencia de Skydropx devuelve los mismos envíos para la misma tarifa durante 96
   * horas, y la cotización se deduplica por contenido. Un reintento puede recibir de vuelta los
   * envíos muertos del intento anterior: eso no es una emisión nueva, y tratarlo como tal chocaba
   * contra la unicidad y reventaba con un 500.
   */
  @Test
  void un_eco_de_la_cache_no_se_toma_por_una_emision_nueva() {
    Pedido pedido = pedido(EstadoPedido.EN_PREPARACION, TipoEntrega.ENVIO_A_DOMICILIO, 1);
    cotizador.devolver(tarifaDeHoy(), otraTarifa());
    emisor.responde(new ResultadoEmision.Aceptada(List.of("177d1939")));
    caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1"))
        .resolver(EstadoEmision.FALLIDA, "murió", AHORA.plusSeconds(260));

    // El proveedor devuelve el mismo envío de la vez anterior.
    assertThrows(
        EmisionRechazadaException.class,
        () -> caso.ejecutar(new EmitirGuiaDePedidoComando(pedido.id(), "admin:1")));

    assertEquals(2, emisiones.todas().size());
    assertEquals(EstadoEmision.FALLIDA, emisiones.todas().get(1).estado());
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
