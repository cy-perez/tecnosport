package co.tecnosport.api.presentation.pago;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.pago.ListarPagosSinTransaccion;
import co.tecnosport.api.application.pago.MarcarPagoSinTransaccionRevisado;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.presentation.pedido.AdminAlertasControlador;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * En el paquete de pago y no en el de pedido para usar sus dobles: el controlador es del panel de
 * pedidos, pero lo que enseña son pagos.
 */
@WebMvcTest(AdminAlertasControlador.class)
@Import(AdminAlertasControladorTest.Configuracion.class)
class AdminAlertasControladorTest {

  private static final AtomicInteger SECUENCIAL = new AtomicInteger(500);

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioPedidosDobleDePrueba pedidos;
  @Autowired private RepositorioPagosDobleDePrueba pagos;

  @BeforeEach
  void limpiar() {
    pedidos.limpiar();
    pagos.limpiar();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null));
  }

  private Pago pagoSinTransaccionCreadoHace(Duration antiguedad) {
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
    pedidos.conPedido(pedido);
    Pago pago =
        Pago.crear(
            pedido.id(),
            new ReferenciaPago(pedido.numeroPedido().valor() + "-1"),
            MetodoPago.WOMPI,
            Dinero.deCop(61_200),
            Instant.now().minus(antiguedad));
    pagos.guardar(pago);
    return pago;
  }

  @Test
  void laAlertaListaElPagoViejoConSuUmbralYDejaFueraElReciente() throws Exception {
    Pago viejo = pagoSinTransaccionCreadoHace(Duration.ofHours(13));
    pagoSinTransaccionCreadoHace(Duration.ofHours(1));

    mockMvc
        .perform(get("/api/v1/admin/alertas/pagos-sin-transaccion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.umbralHoras").value(12))
        .andExpect(jsonPath("$.pagos", hasSize(1)))
        .andExpect(jsonPath("$.pagos[0].pagoId").value(viejo.id().toString()))
        .andExpect(jsonPath("$.pagos[0].referencia").value(viejo.referencia().valor()))
        .andExpect(jsonPath("$.pagos[0].monto.valor").value(61_200))
        .andExpect(jsonPath("$.pagos[0].estadoPedido").value("PAGO_PENDIENTE"));
  }

  @Test
  void marcarloRevisadoLoSacaDeLaAlerta() throws Exception {
    Pago viejo = pagoSinTransaccionCreadoHace(Duration.ofHours(13));

    mockMvc
        .perform(post("/api/v1/admin/alertas/pagos-sin-transaccion/{id}/revision", viejo.id()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/admin/alertas/pagos-sin-transaccion"))
        .andExpect(jsonPath("$.pagos", hasSize(0)));
  }

  @Test
  void unPagoQueYaTieneTransaccionNoSeMarcaY409() throws Exception {
    Pago pago = pagoSinTransaccionCreadoHace(Duration.ofHours(13));
    pago.registrarIdTransaccionPasarela("1234-1610641025-49201");
    pagos.guardar(pago);

    mockMvc
        .perform(post("/api/v1/admin/alertas/pagos-sin-transaccion/{id}/revision", pago.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PAGO_CON_TRANSACCION_O_RESUELTO"));
  }

  /** Llegó a preparación hace cuatro días: pasa el umbral de tres, con su fecha de llegada. */
  @Test
  void laAlertaListaElPedidoDetenidoEnPreparacionConSuLlegada() throws Exception {
    Pago pago = pagoSinTransaccionCreadoHace(Duration.ofHours(1));
    Pedido pedido = pedidos.buscarPorId(pago.pedidoId()).orElseThrow();
    Instant llegada = Instant.now().minus(Duration.ofDays(4));
    pedido.transicionar(
        co.tecnosport.api.domain.pedido.EstadoPedido.PAGADO, "webhook-wompi", "aprobado", llegada);
    pedido.transicionar(
        co.tecnosport.api.domain.pedido.EstadoPedido.EN_PREPARACION,
        "admin:1",
        "inventario confirmado",
        llegada);
    pedidos.guardar(pedido);

    mockMvc
        .perform(get("/api/v1/admin/alertas/pedidos-en-preparacion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.umbralDias").value(3))
        .andExpect(jsonPath("$.pedidos", hasSize(1)))
        .andExpect(jsonPath("$.pedidos[0].pedidoId").value(pedido.id().toString()))
        .andExpect(jsonPath("$.pedidos[0].tipoEntrega").value("ENVIO_A_DOMICILIO"))
        .andExpect(jsonPath("$.pedidos[0].metodoPago").value("WOMPI"))
        .andExpect(jsonPath("$.pedidos[0].desde").exists());
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
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }

    @Bean
    ListarPagosSinTransaccion listarPagosSinTransaccion(
        RepositorioPagos pagos, RepositorioPedidos pedidos) {
      return new ListarPagosSinTransaccion(pagos, pedidos, Instant::now, Duration.ofHours(12));
    }

    @Bean
    co.tecnosport.api.application.pedido.ListarPedidosDetenidosEnPreparacion
        listarPedidosDetenidosEnPreparacion(RepositorioPedidos pedidos) {
      return new co.tecnosport.api.application.pedido.ListarPedidosDetenidosEnPreparacion(
          pedidos, Instant::now, Duration.ofDays(3));
    }

    @Bean
    MarcarPagoSinTransaccionRevisado marcarPagoSinTransaccionRevisado(RepositorioPagos pagos) {
      return new MarcarPagoSinTransaccionRevisado(pagos, Instant::now);
    }
  }
}
