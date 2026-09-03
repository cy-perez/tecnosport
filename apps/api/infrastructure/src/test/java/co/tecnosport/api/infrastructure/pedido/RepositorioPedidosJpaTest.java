package co.tecnosport.api.infrastructure.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
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

// @Transactional de clase: cada prueba en su propia transacción, revertida al terminar. Sin
// concurrencia que probar aquí (eso ya lo cubre RepositorioInventarioJpaTest), igual criterio que
// RepositorioCarritoJpaTest.
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioPedidosJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioPedidosJpa repositorio;

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private LineaPedido linea() {
    return new LineaPedido(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        2,
        Dinero.deCop(50_000),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.jpg");
  }

  private Pedido pedidoAlDomicilio(MetodoPago metodoPago) {
    return Pedido.crear(
        null,
        new CorreoElectronico("cliente@tecnosport.co"),
        List.of(linea()),
        TipoEntrega.ENVIO_A_DOMICILIO,
        DIRECCION_MEDELLIN,
        metodoPago,
        "cliente@tecnosport.co",
        Instant.now());
  }

  @Test
  void guardarYBuscarUnPedidoDeEnvioADomicilio() {
    Pedido pedido = pedidoAlDomicilio(MetodoPago.NEQUI);

    repositorio.guardar(pedido);

    Pedido encontrado = repositorio.buscarPorId(pedido.id()).orElseThrow();
    assertThat(encontrado.correo().valor()).isEqualTo("cliente@tecnosport.co");
    assertThat(encontrado.tipoEntrega()).isEqualTo(TipoEntrega.ENVIO_A_DOMICILIO);
    assertThat(encontrado.direccion()).contains(DIRECCION_MEDELLIN);
    assertThat(encontrado.metodoPago()).isEqualTo(MetodoPago.NEQUI);
    assertThat(encontrado.estado()).isEqualTo(EstadoPedido.PAGO_PENDIENTE);
    assertThat(encontrado.lineas()).hasSize(1);
    assertThat(encontrado.lineas().get(0).sku()).isEqualTo(new Sku("TS-CAM-AZ-M"));
    assertThat(encontrado.total()).isEqualTo(Dinero.deCop(100_000));
    assertThat(encontrado.historial()).hasSize(1);
  }

  @Test
  void guardarUnPedidoDeRetiroEnPuntoSinDireccion() {
    Pedido pedido =
        Pedido.crear(
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(linea()),
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.TARJETA,
            "cliente@tecnosport.co",
            Instant.now());

    repositorio.guardar(pedido);

    Pedido encontrado = repositorio.buscarPorId(pedido.id()).orElseThrow();
    assertThat(encontrado.tipoEntrega()).isEqualTo(TipoEntrega.RETIRO_EN_PUNTO);
    assertThat(encontrado.direccion()).isEmpty();
  }

  @Test
  void unPedidoDeContraentregaQuedaConfirmadoSinPagoPendiente() {
    Pedido pedido = pedidoAlDomicilio(MetodoPago.CONTRAENTREGA);

    repositorio.guardar(pedido);

    Pedido encontrado = repositorio.buscarPorId(pedido.id()).orElseThrow();
    assertThat(encontrado.estado()).isEqualTo(EstadoPedido.CONFIRMADO_CONTRAENTREGA);
  }

  @Test
  void transicionarYGuardarDeNuevoActualizaEstadoYAgregaHistorial() {
    Pedido pedido = pedidoAlDomicilio(MetodoPago.NEQUI);
    repositorio.guardar(pedido);

    pedido.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "pago aprobado", Instant.now());
    repositorio.guardar(pedido);

    Pedido encontrado = repositorio.buscarPorId(pedido.id()).orElseThrow();
    assertThat(encontrado.estado()).isEqualTo(EstadoPedido.PAGADO);
    assertThat(encontrado.historial()).hasSize(2);
    assertThat(encontrado.historial().get(1).motivo()).isEqualTo("pago aprobado");
  }

  @Test
  void unIdInexistenteNoSeEncuentra() {
    Optional<Pedido> encontrado = repositorio.buscarPorId(UUID.randomUUID());

    assertThat(encontrado).isEmpty();
  }
}
