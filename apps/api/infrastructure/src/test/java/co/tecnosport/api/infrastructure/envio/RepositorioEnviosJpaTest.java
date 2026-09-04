package co.tecnosport.api.infrastructure.envio;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.infrastructure.envio.entidad.EnvioJpaEntity;
import co.tecnosport.api.infrastructure.pedido.RepositorioPedidosJpa;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

@SpringBootTest
@Testcontainers
@Transactional
class RepositorioEnviosJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioEnviosJpa repositorio;
  @Autowired private RepositorioPedidosJpa pedidos;
  @Autowired private EnvioJpaRepository envioJpaRepository;

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private UUID sembrarPedidoContraentrega() {
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
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.jpg",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            Instant.now());
    pedidos.guardar(pedido);
    return pedido.id();
  }

  @Test
  void guardaUnEnvioConSusDatos() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Envio envio =
        Envio.crear(pedidoId, "Servientrega", "SE123456", Dinero.deCop(15_000), Instant.now());

    repositorio.guardar(envio);

    EnvioJpaEntity encontrado = envioJpaRepository.findById(envio.id()).orElseThrow();
    assertThat(encontrado.getPedidoId()).isEqualTo(pedidoId);
    assertThat(encontrado.getTransportadora()).isEqualTo("Servientrega");
    assertThat(encontrado.getGuia()).isEqualTo("SE123456");
    assertThat(encontrado.getCostoEnvio()).isEqualByComparingTo(new BigDecimal("15000.00"));
  }

  @Test
  void buscarPorPedidoIdEncuentraElEnvioDelDespacho() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Envio envio =
        Envio.crear(pedidoId, "Servientrega", "SE123456", Dinero.deCop(15_000), Instant.now());
    repositorio.guardar(envio);

    Envio encontrado = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();

    assertThat(encontrado.id()).isEqualTo(envio.id());
    assertThat(encontrado.comisionRecaudo()).isEmpty();
    assertThat(encontrado.recaudoConciliadoEn()).isEmpty();
  }

  @Test
  void unPedidoSinEnvioNoSeEncuentra() {
    assertThat(repositorio.buscarPorPedidoId(UUID.randomUUID())).isEmpty();
  }

  @Test
  void conciliarElRecaudoPersisteLaComisionYLaFecha() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Instant despachadoEn = Instant.now();
    Envio envio =
        Envio.crear(pedidoId, "Servientrega", "SE123456", Dinero.deCop(15_000), despachadoEn);
    repositorio.guardar(envio);

    Instant conciliadoEn = despachadoEn.plusSeconds(3600);
    envio.conciliarRecaudo(Dinero.deCop(5_000), conciliadoEn);
    repositorio.guardar(envio);

    Envio encontrado = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();
    assertThat(encontrado.comisionRecaudo()).contains(Dinero.deCop(5_000));
    assertThat(encontrado.recaudoConciliadoEn()).contains(conciliadoEn);
  }
}
