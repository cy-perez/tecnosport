package co.tecnosport.api.presentation.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.envio.ArmadorDeBultos;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.CotizarEnvio;
import co.tecnosport.api.application.envio.EmisorDeGuias;
import co.tecnosport.api.application.envio.EmitirGuiaDePedido;
import co.tecnosport.api.application.envio.RepositorioEmisiones;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.application.envio.ResultadoCotizacion;
import co.tecnosport.api.application.envio.ResultadoEmision;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.CancelarPedido;
import co.tecnosport.api.application.pedido.ConciliarRecaudo;
import co.tecnosport.api.application.pedido.ConciliarTransferencia;
import co.tecnosport.api.application.pedido.DespacharPedido;
import co.tecnosport.api.application.pedido.ListarPedidosAdmin;
import co.tecnosport.api.application.pedido.MarcarEntregado;
import co.tecnosport.api.application.pedido.RechazarEnEntrega;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.pedido.VerificarContraentrega;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.presentation.compartido.RepositorioSolicitudesReversionVacio;
import co.tecnosport.api.presentation.compartido.TextosDeCorreoDobleDePrueba;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code @DirtiesContext} por método: {@code RepositorioPedidosDobleDePrueba} es un bean singleton
 * del contexto de prueba, y sin esto los pedidos sembrados en un test seguirían visibles en el
 * siguiente — falsea el conteo de {@code listaLosPedidosPaginados}.
 */
