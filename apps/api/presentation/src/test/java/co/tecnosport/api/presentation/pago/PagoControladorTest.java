package co.tecnosport.api.presentation.pago;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.pago.CrearIntentoDePago;
import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.presentation.pago.dto.CrearIntentoDePagoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(PagoControlador.class)
@Import(PagoControladorTest.Configuracion.class)
class PagoControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioPedidosDobleDePrueba pedidos;

  private final ObjectMapper json = new ObjectMapper();

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private Pedido pedidoConMetodo(MetodoPago metodoPago) {
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
                    2,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.jpg")),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            metodoPago,
            "cliente@tecnosport.co",
            Instant.now());
    pedidos.conPedido(pedido);
    return pedido;
  }

  @Test
  void crearIntentoDevuelveLaReferenciaYLaFirma() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);

    mockMvc
        .perform(
            post("/api/v1/pagos/intentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CrearIntentoDePagoRequest(pedido.id()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.referencia").value(matchesPattern("TS-2026-000001-1")))
        .andExpect(jsonPath("$.monto.valor").value(100_000))
        .andExpect(jsonPath("$.firmaIntegridad").exists())
        .andExpect(jsonPath("$.llavePublica").value("pub_test"))
        .andExpect(jsonPath("$.ambiente").value("sandbox"));
  }

  @Test
  void pedidoInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pagos/intentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CrearIntentoDePagoRequest(UUID.randomUUID()))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("PEDIDO_NO_ENCONTRADO"));
  }

  @Test
  void pedidoQueNoEstaEnPagoPendienteDevuelve409() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.CONTRAENTREGA);

    mockMvc
        .perform(
            post("/api/v1/pagos/intentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CrearIntentoDePagoRequest(pedido.id()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PEDIDO_NO_ESTA_EN_PAGO_PENDIENTE"));
  }

  @Test
  void metodoDePagoQueNoVaPorWompiDevuelve409() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.TRANSFERENCIA_MANUAL);

    mockMvc
        .perform(
            post("/api/v1/pagos/intentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CrearIntentoDePagoRequest(pedido.id()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("METODO_DE_PAGO_NO_SOPORTADO_POR_WOMPI"));
  }

  @Test
  void pedidoIdNuloDevuelve422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pagos/intentos").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnprocessableContent());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioPedidosDobleDePrueba repositorioPedidos() {
      return new RepositorioPedidosDobleDePrueba();
    }

    @Bean
    RepositorioPagosDobleDePrueba repositorioPagos() {
      return new RepositorioPagosDobleDePrueba();
    }

    @Bean
    PasarelaDePagos pasarelaDePagos() {
      return new PasarelaDePagosDobleDePrueba();
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }

    @Bean
    CrearIntentoDePago crearIntentoDePago(
        RepositorioPedidos repositorioPedidos,
        RepositorioPagos repositorioPagos,
        PasarelaDePagos pasarelaDePagos) {
      return new CrearIntentoDePago(
          repositorioPedidos, repositorioPagos, pasarelaDePagos, Instant::now);
    }

    @Bean
    PropiedadesWompiPublicas propiedadesWompiPublicas() {
      return new PropiedadesWompiPublicas("pub_test", "sandbox");
    }

    @Bean
    MapeadorRespuestasPago mapeadorRespuestasPago(PropiedadesWompiPublicas propiedades) {
      return new MapeadorRespuestasPago(propiedades);
    }
  }
}
