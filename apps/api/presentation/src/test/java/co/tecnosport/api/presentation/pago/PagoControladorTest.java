package co.tecnosport.api.presentation.pago;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.pago.CrearIntentoDePago;
import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.application.pago.ProcesarEventoDePago;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
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
import java.util.concurrent.atomic.AtomicInteger;
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
  @Autowired private RepositorioPagosDobleDePrueba pagos;

  private final ObjectMapper json = new ObjectMapper();

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  // El contexto de @WebMvcTest se comparte entre los métodos de esta clase (mismos beans, sin
  // reiniciarse): un secuencial fijo repetido entre pruebas dejaba un Pago de una prueba anterior
  // "visible" para buscarPorReferencia en otra. Cada llamada saca uno nuevo.
  private static final AtomicInteger SECUENCIAL = new AtomicInteger();

  private Pedido pedidoConMetodo(MetodoPago metodoPago) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, SECUENCIAL.incrementAndGet()),
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

  private Pago pagoPendienteParaElPedido(Pedido pedido) {
    Pago pago =
        Pago.crear(
            pedido.id(),
            new ReferenciaPago(pedido.numeroPedido().valor() + "-1"),
            pedido.metodoPago(),
            pedido.total(),
            Instant.now());
    pagos.guardar(pago);
    return pago;
  }

  private String cuerpoWebhook(String referencia, String estado, String checksum) {
    return """
        {
          "event": "transaction.updated",
          "data": {
            "transaction": {
              "id": "wompi-tx-1",
              "reference": "%s",
              "status": "%s",
              "amount_in_cents": 10000000
            }
          },
          "signature": {
            "properties": ["transaction.id", "transaction.status", "transaction.amount_in_cents"],
            "checksum": "%s"
          },
          "timestamp": 1700000000,
          "environment": "test"
        }
        """
        .formatted(referencia, estado, checksum);
  }

  @Test
  void webhookAprobadoTransicionaElPagoYElPedido() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    Pago pago = pagoPendienteParaElPedido(pedido);

    mockMvc
        .perform(
            post("/api/v1/pagos/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoWebhook(pago.referencia().valor(), "APPROVED", "checksum-valido")))
        .andExpect(status().isOk());

    assertEquals(
        EstadoPago.APROBADO, pagos.buscarPorReferencia(pago.referencia()).orElseThrow().estado());
    assertEquals(EstadoPedido.PAGADO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void webhookConFirmaInvalidaResponde200SinTransicionar() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    Pago pago = pagoPendienteParaElPedido(pedido);

    mockMvc
        .perform(
            post("/api/v1/pagos/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoWebhook(pago.referencia().valor(), "APPROVED", "checksum-invalido")))
        .andExpect(status().isOk());

    assertEquals(
        EstadoPedido.PAGO_PENDIENTE, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void webhookDeOtroTipoDeEventoNoProcesaNada() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.NEQUI);
    pagoPendienteParaElPedido(pedido);
    String cuerpoOtroEvento =
        """
        {"event": "nequi_token.updated", "data": {}}
        """;

    mockMvc
        .perform(
            post("/api/v1/pagos/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoOtroEvento))
        .andExpect(status().isOk());

    assertEquals(
        EstadoPedido.PAGO_PENDIENTE, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
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
        .andExpect(jsonPath("$.referencia").value(matchesPattern("TS-2026-\\d{6}-1")))
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
    ProcesarEventoDePago procesarEventoDePago(
        RepositorioPagos repositorioPagos,
        RepositorioPedidos repositorioPedidos,
        PasarelaDePagos pasarelaDePagos) {
      return new ProcesarEventoDePago(
          repositorioPagos, repositorioPedidos, pasarelaDePagos, Instant::now);
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
