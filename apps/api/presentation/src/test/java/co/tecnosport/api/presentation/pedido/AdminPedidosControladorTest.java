package co.tecnosport.api.presentation.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.envio.RepositorioEnvios;
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
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.presentation.compartido.RepositorioSolicitudesReversionVacio;
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

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  @AfterEach
  void limpiarContextoDeSeguridad() {
    SecurityContextHolder.clearContext();
  }

  private void autenticarComoAdmin() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null));
  }

  private Pedido pedidoConMetodo(MetodoPago metodoPago) {
    UUID varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(1, "stock inicial de prueba", Instant.now());
    MovimientoInventario reserva = inventario.reservar(1, null, Instant.now());
    inventarios.conInventario(inventario);

    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
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
                    {"transportadora":"Servientrega","guia":"SE123456","costoEnvio":15000}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("DESPACHADO"))
        .andExpect(jsonPath("$.envio.transportadora").value("Servientrega"))
        .andExpect(jsonPath("$.envio.guia").value("SE123456"))
        .andExpect(jsonPath("$.envio.costoEnvio.valor").value(15000))
        .andExpect(jsonPath("$.historial[-1].estado").value("DESPACHADO"));

    assertEquals(1, envios.guardados().size());
    assertEquals("Servientrega", envios.guardados().get(0).transportadora());
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
                    {"transportadora":"Servientrega","guia":"SE123456","costoEnvio":15000}
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
            NumeroPedido.de(2026, 1),
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
        Envio.crear(pedido.id(), "Servientrega", "SE123456", Dinero.deCop(15_000), Instant.now()));
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/recaudo", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"comisionRecaudo":5000}
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
      return new DespacharPedido(repositorioPedidos, repositorioEnvios, Instant::now);
    }

    @Bean
    CancelarPedido cancelarPedido(
        RepositorioPedidos repositorioPedidos,
        RepositorioInventario repositorioInventario,
        RepositorioReintegros repositorioReintegros) {
      return new CancelarPedido(
          repositorioPedidos,
          repositorioInventario,
          repositorioReintegros,
          new TopeDeReintegro(repositorioReintegros, new RepositorioSolicitudesReversionVacio()),
          (destinatario, asunto, cuerpo) -> {},
          (texto, argumentos) -> texto.clave(),
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
          new TopeDeReintegro(repositorioReintegros, new RepositorioSolicitudesReversionVacio()));
    }
  }
}
