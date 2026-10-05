package co.tecnosport.api.presentation.pago;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.pago.ListarPagosSinPedido;
import co.tecnosport.api.application.pago.RegistrarReintegroDePagoSinPedido;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import co.tecnosport.api.presentation.compartido.TextosDeCorreoDobleDePrueba;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(AdminPagosControlador.class)
@Import(AdminPagosControladorTest.Configuracion.class)
class AdminPagosControladorTest {

  private static final AtomicInteger SECUENCIAL = new AtomicInteger();

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioPedidosDobleDePrueba pedidos;
  @Autowired private RepositorioPagosDobleDePrueba pagos;
  @Autowired private Reintegros reintegros;

  @BeforeEach
  void limpiar() {
    pedidos.limpiar();
    pagos.limpiar();
    reintegros.lista.clear();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null));
  }

  private Pago pagoSinPedido() {
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
                    "Camiseta",
                    1,
                    Dinero.deCop(50_000),
                    BigDecimal.ZERO,
                    null,
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            MetodoPago.WOMPI,
            "cliente@tecnosport.co",
            Instant.now());
    pedido.transicionar(EstadoPedido.CANCELADO, "admin:1", "sin existencia", Instant.now());
    pedidos.conPedido(pedido);
    Pago pago =
        Pago.crear(
            pedido.id(),
            new ReferenciaPago(pedido.numeroPedido().valor() + "-1"),
            MetodoPago.WOMPI,
            Dinero.deCop(61_200),
            Instant.now());
    pago.aplicarEvento(new EventoPago("evt-1", EstadoPago.APROBADO, Instant.now()));
    pago.marcarSinPedidoQueLoEspere(Instant.now());
    pagos.guardar(pago);
    return pago;
  }

  @Test
  void laBandejaListaElPagoConSuPedidoYElMontoADevolver() throws Exception {
    Pago pago = pagoSinPedido();

    mockMvc
        .perform(get("/api/v1/admin/pagos/sin-pedido"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].pagoId").value(pago.id().toString()))
        .andExpect(jsonPath("$[0].monto.valor").value(61_200))
        .andExpect(jsonPath("$[0].estadoPedido").value("CANCELADO"));
  }

  /** El cuerpo no lleva monto, y si lo llevara no se usaría: el reintegro es el pago entero. */
  @Test
  void registrarElReintegroUsaElMontoDelPagoYLoSacaDeLaBandeja() throws Exception {
    Pago pago = pagoSinPedido();

    mockMvc
        .perform(
            post("/api/v1/admin/pagos/{id}/reintegro", pago.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"medio\":\"WOMPI\",\"comprobante\":\"R-1\",\"monto\":1}"))
        .andExpect(status().isNoContent());

    assertEquals(1, reintegros.lista.size());
    assertEquals(Dinero.deCop(61_200), reintegros.lista.get(0).monto());
    assertEquals(MotivoReintegro.PAGO_SIN_PEDIDO, reintegros.lista.get(0).motivo());
    mockMvc.perform(get("/api/v1/admin/pagos/sin-pedido")).andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void devolverDosVecesEsUn409() throws Exception {
    Pago pago = pagoSinPedido();
    String cuerpo = "{\"medio\":\"WOMPI\"}";
    mockMvc.perform(
        post("/api/v1/admin/pagos/{id}/reintegro", pago.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpo));

    mockMvc
        .perform(
            post("/api/v1/admin/pagos/{id}/reintegro", pago.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PAGO_SIN_PEDIDO_YA_DEVUELTO"));
  }

  /** Doble mínimo del repositorio de reintegros, en memoria. */
  static final class Reintegros implements RepositorioReintegros {

    final List<Reintegro> lista = new ArrayList<>();

    @Override
    public void guardar(Reintegro reintegro) {
      lista.add(reintegro);
    }

    @Override
    public Optional<Reintegro> buscarPorId(UUID id) {
      return lista.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    @Override
    public List<Reintegro> buscarPorPedido(UUID pedidoId) {
      return lista.stream().filter(r -> r.pedidoId().equals(pedidoId)).toList();
    }

    @Override
    public Optional<Reintegro> buscarPorOrigen(UUID origenId) {
      return lista.stream().filter(r -> r.origenId().equals(origenId)).findFirst();
    }
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
    Reintegros repositorioReintegros() {
      return new Reintegros();
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }

    @Bean
    ListarPagosSinPedido listarPagosSinPedido(
        RepositorioPagos pagos, RepositorioPedidos pedidos, RepositorioReintegros reintegros) {
      return new ListarPagosSinPedido(pagos, pedidos, reintegros);
    }

    @Bean
    RegistrarReintegroDePagoSinPedido registrarReintegroDePagoSinPedido(
        RepositorioPagos pagos, RepositorioPedidos pedidos, RepositorioReintegros reintegros) {
      Reloj reloj = Instant::now;
      return new RegistrarReintegroDePagoSinPedido(
          pagos,
          pedidos,
          reintegros,
          (destinatario, asunto, cuerpo) -> {},
          new TextosDeCorreoDobleDePrueba(),
          reloj);
    }
  }
}
