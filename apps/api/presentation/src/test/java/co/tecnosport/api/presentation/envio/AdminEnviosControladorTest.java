package co.tecnosport.api.presentation.envio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.AcusarRevisionDeEmision;
import co.tecnosport.api.application.envio.AcusarRevisionDeGuia;
import co.tecnosport.api.application.envio.ListarEnviosEnRevision;
import co.tecnosport.api.application.envio.RepositorioAcusesDeRevision;
import co.tecnosport.api.application.envio.RepositorioEmisiones;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.application.envio.ResolverEmisionIndeterminada;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.presentation.pedido.RepositorioEmisionesDobleDePrueba;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * La bandeja por HTTP. Lo que se comprueba aquí es el contrato —qué sale, con qué nombres— y las
 * dos traducciones de error que antes no existían: una guía que no está es 404, y acusar algo sano
 * es 409.
 *
 * <p>{@code @DirtiesContext} por el mismo motivo que los demás controladores del panel: los dobles
 * son beans singleton del contexto de prueba y arrastrarían estado entre métodos.
 */
@WebMvcTest(AdminEnviosControlador.class)
@Import(AdminEnviosControladorTest.Configuracion.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminEnviosControladorTest {

  private static final Instant AHORA = Instant.parse("2026-09-17T15:00:00Z");
  private static final Instant DESPACHO = Instant.parse("2026-09-10T14:00:00Z");

  private static final Direccion MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null);

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioEnvios envios;
  @Autowired private RepositorioEmisiones emisiones;
  @Autowired private RepositorioPedidos pedidos;

  @BeforeEach
  void autenticarComoAdmin() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null));
  }

  @AfterEach
  void limpiarContextoDeSeguridad() {
    SecurityContextHolder.clearContext();
  }

  private Pedido sembrarPedido() {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 42),
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
                    null,
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            DESPACHO.minusSeconds(3600));
    pedidos.guardar(pedido);
    return pedido;
  }

  private void sembrarGuiaRetenida() {
    Pedido pedido = sembrarPedido();
    Envio envio =
        Envio.crear(
            pedido.id(),
            List.of(GuiaEnvio.crear("Servientrega", "SE-1", Dinero.deCop(8_200))),
            DESPACHO);
    envio.registrarEvento(
        "SE-1",
        new EventoSeguimiento(
            GeneradorIdentificador.nuevo(),
            EstadoEnvio.RETENIDO,
            "retenido en bodega",
            DESPACHO.plusSeconds(3600),
            DESPACHO.plusSeconds(3700),
            "ev-1"));
    envios.guardar(envio);
  }

  @Test
  void laBandejaTraeLaGuiaQuietaConSuPedidoYSuEstado() throws Exception {
    sembrarGuiaRetenida();

    mockMvc
        .perform(get("/api/v1/admin/envios/revision"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.guias.length()").value(1))
        .andExpect(jsonPath("$.guias[0].numeroGuia").value("SE-1"))
        .andExpect(jsonPath("$.guias[0].estado").value("RETENIDO"))
        .andExpect(jsonPath("$.guias[0].numeroPedido").value("TS-2026-000042"))
        .andExpect(jsonPath("$.guias[0].revisadaEn").doesNotExist())
        .andExpect(jsonPath("$.emisiones.length()").value(0));
  }

  @Test
  void acusarLaGuiaLaSacaDeLaBandejaYDevuelveElActor() throws Exception {
    sembrarGuiaRetenida();

    mockMvc
        .perform(
            post("/api/v1/admin/envios/revision/guias/{numero}/acuse", "SE-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\":\"Hablé con la transportadora.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tipo").value("GUIA"))
        .andExpect(jsonPath("$.nota").value("Hablé con la transportadora."))
        .andExpect(jsonPath("$.actor").value(org.hamcrest.Matchers.startsWith("admin:")))
        .andExpect(jsonPath("$.revisadoEn").value("2026-09-17T15:00:00Z"));

    mockMvc
        .perform(get("/api/v1/admin/envios/revision"))
        .andExpect(jsonPath("$.guias.length()").value(0));
  }

  @Test
  void acusarUnaGuiaQueNoExisteEs404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/envios/revision/guias/{numero}/acuse", "NO-EXISTE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\":null}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("GUIA_NO_ENCONTRADA"));
  }

  @Test
  void laBandejaTraeLaEmisionIndeterminadaConSuTarifa() throws Exception {
    Pedido pedido = sembrarPedido();
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Coordinadora", "tarifa-1", "admin:7", DESPACHO);
    emision.indeterminada("la llamada no terminó", DESPACHO.plusSeconds(30));
    emisiones.guardar(emision);

    mockMvc
        .perform(get("/api/v1/admin/envios/revision"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emisiones.length()").value(1))
        .andExpect(jsonPath("$.emisiones[0].estado").value("INDETERMINADA"))
        .andExpect(jsonPath("$.emisiones[0].idTarifa").value("tarifa-1"))
        .andExpect(jsonPath("$.emisiones[0].numeroPedido").value("TS-2026-000042"));
  }

  /** Acusar una emisión sana es 409: dejaría escrito un problema que nunca existió. */
  @Test
  void acusarUnaEmisionQueNoPideRevisionEs409() throws Exception {
    Pedido pedido = sembrarPedido();
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Envía", "tarifa-2", "admin:7", DESPACHO);
    emision.aceptada(List.of("env-1"), DESPACHO);
    emisiones.guardar(emision);

    mockMvc
        .perform(
            post("/api/v1/admin/envios/revision/emisiones/{id}/acuse", emision.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nota\":null}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("ACUSE_NO_APLICABLE"));
  }

  /**
   * El camino que desbloquea el pedido: la persona miró el panel, el envío no está, y la emisión
   * deja de bloquear.
   */
  @Test
  void resolverSinCobroDejaLaEmisionFallida() throws Exception {
    Pedido pedido = sembrarPedido();
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Coordinadora", "tarifa-1", "admin:7", DESPACHO);
    emision.indeterminada("la llamada no terminó", DESPACHO.plusSeconds(30));
    emisiones.guardar(emision);

    mockMvc
        .perform(
            post("/api/v1/admin/envios/revision/emisiones/{id}/resolucion", emision.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veredicto\":\"SIN_COBRO\",\"nota\":\"No aparece.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("FALLIDA"))
        .andExpect(
            jsonPath("$.detalle").value(org.hamcrest.Matchers.containsString("no hubo cobro")));

    mockMvc
        .perform(get("/api/v1/admin/envios/revision"))
        .andExpect(jsonPath("$.emisiones.length()").value(0));
  }

  /** El otro camino: el envío estaba, y vuelve a en curso con lo que la persona encontró. */
  @Test
  void resolverConEnvioDevuelveLaEmisionAEnCurso() throws Exception {
    Pedido pedido = sembrarPedido();
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Coordinadora", "tarifa-1", "admin:7", DESPACHO);
    emision.indeterminada("la llamada no terminó", DESPACHO.plusSeconds(30));
    emisiones.guardar(emision);

    mockMvc
        .perform(
            post("/api/v1/admin/envios/revision/emisiones/{id}/resolucion", emision.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veredicto\":\"CON_ENVIO\",\"enviosEnPlataforma\":[\"env-hallado\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("EN_CURSO"))
        .andExpect(jsonPath("$.enviosEnPlataforma[0]").value("env-hallado"));
  }

  /** Decir que el envío está sin decir cuál no resuelve nada: 409, y la emisión se queda igual. */
  @Test
  void resolverConEnvioSinIdentificadoresEs409() throws Exception {
    Pedido pedido = sembrarPedido();
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Coordinadora", "tarifa-1", "admin:7", DESPACHO);
    emision.indeterminada("la llamada no terminó", DESPACHO.plusSeconds(30));
    emisiones.guardar(emision);

    mockMvc
        .perform(
            post("/api/v1/admin/envios/revision/emisiones/{id}/resolucion", emision.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veredicto\":\"CON_ENVIO\",\"enviosEnPlataforma\":[]}"))
        .andExpect(status().isConflict());
  }

  @Test
  void unVeredictoQueNoExisteEs422() throws Exception {
    Pedido pedido = sembrarPedido();
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedido.id(), "Coordinadora", "tarifa-1", "admin:7", DESPACHO);
    emision.indeterminada("la llamada no terminó", DESPACHO.plusSeconds(30));
    emisiones.guardar(emision);

    mockMvc
        .perform(
            post("/api/v1/admin/envios/revision/emisiones/{id}/resolucion", emision.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"veredicto\":\"QUIZA\"}"))
        .andExpect(status().isUnprocessableContent());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    Reloj reloj() {
      return () -> AHORA;
    }

    @Bean
    RepositorioEnvios repositorioEnvios() {
      return new RepositorioEnviosDobleDePrueba();
    }

    @Bean
    RepositorioEmisiones repositorioEmisiones() {
      return new RepositorioEmisionesDobleDePrueba();
    }

    @Bean
    RepositorioAcusesDeRevision repositorioAcuses() {
      return new RepositorioAcusesDobleDePrueba();
    }

    @Bean
    RepositorioPedidos repositorioPedidos() {
      return new RepositorioPedidosDobleDePrueba();
    }

    @Bean
    ListarEnviosEnRevision listarEnviosEnRevision(
        RepositorioEnvios envios,
        RepositorioEmisiones emisiones,
        RepositorioAcusesDeRevision acuses,
        RepositorioPedidos pedidos) {
      return new ListarEnviosEnRevision(envios, emisiones, acuses, pedidos);
    }

    @Bean
    AcusarRevisionDeGuia acusarRevisionDeGuia(
        RepositorioEnvios envios, RepositorioAcusesDeRevision acuses, Reloj reloj) {
      return new AcusarRevisionDeGuia(envios, acuses, reloj);
    }

    @Bean
    AcusarRevisionDeEmision acusarRevisionDeEmision(
        RepositorioEmisiones emisiones, RepositorioAcusesDeRevision acuses, Reloj reloj) {
      return new AcusarRevisionDeEmision(emisiones, acuses, reloj);
    }

    @Bean
    ResolverEmisionIndeterminada resolverEmisionIndeterminada(
        RepositorioEmisiones emisiones, RepositorioAcusesDeRevision acuses, Reloj reloj) {
      return new ResolverEmisionIndeterminada(emisiones, acuses, reloj);
    }

    @Bean
    MapeadorBandejaDeRevision mapeador() {
      return new MapeadorBandejaDeRevision();
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }
  }
}
