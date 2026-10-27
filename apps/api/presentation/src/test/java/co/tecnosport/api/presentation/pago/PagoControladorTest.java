package co.tecnosport.api.presentation.pago;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pago.CrearIntentoDePago;
import co.tecnosport.api.application.pago.PasarelaDePagos;
import co.tecnosport.api.application.pago.ProcesarEventoDePago;
import co.tecnosport.api.application.pago.RegistrarIdTransaccionWompi;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
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
import co.tecnosport.api.presentation.pago.dto.RegistrarIdTransaccionWompiRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
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
  @Autowired private PasarelaDePagosDobleDePrueba pasarela;
  @Autowired private RepositorioPedidosDobleDePrueba pedidos;
  @Autowired private RepositorioPagosDobleDePrueba pagos;
  @Autowired private RepositorioInventarioDobleDePrueba inventario;

  @BeforeEach
  void reiniciarElEstadoCompartido() {
    // Los dobles son beans del contexto de @WebMvcTest, uno solo para todos los métodos de
    // esta clase: sin esto, los pedidos y pagos de una prueba siguen visibles en la siguiente.
    pedidos.limpiar();
    pagos.limpiar();
    pasarela.limpiar();
  }

  private final ObjectMapper json = new ObjectMapper();

  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  // El contexto de @WebMvcTest se comparte entre los métodos de esta clase (mismos beans, sin
  // reiniciarse): un secuencial fijo repetido entre pruebas dejaba un Pago de una prueba anterior
  // "visible" para buscarPorReferencia en otra. Cada llamada saca uno nuevo.
  private static final AtomicInteger SECUENCIAL = new AtomicInteger();

  private Pedido pedidoConMetodo(MetodoPago metodoPago) {
    return pedidoConMetodo(metodoPago, Instant.now());
  }

  /**
   * La línea lleva su reserva de 30 minutos hecha en {@code reservadaEn}, como la deja el pedido.
   */
  private Pedido pedidoConMetodo(MetodoPago metodoPago, Instant reservadaEn) {
    UUID varianteId = UUID.randomUUID();
    Inventario libro = Inventario.crear(varianteId);
    libro.registrarEntrada(5, "siembra", reservadaEn);
    MovimientoInventario reserva = libro.reservar(2, Duration.ofMinutes(30), reservadaEn);
    inventario.conInventario(libro);
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, SECUENCIAL.incrementAndGet()),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    2,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.jpg",
                    reserva.id())),
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
              "amount_in_cents": 10000000,
              "currency": "COP"
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
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    Pago pago = pagoPendienteParaElPedido(pedido);
    // Un APPROVED se confirma con la pasarela antes de aplicarse.
    pasarela.conTransaccionAprobada("wompi-tx-1", pago.referencia().valor(), pago.monto());

    mockMvc
        .perform(
            post("/api/v1/pagos/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoWebhook(pago.referencia().valor(), "APPROVED", "checksum-valido")))
        .andExpect(status().isOk());

    assertEquals(
        EstadoPago.APROBADO, pagos.buscarPorReferencia(pago.referencia()).orElseThrow().estado());
    // EN_PREPARACION y no PAGADO: la línea lleva una reserva real y el aprobado la confirma. Con
    // la reserva inventada que tenía antes, el pedido se quedaba en PAGADO sin inventario detrás.
    assertEquals(
        EstadoPedido.EN_PREPARACION, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  /** Sin la confirmación de la pasarela, un APPROVED firmado no paga el pedido. */
  @Test
  void webhookAprobadoQueLaPasarelaNoConfirmaNoTransiciona() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    Pago pago = pagoPendienteParaElPedido(pedido);

    mockMvc
        .perform(
            post("/api/v1/pagos/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoWebhook(pago.referencia().valor(), "APPROVED", "checksum-valido")))
        .andExpect(status().isOk());

    assertEquals(
        EstadoPago.PENDIENTE, pagos.buscarPorReferencia(pago.referencia()).orElseThrow().estado());
    assertEquals(
        EstadoPedido.PAGO_PENDIENTE, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  @Test
  void webhookConFirmaInvalidaResponde200SinTransicionar() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
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
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
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
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);

    mockMvc
        .perform(
            post("/api/v1/pagos/intentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CrearIntentoDePagoRequest(pedido.id()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.referencia").value(matchesPattern("TS-2026-\\d{6}-1")))
        .andExpect(jsonPath("$.monto.valor").value(100_000))
        // El mismo número que firma el servidor; el navegador ya no lo calcula.
        .andExpect(jsonPath("$.montoEnCentavos").value(10_000_000))
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
  void pedidoConLaReservaVencidaDevuelve409ConSuCodigo() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI, Instant.now().minus(Duration.ofMinutes(31)));

    mockMvc
        .perform(
            post("/api/v1/pagos/intentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CrearIntentoDePagoRequest(pedido.id()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("RESERVA_DEL_PEDIDO_VENCIDA"));
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

  @Test
  void registrarIdTransaccionGuardaElIdEnElPago() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    Pago pago = pagoPendienteParaElPedido(pedido);
    pasarela.conTransaccion("1234-1610641025-49201", pago.referencia().valor(), pago.monto());

    mockMvc
        .perform(
            patch("/api/v1/pagos/intentos/{referencia}", pago.referencia().valor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new RegistrarIdTransaccionWompiRequest("1234-1610641025-49201"))))
        .andExpect(status().isOk());

    assertEquals(
        "1234-1610641025-49201",
        pagos
            .buscarPorReferencia(pago.referencia())
            .orElseThrow()
            .idTransaccionPasarela()
            .orElseThrow());
  }

  /** El id que la pasarela no reporta con esta referencia no entra, y el pago sigue sin id. */
  @Test
  void registrarUnIdQueNoEsDeEstePagoDevuelve422YNoLoGuarda() throws Exception {
    Pago pago = pagoPendienteParaElPedido(pedidoConMetodo(MetodoPago.WOMPI));

    mockMvc
        .perform(
            patch("/api/v1/pagos/intentos/{referencia}", pago.referencia().valor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new RegistrarIdTransaccionWompiRequest("basura"))))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("TRANSACCION_DE_OTRO_PAGO"));

    assertTrue(
        pagos
            .buscarPorReferencia(pago.referencia())
            .orElseThrow()
            .idTransaccionPasarela()
            .isEmpty());
  }

  @Test
  void registrarIdTransaccionConReferenciaInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/pagos/intentos/{referencia}", "no-existe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new RegistrarIdTransaccionWompiRequest("1234-1610641025-49201"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("PAGO_NO_ENCONTRADO"));
  }

  @Test
  void registrarIdTransaccionVacioDevuelve422() throws Exception {
    Pedido pedido = pedidoConMetodo(MetodoPago.WOMPI);
    Pago pago = pagoPendienteParaElPedido(pedido);

    mockMvc
        .perform(
            patch("/api/v1/pagos/intentos/{referencia}", pago.referencia().valor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
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
    PasarelaDePagosDobleDePrueba pasarelaDePagos() {
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
        PasarelaDePagos pasarelaDePagos,
        RepositorioInventario repositorioInventario) {
      return new CrearIntentoDePago(
          repositorioPedidos,
          repositorioPagos,
          pasarelaDePagos,
          repositorioInventario,
          Instant::now);
    }

    @Bean
    RepositorioInventarioDobleDePrueba repositorioInventario() {
      return new RepositorioInventarioDobleDePrueba();
    }

    @Bean
    ProcesarEventoDePago procesarEventoDePago(
        RepositorioPagos repositorioPagos,
        RepositorioPedidos repositorioPedidos,
        RepositorioInventario repositorioInventario,
        PasarelaDePagos pasarelaDePagos) {
      return new ProcesarEventoDePago(
          repositorioPagos,
          repositorioPedidos,
          repositorioInventario,
          pasarelaDePagos,
          Instant::now);
    }

    @Bean
    RegistrarIdTransaccionWompi registrarIdTransaccionWompi(
        RepositorioPagos repositorioPagos, PasarelaDePagos pasarelaDePagos) {
      return new RegistrarIdTransaccionWompi(repositorioPagos, pasarelaDePagos);
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