@WebMvcTest(AdminPedidosControlador.class)
@Import(AdminPedidosControladorTest.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminPedidosControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioPedidosDobleDePrueba pedidos;
  @Autowired private RepositorioEnviosDobleDePrueba envios;
  @Autowired private RepositorioInventarioDobleDePrueba inventarios;
  @Autowired private RepositorioEmisionesDobleDePrueba emisiones;
  @Autowired private EmisorDeGuiasDobleDePrueba emisor;
  @Autowired private RepositorioProductosDobleDePrueba productos;

  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  @AfterEach
  void limpiarContextoDeSeguridad() {
    SecurityContextHolder.clearContext();
  }

  private void autenticarComoAdmin() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null));
  }

  /**
   * Un número distinto por cada pedido sembrado. En el repositorio real el número es único, y dos
   * pruebas de esta clase siembran más de un pedido: con el mismo número estaban montando un
   * escenario que no puede existir.
   */
  private int pedidosSembrados = 0;

  private NumeroPedido siguienteNumeroDePrueba() {
    return NumeroPedido.de(2026, ++pedidosSembrados);
  }

  private Pedido pedidoConMetodo(MetodoPago metodoPago) {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(1, "stock inicial de prueba", Instant.now());
    MovimientoInventario reserva = inventario.reservar(1, null, Instant.now());
    inventarios.conInventario(inventario);

    Pedido pedido =
        Pedido.crear(
            siguienteNumeroDePrueba(),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    reserva.id())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            metodoPago,
            "cliente@tecnosport.co",
            Instant.now());
    pedidos.guardar(pedido);
    return pedido;
  }

  /** Un pedido creado hace {@code diasAtras} y llevado hasta {@code transiciones}. */
  private Pedido pedidoViejo(MetodoPago metodoPago, int diasAtras, EstadoPedido... transiciones) {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    Instant nacimiento = Instant.now().minus(diasAtras, java.time.temporal.ChronoUnit.DAYS);
    inventario.registrarEntrada(1, "stock inicial de prueba", nacimiento);
    MovimientoInventario reserva = inventario.reservar(1, null, nacimiento);
    inventarios.conInventario(inventario);

    Pedido pedido =
        Pedido.crear(
            siguienteNumeroDePrueba(),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    reserva.id())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            metodoPago,
            "cliente@tecnosport.co",
            nacimiento);
    Instant momento = nacimiento;
    for (EstadoPedido siguiente : transiciones) {
      momento = momento.plusSeconds(3600);
      pedido.transicionar(siguiente, "sistema", "prueba", momento);
    }
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void elPanelVeElPlazoDeEntregaVencido() throws Exception {
    pedidoViejo(MetodoPago.NEQUI, 40, EstadoPedido.PAGADO);

    mockMvc
        .perform(get("/api/v1/admin/pedidos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].plazoDeEntrega.verdicto").value("VENCIDO"))
        // Vencido y sin aviso es un estado normal: el vigilante pasa cada doce horas.
        .andExpect(jsonPath("$.items[0].plazoDeEntrega.avisadoEn").doesNotExist())
        .andExpect(jsonPath("$.items[0].plazoDeEntrega.limite").exists());
  }

  @Test
  void unPedidoDentroDelPlazoNoSePintaComoIncumplido() throws Exception {
    pedidoViejo(MetodoPago.NEQUI, 3, EstadoPedido.PAGADO);

    mockMvc
        .perform(get("/api/v1/admin/pedidos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].plazoDeEntrega.verdicto").value("EN_PLAZO"));
  }

  @Test
  void mientrasElPagoSigaPendienteNoHayPlazoQueMostrar() throws Exception {
    pedidoViejo(MetodoPago.NEQUI, 40);

    mockMvc
        .perform(get("/api/v1/admin/pedidos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].plazoDeEntrega").doesNotExist());
  }

  @Test
  void unPedidoViejoEntregadoATiempoSigueEnPlazo() throws Exception {
    // Se juzga contra su fecha de entrega, no contra el reloj de hoy: si no, cualquier pedido
    // antiguo cumplido aparecería como incumplido para siempre.
    pedidoViejo(
        MetodoPago.NEQUI,
        120,
        EstadoPedido.PAGADO,
        EstadoPedido.EN_PREPARACION,
        EstadoPedido.DESPACHADO,
        EstadoPedido.ENTREGADO);

    mockMvc
        .perform(get("/api/v1/admin/pedidos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].plazoDeEntrega.verdicto").value("EN_PLAZO"));
  }

  @Test
  void listaLosPedidosPaginados() throws Exception {
    pedidoConMetodo(MetodoPago.NEQUI);
    pedidoConMetodo(MetodoPago.TRANSFERENCIA_MANUAL);

    mockMvc
        .perform(get("/api/v1/admin/pedidos").param("pagina", "0").param("tamano", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.pagina").value(0))
        .andExpect(jsonPath("$.totalPedidos").value(2));
  }

  @Test
  void conciliaUnaTransferenciaManualYLaDejaListaParaPreparar() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.TRANSFERENCIA_MANUAL);
    autenticarComoAdmin();

    mockMvc
        .perform(post("/api/v1/admin/pedidos/{id}/conciliar-transferencia", pedido.id()))
        .andExpect(status().isOk())
        // ConciliarTransferencia encadena PAGADO -> EN_PREPARACION de una vez: no hace falta un
        // clic extra en el panel para pasar de "pago conciliado" a "listo para preparar".
        .andExpect(jsonPath("$.estado").value("EN_PREPARACION"));
  }

  @Test
  void conciliarUnPedidoDeWompiDevuelve409() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    autenticarComoAdmin();

    mockMvc
        .perform(post("/api/v1/admin/pedidos/{id}/conciliar-transferencia", pedido.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("METODO_DE_PAGO_NO_ES_TRANSFERENCIA_MANUAL"));
  }

  @Test
  void conciliarUnPedidoInexistenteDevuelve404() throws Exception {
    autenticarComoAdmin();

    mockMvc
        .perform(post("/api/v1/admin/pedidos/{id}/conciliar-transferencia", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("PEDIDO_NO_ENCONTRADO"));
  }

  @Test
  void verificarUnPedidoContraentregaLoPasaAEnPreparacion() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/verificar-contraentrega", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo":"Verificado por WhatsApp"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("EN_PREPARACION"));
  }

  @Test
  void despacharUnPedidoEnPreparacionCreaElEnvio() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", Instant.now());
    pedidos.guardar(pedido);
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/despacho", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"guias":[{"transportadora":"Servientrega","guia":"SE123456","costoEnvio":15000}]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("DESPACHADO"))
        .andExpect(jsonPath("$.envio.guias[0].transportadora").value("Servientrega"))
        .andExpect(jsonPath("$.envio.guias[0].guia").value("SE123456"))
        .andExpect(jsonPath("$.envio.costoEnvio.valor").value(15000))
        .andExpect(jsonPath("$.historial[-1].estado").value("DESPACHADO"));

    assertEquals(1, envios.guardados().size());
    assertEquals("Servientrega", envios.guardados().get(0).guias().getFirst().transportadora());
  }

  /**
   * Siembra en el catálogo la variante que ya lleva el pedido, que es lo que la emisión necesita
   * para armar los bultos: el peso, las medidas y la línea salen del producto, nunca de la línea
   * congelada. Se copia el identificador porque el pedido ya nació con él.
   */
  private void sembrarCatalogoDe(Pedido pedido) {
    Producto producto =
        Producto.crear(
            "Camiseta running Dry-Fit",
            new Slug("camiseta-running-dry-fit"),
            "Descripción",
            Marca.crear("TecnoSport"),
            Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA));
    producto.agregarVariante(
        new Variante(
            pedido.lineas().getFirst().varianteId(),
            new Sku("TS-CAM-AZ-M"),
            Dinero.deCop(50_000),
            new BigDecimal("0.19"),
            null,
            new Paquete(180, 30, 25, 4),
            EstadoVariante.ACTIVA,
            List.of(),
            null));
    productos.conProductos(producto);
  }

  /**
   * Con {@link Contacto}, que los demás pedidos de esta clase no llevan y aquí es obligatorio: sin
   * nombre ni teléfono de quien recibe no hay guía que emitir, y la emisión lo rechaza con un 409
   * antes de tocar a la plataforma.
   */
  private Pedido pedidoListoParaEmitir() {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(1, "stock inicial de prueba", Instant.now());
    MovimientoInventario reserva = inventario.reservar(1, null, Instant.now());
    inventarios.conInventario(inventario);

    Pedido pedido =
        Pedido.crear(
            siguienteNumeroDePrueba(),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    reserva.id())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            Instant.now(),
            null,
            new Contacto("Comprador de prueba", "3001234567"));
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", Instant.now());
    pedidos.guardar(pedido);
    sembrarCatalogoDe(pedido);
    autenticarComoAdmin();
    return pedido;
  }

  /** Un pedido sin a quién entregarle no llega a la plataforma: se rechaza antes. */
  @Test
  void emitirLaGuiaDeUnPedidoSinContactoDevuelve409() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", Instant.now());
    pedidos.guardar(pedido);
    sembrarCatalogoDe(pedido);
    autenticarComoAdmin();

    mockMvc
        .perform(post("/api/v1/admin/pedidos/{id}/emitir-guia", pedido.id()))
        .andExpect(status().isConflict());

    assertTrue(emisiones.todas().isEmpty());
  }

  /**
   * 202 y no 200: lo que se acepta es la solicitud, no el despacho. La plataforma cobra al crear y
   * devuelve la guía en {@code null}, así que el pedido tiene que seguir en {@code EN_PREPARACION}
   * hasta que la tarea programada traiga los números.
   */
  @Test
  void emitirLaGuiaAceptaLaSolicitudYNoDespachaTodavia() throws Exception {
    Pedido pedido = pedidoListoParaEmitir();

    mockMvc
        .perform(post("/api/v1/admin/pedidos/{id}/emitir-guia", pedido.id()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.estado").value("EN_CURSO"))
        .andExpect(jsonPath("$.transportadora").value("Servientrega"))
        .andExpect(jsonPath("$.cuantosEnvios").value(1))
        .andExpect(jsonPath("$.resueltaEn").doesNotExist());

    assertEquals(1, emisiones.todas().size());
    assertEquals(
        EstadoPedido.EN_PREPARACION, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
    assertTrue(envios.guardados().isEmpty());
  }

  /** Los identificadores de la plataforma no salen al panel: no le dicen nada a quien despacha. */
  @Test
  void laRespuestaDeLaEmisionNoExponeLosIdentificadoresDeLaPlataforma() throws Exception {
    Pedido pedido = pedidoListoParaEmitir();

    String cuerpo =
        mockMvc
            .perform(post("/api/v1/admin/pedidos/{id}/emitir-guia", pedido.id()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertFalse(cuerpo.contains("177d1939"));
  }

  /**
   * La puerta que cuesta plata: la plataforma cobra al crear, así que dos solicitudes son dos
   * cobros por el mismo pedido.
   */
  @Test
  void emitirDosVecesElMismoPedidoDevuelve409() throws Exception {
    Pedido pedido = pedidoListoParaEmitir();
    mockMvc.perform(post("/api/v1/admin/pedidos/{id}/emitir-guia", pedido.id()));

    mockMvc
        .perform(post("/api/v1/admin/pedidos/{id}/emitir-guia", pedido.id()))
        .andExpect(status().isConflict());

    assertEquals(1, emisiones.todas().size());
  }

  @Test
  void emitirLaGuiaDeUnPedidoSinVerificarDevuelve409() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);
    sembrarCatalogoDe(pedido);
    autenticarComoAdmin();

    mockMvc
        .perform(post("/api/v1/admin/pedidos/{id}/emitir-guia", pedido.id()))
        .andExpect(status().isConflict());

    assertTrue(emisiones.todas().isEmpty());
  }

  /**
   * 502 y no 503: la petición llegó y la contestaron diciendo que no, que es distinto de un
   * proveedor que no responde.
   */
  @Test
  void siLaPlataformaRechazaLaEmisionElPanelRecibe502() throws Exception {
    Pedido pedido = pedidoListoParaEmitir();
    emisor.responde(
        new ResultadoEmision.Rechazada(
            ResultadoEmision.Motivo.DATOS_RECHAZADOS, "422 area_level1 no puede estar en blanco"));

    mockMvc
        .perform(post("/api/v1/admin/pedidos/{id}/emitir-guia", pedido.id()))
        .andExpect(status().isBadGateway());

    // Y deja fila: sin ella se pierde la tarifa, que es lo unico que recupera un envio que la
    // plataforma pudo haber creado antes de decir que no.
    assertEquals(1, emisiones.todas().size());
  }

  @Test
  void despacharUnPedidoSinVerificarDevuelve422() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/despacho", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"guias":[{"transportadora":"Servientrega","guia":"SE123456","costoEnvio":15000}]}
                    """))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void marcarEntregadoUnPedidoContraentregaLoDejaEnRecaudoPendiente() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", Instant.now());
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despachado", Instant.now());
    pedidos.guardar(pedido);
    autenticarComoAdmin();

    mockMvc
        .perform(post("/api/v1/admin/pedidos/{id}/entrega", pedido.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("RECAUDO_PENDIENTE"));
  }

  @Test
  void rechazarEnEntregaLiberaElInventario() throws Exception {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(5, "siembra de prueba", Instant.now());
    MovimientoInventario reserva = inventario.reservar(1, null, Instant.now());
    inventarios.conInventario(inventario);

    Pedido pedido =
        Pedido.crear(
            siguienteNumeroDePrueba(),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    reserva.id())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            Instant.now());
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", Instant.now());
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despachado", Instant.now());
    pedidos.guardar(pedido);
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/rechazo-entrega", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo":"cliente no recibió"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("RECHAZADO_EN_ENTREGA"));

    assertEquals(
        5,
        inventarios.buscarPorVarianteId(varianteId).orElseThrow().saldoDisponible(Instant.now()));
  }

  @Test
  void listarFiltradoPorEstadoSoloTraeEsePedido() throws Exception {
    pedidoConMetodo(MetodoPago.NEQUI);
    Pedido pendiente = pedidoConMetodo(MetodoPago.CONTRAENTREGA);
    pendiente.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", Instant.now());
    pendiente.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despachado", Instant.now());
    pendiente.transicionar(EstadoPedido.ENTREGADO, "admin:test", "entregado", Instant.now());
    pendiente.transicionar(
        EstadoPedido.RECAUDO_PENDIENTE, "admin:test", "recaudo pendiente", Instant.now());
    pedidos.guardar(pendiente);

    mockMvc
        .perform(get("/api/v1/admin/pedidos").param("estado", "RECAUDO_PENDIENTE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(pendiente.id().toString()));
  }

  /**
   * Un cuerpo sin la modalidad no se cuela como nula. No lo impide ninguna anotación —esta capa no
   * tiene proveedor de Bean Validation— sino Jackson 3, que no rellena los componentes que falten
   * de un {@code record}. La prueba existe porque la garantía viene de un detalle del serializador
   * y no de algo escrito en el DTO.
   */
  @Test
  void conciliarRecaudoSinModalidadNoSeAcepta() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);
    pedidos.guardar(pedido);
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/recaudo", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"comisionRecaudo":5000}
                    """))
        .andExpect(status().isUnprocessableContent())
        // El código importa: sin él, la prueba pasaría igual por la transición inválida del pedido
        // y no por la deserialización, que es lo que aquí se quiere demostrar.
        .andExpect(jsonPath("$.codigo").value("HTTP_MESSAGE_NOT_READABLE"));
  }

  /**
   * Los créditos de la plataforma no cobran comisión (docs/13 §3): una conciliación que diga las
   * dos cosas está mal en una de ellas, y el dominio la rechaza antes de tocar el envío.
   */
  @Test
  void conciliarACreditosConComisionDevuelve422() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", Instant.now());
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despachado", Instant.now());
    pedido.transicionar(EstadoPedido.ENTREGADO, "admin:test", "entregado", Instant.now());
    pedido.transicionar(
        EstadoPedido.RECAUDO_PENDIENTE, "admin:test", "recaudo pendiente", Instant.now());
    pedidos.guardar(pedido);
    envios.guardar(
        Envio.crear(
            pedido.id(),
            List.of(GuiaEnvio.crear("Servientrega", "SE123457", Dinero.deCop(15_000))),
            Instant.now()));
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/recaudo", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"modalidadRecaudo":"CREDITOS","comisionRecaudo":5000}
                    """))
        .andExpect(status().isUnprocessableContent());

    assertTrue(envios.buscarPorPedidoId(pedido.id()).orElseThrow().recaudoConciliadoEn().isEmpty());
  }

  @Test
  void conciliarRecaudoRegistraLaComisionEnElEnvio() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "verificado", Instant.now());
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despachado", Instant.now());
    pedido.transicionar(EstadoPedido.ENTREGADO, "admin:test", "entregado", Instant.now());
    pedido.transicionar(
        EstadoPedido.RECAUDO_PENDIENTE, "admin:test", "recaudo pendiente", Instant.now());
    pedidos.guardar(pedido);
    envios.guardar(
        Envio.crear(
            pedido.id(),
            List.of(GuiaEnvio.crear("Servientrega", "SE123456", Dinero.deCop(15_000))),
            Instant.now()));
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/recaudo", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"modalidadRecaudo":"BANCO","comisionRecaudo":5000}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("RECAUDO_CONCILIADO"))
        .andExpect(jsonPath("$.envio.comisionRecaudo.valor").value(5000));

    assertEquals(
        Dinero.deCop(5_000),
        envios.buscarPorPedidoId(pedido.id()).orElseThrow().comisionRecaudo().orElseThrow());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioPedidosDobleDePrueba repositorioPedidos() {
      return new RepositorioPedidosDobleDePrueba();
    }

    @Bean
    RepositorioEnviosDobleDePrueba repositorioEnvios() {
      return new RepositorioEnviosDobleDePrueba();
    }

    @Bean
    RepositorioInventarioDobleDePrueba repositorioInventario() {
      return new RepositorioInventarioDobleDePrueba();
    }

    @Bean
    ListarPedidosAdmin listarPedidosAdmin(RepositorioPedidos repositorioPedidos) {
      return new ListarPedidosAdmin(repositorioPedidos);
    }

    @Bean
    ConciliarTransferencia conciliarTransferencia(
        RepositorioPedidos repositorioPedidos, RepositorioInventario repositorioInventario) {
      return new ConciliarTransferencia(repositorioPedidos, repositorioInventario, Instant::now);
    }

    @Bean
    VerificarContraentrega verificarContraentrega(RepositorioPedidos repositorioPedidos) {
      return new VerificarContraentrega(repositorioPedidos, Instant::now);
    }

    @Bean
    DespacharPedido despacharPedido(
        RepositorioPedidos repositorioPedidos, RepositorioEnvios repositorioEnvios) {
      return new DespacharPedido(
          repositorioPedidos,
          repositorioEnvios,
          (destinatario, asunto, cuerpo) -> {},
          new TextosDeCorreoDobleDePrueba(),
          Instant::now,
          "https://tecnosport.co/es/checkout/estado");
    }

    @Bean
    RepositorioEmisionesDobleDePrueba repositorioEmisiones() {
      return new RepositorioEmisionesDobleDePrueba();
    }

    @Bean
    EmisorDeGuiasDobleDePrueba emisorDeGuias() {
      return new EmisorDeGuiasDobleDePrueba();
    }

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductosParaEmision() {
      return new RepositorioProductosDobleDePrueba();
    }

    /**
     * Devuelve siempre la misma tarifa. Lo que estas pruebas comprueban es el cableado HTTP del
     * endpoint; que emitir recotice y elija bien se prueba en {@code EmitirGuiaDePedidoTest}.
     */
    @Bean
    CotizadorEnvio cotizadorEnvio() {
      return (CotizacionEnvio cotizacion) ->
          new ResultadoCotizacion.ConTarifas(
              List.of(
                  new TarifaEnvio(
                      "rate-de-hoy",
                      "Servientrega",
                      "Standard",
                      Dinero.deCop(8_200),
                      2,
                      true,
                      Instant.now().plusSeconds(86_400))));
    }

    @Bean
    EmitirGuiaDePedido emitirGuiaDePedido(
        RepositorioPedidos repositorioPedidos,
        RepositorioEmisiones repositorioEmisiones,
        RepositorioProductos repositorioProductos,
        CotizadorEnvio cotizadorEnvio,
        EmisorDeGuias emisorDeGuias) {
      ArmadorDeBultos armador =
          new ArmadorDeBultos(repositorioProductos, Dinero.deCop(10_000), Dinero.deCop(5_000_000));
      return new EmitirGuiaDePedido(
          repositorioPedidos,
          repositorioEmisiones,
          armador,
          new CotizarEnvio(armador, cotizadorEnvio, Instant::now),
          emisorDeGuias,
          // Sin transacciones que separar en un @WebMvcTest: lo que aquí se prueba es el cableado
          // HTTP, y el orden de las escrituras alrededor del cobro se prueba en su caso de uso.
          new EnTransaccionPropiaDobleDePrueba(),
          Instant::now);
    }

    @Bean
    CancelarPedido cancelarPedido(
        RepositorioPedidos repositorioPedidos,
        RepositorioInventario repositorioInventario,
        RepositorioReintegros repositorioReintegros,
        RepositorioEmisiones repositorioEmisiones,
        EmisorDeGuias emisorDeGuias) {
      return new CancelarPedido(
          repositorioPedidos,
          repositorioInventario,
          repositorioReintegros,
          repositorioEmisiones,
          emisorDeGuias,
          new TopeDeReintegro(repositorioReintegros, new RepositorioSolicitudesReversionVacio()),
          (destinatario, asunto, cuerpo) -> {},
          new TextosDeCorreoDobleDePrueba(),
          Instant::now);
    }

    @Bean
    RepositorioReintegros repositorioReintegros() {
      return new RepositorioReintegrosDobleDePrueba();
    }

    @Bean
    MarcarEntregado marcarEntregado(
        RepositorioPedidos repositorioPedidos, RepositorioInventario repositorioInventario) {
      return new MarcarEntregado(repositorioPedidos, repositorioInventario, Instant::now);
    }

    @Bean
    RechazarEnEntrega rechazarEnEntrega(
        RepositorioPedidos repositorioPedidos, RepositorioInventario repositorioInventario) {
      return new RechazarEnEntrega(repositorioPedidos, repositorioInventario, Instant::now);
    }

    @Bean
    ConciliarRecaudo conciliarRecaudo(
        RepositorioPedidos repositorioPedidos, RepositorioEnvios repositorioEnvios) {
      return new ConciliarRecaudo(repositorioPedidos, repositorioEnvios, Instant::now);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }

    @Bean
    PropiedadesTransferenciaManual propiedadesTransferenciaManual() {
      return new PropiedadesTransferenciaManual(
          "Bancolombia", "ahorros", "123-456789-00", "TecnoSport SAS");
    }

    @Bean
    MapeadorRespuestasPedido mapeadorRespuestasPedido(
        PropiedadesTransferenciaManual propiedadesTransferencia,
        RepositorioEnvios repositorioEnvios,
        RepositorioReintegros repositorioReintegros) {
      return new MapeadorRespuestasPedido(
          propiedadesTransferencia,
          repositorioEnvios,
          new TopeDeReintegro(repositorioReintegros, new RepositorioSolicitudesReversionVacio()),
          java.time.Instant::now);
    }
  }
}
