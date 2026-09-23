package co.tecnosport.api.presentation.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pago.CrearIntentoDePagoSistecredito;
import co.tecnosport.api.application.pago.PasarelaSistecredito;
import co.tecnosport.api.application.pago.ProcesarNotificacionSistecredito;
import co.tecnosport.api.application.pago.RepositorioPagos;
import co.tecnosport.api.application.pago.TransaccionSistecredito;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
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

/**
 * El endpoint que Sistecrédito llama en cada cambio de estado. <b>No tenía ninguna prueba de esta
 * capa</b> hasta el 23 de septiembre de 2026, que es el día en que el despliegue de dev enseñó lo
 * que le faltaba: dos notificaciones simultáneas de la misma transacción hacían que la segunda
 * saliera por "Error inesperado sin manejar" con un 500 para la pasarela — justo la respuesta que
 * la hace reintentar.
 *
 * <p>La notificación <b>no viene firmada</b>: lo único que la autentica es la consulta que el caso
 * de uso hace contra la pasarela, y por eso el doble de la pasarela es el centro del montaje.
 */
@WebMvcTest(SistecreditoControlador.class)
@Import(SistecreditoControladorTest.Configuracion.class)
class SistecreditoControladorTest {

  private static final String ID_TRANSACCION = "6ab40fd77705464e7c900c70";

  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private static final AtomicInteger SECUENCIAL = new AtomicInteger();

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioPedidosDobleDePrueba pedidos;
  @Autowired private RepositorioPagosDobleDePrueba pagos;
  @Autowired private PasarelaSistecreditoDobleDePrueba pasarela;

  @BeforeEach
  void reiniciarElEstadoCompartido() {
    pedidos.limpiar();
    pagos.limpiar();
    pasarela.limpiar();
  }

  @Test
  void unaNotificacionAprobadaAplicaElPagoYDejaElPedidoListoParaPreparar() throws Exception {
    Pago pago = pagoPendienteConSuTransaccion();

    mockMvc
        .perform(
            post("/api/v1/pagos/sistecredito/confirmacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoNotificacion(pago.referencia().valor(), "Approved")))
        .andExpect(status().isOk());

    assertEquals(
        EstadoPago.APROBADO, pagos.buscarPorReferencia(pago.referencia()).orElseThrow().estado());
    // PAGADO y no EN_PREPARACION: el salto de uno a otro lo da confirmar la reserva de inventario,
    // y en este montaje no hay ninguna que confirmar. Contra dev, con su reserva de verdad, el
    // pedido siguió hasta EN_PREPARACION en el mismo minuto.
    assertEquals(EstadoPedido.PAGADO, pedidos.buscarPorId(pago.pedidoId()).orElseThrow().estado());
  }

  /**
   * <b>Lo que pasó de verdad, las dos veces que se probó contra dev.</b> Sistecrédito manda la
   * notificación por duplicado y las dos copias entran a la vez: las dos leen el pago pendiente —la
   * guarda del caso de uso solo atrapa las repeticiones en serie— y la segunda choca contra el
   * índice único de {@code evento_pago}.
   *
   * <p>El estado ya quedó bien, lo dejó la gemela. Lo único que hay que evitar es contestar algo
   * que la haga reintentar, así que esto es un 200.
   */
  @Test
  void unaNotificacionDuplicadaEnCarreraNoSeLeContestaConUn500() throws Exception {
    Pago pago = pagoPendienteConSuTransaccion();
    pagos.simularQueOtraNotificacionYaRegistroElEvento();

    mockMvc
        .perform(
            post("/api/v1/pagos/sistecredito/confirmacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoNotificacion(pago.referencia().valor(), "Approved")))
        .andExpect(status().isOk());
  }

  /** Sin pago local no hay nada que aplicar, y tampoco nada que reintentar. */
  @Test
  void unaNotificacionDeUnaReferenciaAjenaResponde200SinTocarNada() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/pagos/sistecredito/confirmacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoNotificacion("TS-2026-999999-1", "Approved")))
        .andExpect(status().isOk());
  }

  private Pago pagoPendienteConSuTransaccion() {
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
                    new BigDecimal("0.00"),
                    "https://cdn.tecnosport.co/img.jpg",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.SISTECREDITO,
            "cliente@tecnosport.co",
            Instant.now());
    pedidos.conPedido(pedido);

    Pago pago =
        Pago.crear(
            pedido.id(),
            new ReferenciaPago(pedido.numeroPedido().valor() + "-1"),
            MetodoPago.SISTECREDITO,
            pedido.total(),
            Instant.now());
    pago.registrarIdTransaccionPasarela(ID_TRANSACCION);
    pagos.guardar(pago);

    pasarela.responder(
        new TransaccionSistecredito(
            ID_TRANSACCION,
            pago.referencia().valor(),
            "Approved",
            pedido.total().valor().longValueExact(),
            null,
            "6",
            "Approved"));
    return pago;
  }

  /**
   * La forma que llega de verdad: la transacción dentro de {@code data}, tal como la enseña la guía
   * {@code G-SCL-21} §3.3.
   */
  private String cuerpoNotificacion(String referencia, String estado) {
    return """
        {
          "data": {
            "_id": "%s",
            "invoice": "%s",
            "transactionStatus": "%s",
            "paymentMethodResponse": {"statusResponse": "%s", "codeResponse": "6"}
          }
        }
        """
        .formatted(ID_TRANSACCION, referencia, estado, estado);
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
    RepositorioInventarioDobleDePrueba repositorioInventario() {
      return new RepositorioInventarioDobleDePrueba();
    }

    @Bean
    PasarelaSistecreditoDobleDePrueba pasarelaSistecredito() {
      return new PasarelaSistecreditoDobleDePrueba();
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }

    /**
     * El controlador lo exige para construirse aunque estas pruebas no toquen {@code /intentos}:
     * las dos puntas de Sistecrédito comparten controlador. Con el sandbox apagado, que es lo que
     * un despliegue sin perfil de pruebas tiene.
     */
    @Bean
    CrearIntentoDePagoSistecredito crearIntentoDePagoSistecredito(
        RepositorioPedidos repositorioPedidos,
        RepositorioPagos repositorioPagos,
        PasarelaSistecredito pasarela) {
      return new CrearIntentoDePagoSistecredito(
          repositorioPedidos,
          repositorioPagos,
          pasarela,
          new EnTransaccionPropia() {
            @Override
            public <T> T ejecutar(Supplier<T> trabajo) {
              return trabajo.get();
            }
          },
          Instant::now,
          "https://tecnosport.co/{idioma}/checkout/sistecredito/retorno",
          "https://api.tecnosport.co/api/v1/pagos/sistecredito/confirmacion",
          false,
          null);
    }

    @Bean
    ProcesarNotificacionSistecredito procesarNotificacionSistecredito(
        RepositorioPagos repositorioPagos,
        RepositorioPedidos repositorioPedidos,
        RepositorioInventario repositorioInventario,
        PasarelaSistecredito pasarela) {
      return new ProcesarNotificacionSistecredito(
          repositorioPagos, repositorioPedidos, repositorioInventario, pasarela, Instant::now);
    }
  }
}
