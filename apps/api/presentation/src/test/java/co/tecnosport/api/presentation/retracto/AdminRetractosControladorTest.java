package co.tecnosport.api.presentation.retracto;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.retracto.RecibirProductoDevuelto;
import co.tecnosport.api.application.retracto.RegistrarReintegro;
import co.tecnosport.api.application.retracto.RegistrarRetracto;
import co.tecnosport.api.application.retracto.RepositorioSolicitudesRetracto;
import co.tecnosport.api.domain.compartido.CalendarioHabil;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
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
 * {@code @DirtiesContext} por el mismo motivo que {@code AdminPedidosControladorTest}: los dobles
 * son beans singleton del contexto de prueba y arrastrarian estado entre metodos.
 */
@WebMvcTest(AdminRetractosControlador.class)
@Import(AdminRetractosControladorTest.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminRetractosControladorTest {

  private static final Instant ENTREGA = Instant.parse("2026-09-10T20:30:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioPedidosDobleDePrueba pedidos;
  @Autowired private RepositorioInventarioDobleDePrueba inventarios;

  @AfterEach
  void limpiarContextoDeSeguridad() {
    SecurityContextHolder.clearContext();
  }

  private void autenticarComoAdmin() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null));
  }

  private Pedido pedidoConReserva(int numero, UUID varianteId, MovimientoInventario reserva) {
    return Pedido.crear(
        NumeroPedido.de(2026, numero),
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
        new Direccion("05", "Antioquia", "05001", "Medellin", "Cra. 26C #38B-31", null),
        MetodoPago.CONTRAENTREGA,
        "cliente@tecnosport.co",
        ENTREGA.minusSeconds(86_400));
  }

  private MovimientoInventario sembrarInventario(UUID varianteId) {
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(3, "stock de prueba", ENTREGA.minusSeconds(1000));
    MovimientoInventario reserva = inventario.reservar(1, null, ENTREGA.minusSeconds(900));
    inventarios.conInventario(inventario);
    return reserva;
  }

  /** Contraentrega recorrido hasta el final: entregado, recaudado y conciliado. */
  private Pedido pedidoEntregado() {
    UUID varianteId = UUID.randomUUID();
    Pedido pedido = pedidoConReserva(1, varianteId, sembrarInventario(varianteId));
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "admin:1", "verificado", ENTREGA);
    pedido.transicionar(EstadoPedido.DESPACHADO, "admin:1", "guia 123", ENTREGA);
    pedido.transicionar(EstadoPedido.ENTREGADO, "admin:1", "entregado", ENTREGA);
    pedido.transicionar(EstadoPedido.RECAUDO_PENDIENTE, "admin:1", "recaudo", ENTREGA);
    pedido.transicionar(EstadoPedido.RECAUDO_CONCILIADO, "admin:1", "conciliado", ENTREGA);
    pedidos.guardar(pedido);
    return pedido;
  }

  private Pedido pedidoSinEntregar() {
    UUID varianteId = UUID.randomUUID();
    Pedido pedido = pedidoConReserva(2, varianteId, sembrarInventario(varianteId));
    pedidos.guardar(pedido);
    return pedido;
  }

  private String radicar(Pedido pedido) throws Exception {
    String cuerpo =
        mockMvc
            .perform(
                post("/api/v1/admin/pedidos/{id}/retractos", pedido.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"motivo\":\"no le sirvio la talla\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return cuerpo.split("\"id\":\"")[1].split("\"")[0];
  }

  private void radicarYRecibir(Pedido pedido, String[] idFuera) throws Exception {
    String solicitudId = radicar(pedido);
    idFuera[0] = solicitudId;
    mockMvc
        .perform(post("/api/v1/admin/retractos/{id}/recepcion", solicitudId))
        .andExpect(status().isOk());
  }

  @Test
  void radicaUnRetractoSobreUnPedidoEntregado() throws Exception {
    Pedido pedido = pedidoEntregado();
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/retractos", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estado").value("RADICADA"))
        .andExpect(jsonPath("$.verdictoAlRadicar").value("EN_PLAZO"))
        .andExpect(jsonPath("$.pedidoId").value(pedido.id().toString()));
  }

  @Test
  void radicarSobreUnPedidoSinEntregarDevuelve409() throws Exception {
    Pedido pedido = pedidoSinEntregar();
    autenticarComoAdmin();

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/retractos", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict());
  }

  @Test
  void radicarDosVecesDevuelve409() throws Exception {
    Pedido pedido = pedidoEntregado();
    autenticarComoAdmin();
    radicar(pedido);

    mockMvc
        .perform(
            post("/api/v1/admin/pedidos/{id}/retractos", pedido.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict());
  }

  @Test
  void recibirElProductoDejaElPedidoDevueltoYArrancaElPlazoDeReintegro() throws Exception {
    Pedido pedido = pedidoEntregado();
    autenticarComoAdmin();
    String solicitudId = radicar(pedido);

    mockMvc
        .perform(post("/api/v1/admin/retractos/{id}/recepcion", solicitudId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PRODUCTO_RECIBIDO"))
        .andExpect(jsonPath("$.productoRecibidoEn").exists())
        .andExpect(jsonPath("$.limiteDeReintegro").exists());
  }

  @Test
  void registraElReembolsoYCierraLaSolicitud() throws Exception {
    Pedido pedido = pedidoEntregado();
    autenticarComoAdmin();
    String[] id = new String[1];
    radicarYRecibir(pedido, id);

    mockMvc
        .perform(
            post("/api/v1/admin/retractos/{id}/reintegro", id[0])
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"monto\":50000,\"medio\":\"TRANSFERENCIA_BANCARIA\",\"comprobante\":\"TRF-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("REEMBOLSADA"))
        .andExpect(jsonPath("$.reintegro.medio").value("TRANSFERENCIA_BANCARIA"))
        .andExpect(jsonPath("$.reintegro.comprobante").value("TRF-1"));
  }

  @Test
  void reembolsarMasDeLoPagadoDevuelve422() throws Exception {
    Pedido pedido = pedidoEntregado();
    autenticarComoAdmin();
    String[] id = new String[1];
    radicarYRecibir(pedido, id);

    mockMvc
        .perform(
            post("/api/v1/admin/retractos/{id}/reintegro", id[0])
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"monto\":50001,\"medio\":\"WOMPI\"}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void reembolsarAntesDeRecibirElProductoDevuelve422() throws Exception {
    Pedido pedido = pedidoEntregado();
    autenticarComoAdmin();
    String solicitudId = radicar(pedido);

    mockMvc
        .perform(
            post("/api/v1/admin/retractos/{id}/reintegro", solicitudId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"monto\":50000,\"medio\":\"WOMPI\"}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void unaSolicitudInexistenteDevuelve404() throws Exception {
    autenticarComoAdmin();

    mockMvc
        .perform(post("/api/v1/admin/retractos/{id}/recepcion", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void listaLasSolicitudesDeUnPedido() throws Exception {
    Pedido pedido = pedidoEntregado();
    autenticarComoAdmin();
    radicar(pedido);

    mockMvc
        .perform(get("/api/v1/admin/pedidos/{id}/retractos", pedido.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].motivo").value("no le sirvio la talla"));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioSolicitudesRetractoDobleDePrueba repositorioSolicitudes() {
      return new RepositorioSolicitudesRetractoDobleDePrueba();
    }

    @Bean
    RepositorioPedidosDobleDePrueba repositorioPedidos() {
      return new RepositorioPedidosDobleDePrueba();
    }

    @Bean
    RepositorioInventarioDobleDePrueba repositorioInventario() {
      return new RepositorioInventarioDobleDePrueba();
    }

    /** Dos dias despues de la entrega: dentro de los cinco habiles, sin depender del reloj real. */
    @Bean
    EnviadorDeCorreo enviadorDeCorreo() {
      return new EnviadorDeCorreoDobleDePrueba();
    }

    @Bean
    Reloj reloj() {
      return () -> ENTREGA.plusSeconds(172_800);
    }

    @Bean
    RegistrarRetracto registrarRetracto(
        RepositorioSolicitudesRetracto solicitudes,
        RepositorioPedidos pedidos,
        EnviadorDeCorreo correos,
        Reloj reloj) {
      return new RegistrarRetracto(
          solicitudes, pedidos, CalendarioHabil.sinFestivosCargados(), correos, reloj);
    }

    @Bean
    RecibirProductoDevuelto recibirProductoDevuelto(
        RepositorioSolicitudesRetracto solicitudes,
        RepositorioPedidos pedidos,
        RepositorioInventario inventarios,
        Reloj reloj) {
      return new RecibirProductoDevuelto(solicitudes, pedidos, inventarios, reloj);
    }

    @Bean
    RegistrarReintegro registrarReintegro(
        RepositorioSolicitudesRetracto solicitudes,
        RepositorioPedidos pedidos,
        RepositorioReintegros reintegros,
        EnviadorDeCorreo correos,
        Reloj reloj) {
      return new RegistrarReintegro(solicitudes, pedidos, reintegros, correos, reloj);
    }

    @Bean
    RepositorioReintegros repositorioReintegros() {
      return new RepositorioReintegrosDobleDePrueba();
    }

    @Bean
    MapeadorRespuestasRetracto mapeador() {
      return new MapeadorRespuestasRetracto();
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }
  }
}
