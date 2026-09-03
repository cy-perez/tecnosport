package co.tecnosport.api.infrastructure.pago;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pago.EstadoPago;
import co.tecnosport.api.domain.pago.EventoPago;
import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.infrastructure.pedido.RepositorioPedidosJpa;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// @Transactional de clase: cada prueba en su propia transacción, revertida al terminar. Mismo
// criterio que RepositorioPedidosJpaTest.
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioPagosJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioPagosJpa repositorio;
  @Autowired private RepositorioPedidosJpa repositorioPedidos;

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");
  private static final NumeroPedido NUMERO = NumeroPedido.de(2026, 1);

  private UUID crearYGuardarPedido() {
    Pedido pedido =
        Pedido.crear(
            NUMERO,
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
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            Instant.now());
    repositorioPedidos.guardar(pedido);
    return pedido.id();
  }

  @Test
  void guardarYBuscarUnPagoPorReferencia() {
    UUID pedidoId = crearYGuardarPedido();
    ReferenciaPago referencia = new ReferenciaPago("TS-" + UUID.randomUUID());
    Pago pago =
        Pago.crear(pedidoId, referencia, MetodoPago.NEQUI, Dinero.deCop(100_000), Instant.now());

    repositorio.guardar(pago);

    Pago encontrado = repositorio.buscarPorReferencia(referencia).orElseThrow();
    assertThat(encontrado.pedidoId()).isEqualTo(pedidoId);
    assertThat(encontrado.metodoPago()).isEqualTo(MetodoPago.NEQUI);
    assertThat(encontrado.monto()).isEqualTo(Dinero.deCop(100_000));
    assertThat(encontrado.estado()).isEqualTo(EstadoPago.PENDIENTE);
    assertThat(encontrado.eventos()).isEmpty();
  }

  @Test
  void guardarConEventosLosPersisteEnOrden() {
    UUID pedidoId = crearYGuardarPedido();
    ReferenciaPago referencia = new ReferenciaPago("TS-" + UUID.randomUUID());
    Instant ahora = Instant.now();
    Pago pago = Pago.crear(pedidoId, referencia, MetodoPago.NEQUI, Dinero.deCop(100_000), ahora);
    pago.aplicarEvento(new EventoPago("evt-1", EstadoPago.APROBADO, ahora.plusSeconds(30)));

    repositorio.guardar(pago);

    Pago encontrado = repositorio.buscarPorReferencia(referencia).orElseThrow();
    assertThat(encontrado.estado()).isEqualTo(EstadoPago.APROBADO);
    assertThat(encontrado.eventos()).hasSize(1);
    assertThat(encontrado.eventos().get(0).idEvento()).isEqualTo("evt-1");
  }

  @Test
  void buscarPorPedidoIdDevuelveTodosLosIntentos() {
    UUID pedidoId = crearYGuardarPedido();
    Pago primero =
        Pago.crear(
            pedidoId,
            new ReferenciaPago("TS-" + UUID.randomUUID()),
            MetodoPago.NEQUI,
            Dinero.deCop(100_000),
            Instant.now());
    Pago segundo =
        Pago.crear(
            pedidoId,
            new ReferenciaPago("TS-" + UUID.randomUUID()),
            MetodoPago.NEQUI,
            Dinero.deCop(100_000),
            Instant.now());
    repositorio.guardar(primero);
    repositorio.guardar(segundo);

    List<Pago> encontrados = repositorio.buscarPorPedidoId(pedidoId);

    assertThat(encontrados).hasSize(2);
  }

  @Test
  void unaReferenciaInexistenteNoSeEncuentra() {
    Optional<Pago> encontrado = repositorio.buscarPorReferencia(new ReferenciaPago("no-existe"));

    assertThat(encontrado).isEmpty();
  }

  @Test
  void registrarIdTransaccionWompiSePersiste() {
    UUID pedidoId = crearYGuardarPedido();
    ReferenciaPago referencia = new ReferenciaPago("TS-" + UUID.randomUUID());
    Pago pago =
        Pago.crear(pedidoId, referencia, MetodoPago.NEQUI, Dinero.deCop(100_000), Instant.now());
    pago.registrarIdTransaccionWompi("1234-1610641025-49201");

    repositorio.guardar(pago);

    Pago encontrado = repositorio.buscarPorReferencia(referencia).orElseThrow();
    assertThat(encontrado.idTransaccionWompi()).contains("1234-1610641025-49201");
  }

  @Test
  void buscarPendientesParaConciliarSoloDevuelveLosQueCalifican() {
    UUID pedidoId = crearYGuardarPedido();
    Instant ahora = Instant.now();
    Instant viejo = ahora.minus(Duration.ofHours(1));
    Instant umbral = ahora.minus(Duration.ofMinutes(15));

    Pago califica =
        Pago.crear(
            pedidoId,
            new ReferenciaPago("TS-" + UUID.randomUUID()),
            MetodoPago.NEQUI,
            Dinero.deCop(100_000),
            viejo);
    califica.registrarIdTransaccionWompi("wompi-tx-califica");
    repositorio.guardar(califica);

    Pago sinId =
        Pago.crear(
            pedidoId,
            new ReferenciaPago("TS-" + UUID.randomUUID()),
            MetodoPago.NEQUI,
            Dinero.deCop(100_000),
            viejo);
    repositorio.guardar(sinId);

    Pago muyReciente =
        Pago.crear(
            pedidoId,
            new ReferenciaPago("TS-" + UUID.randomUUID()),
            MetodoPago.NEQUI,
            Dinero.deCop(100_000),
            ahora);
    muyReciente.registrarIdTransaccionWompi("wompi-tx-reciente");
    repositorio.guardar(muyReciente);

    Pago yaAprobado =
        Pago.crear(
            pedidoId,
            new ReferenciaPago("TS-" + UUID.randomUUID()),
            MetodoPago.NEQUI,
            Dinero.deCop(100_000),
            viejo);
    yaAprobado.registrarIdTransaccionWompi("wompi-tx-aprobado");
    yaAprobado.aplicarEvento(new EventoPago("evt-aprobado", EstadoPago.APROBADO, viejo));
    repositorio.guardar(yaAprobado);

    List<Pago> pendientes = repositorio.buscarPendientesParaConciliar(umbral);

    assertThat(pendientes)
        .extracting(p -> p.referencia().valor())
        .containsExactly(califica.referencia().valor());
  }
}
