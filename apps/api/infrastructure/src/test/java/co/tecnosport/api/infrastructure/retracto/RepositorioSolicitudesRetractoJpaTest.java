package co.tecnosport.api.infrastructure.retracto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.reintegro.MedioReintegro;
import co.tecnosport.api.domain.reintegro.MotivoReintegro;
import co.tecnosport.api.domain.reintegro.Reintegro;
import co.tecnosport.api.domain.retracto.CalendarioHabil;
import co.tecnosport.api.domain.retracto.EstadoSolicitudRetracto;
import co.tecnosport.api.domain.retracto.SolicitudRetracto;
import co.tecnosport.api.domain.retracto.VerdictoPlazo;
import co.tecnosport.api.infrastructure.pedido.RepositorioPedidosJpa;
import co.tecnosport.api.infrastructure.reintegro.RepositorioReintegrosJpa;
import co.tecnosport.api.infrastructure.retracto.entidad.SolicitudRetractoJpaEntity;
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
class RepositorioSolicitudesRetractoJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioSolicitudesRetractoJpa repositorio;
  @Autowired private RepositorioPedidosJpa pedidos;
  @Autowired private SolicitudRetractoJpaRepository jpa;
  @Autowired private RepositorioReintegrosJpa reintegros;

  private static final Instant ENTREGA = Instant.parse("2026-09-10T20:30:00Z");

  private UUID sembrarPedido() {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, (int) (System.nanoTime() % 100_000) + 1),
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
                    "https://cdn.tecnosport.co/img.webp",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            ENTREGA);
    pedidos.guardar(pedido);
    return pedido.id();
  }

  private SolicitudRetracto radicada(UUID pedidoId) {
    return SolicitudRetracto.radicar(
        pedidoId,
        ENTREGA,
        ENTREGA.plusSeconds(86_400),
        "admin:1",
        "no le sirvió la talla",
        CalendarioHabil.sinFestivosCargados());
  }

  @Test
  void guardaYRecuperaUnaSolicitudRecienRadicada() {
    UUID pedidoId = sembrarPedido();
    SolicitudRetracto solicitud = radicada(pedidoId);

    repositorio.guardar(solicitud);
    SolicitudRetracto recuperada = repositorio.buscarPorId(solicitud.id()).orElseThrow();

    assertThat(recuperada.pedidoId()).isEqualTo(pedidoId);
    assertThat(recuperada.estado()).isEqualTo(EstadoSolicitudRetracto.RADICADA);
    assertThat(recuperada.verdictoAlRadicar()).isEqualTo(VerdictoPlazo.EN_PLAZO);
    assertThat(recuperada.motivo()).contains("no le sirvió la talla");
    assertThat(recuperada.productoRecibidoEn()).isEmpty();
    assertThat(recuperada.reintegroId()).isEmpty();
  }

  @Test
  void elRecorridoCompletoSobreviveAlViajeDeIdaYVuelta() {
    UUID pedidoId = sembrarPedido();
    SolicitudRetracto solicitud = radicada(pedidoId);
    solicitud.recibirProducto(ENTREGA.plusSeconds(172_800));
    Reintegro reintegro =
        Reintegro.registrar(
            pedidoId,
            MotivoReintegro.RETRACTO,
            solicitud.id(),
            Dinero.deCop(50_000),
            MedioReintegro.TRANSFERENCIA_BANCARIA,
            "TRF-9912",
            ENTREGA.plusSeconds(259_200),
            "admin:1");
    solicitud.registrarReintegro(reintegro.id());
    reintegros.guardar(reintegro);

    repositorio.guardar(solicitud);
    jpa.flush();
    SolicitudRetracto recuperada = repositorio.buscarPorId(solicitud.id()).orElseThrow();

    assertThat(recuperada.estado()).isEqualTo(EstadoSolicitudRetracto.REEMBOLSADA);
    assertThat(recuperada.productoRecibidoEn()).contains(ENTREGA.plusSeconds(172_800));
    assertThat(recuperada.reintegroId()).contains(reintegro.id());
    Reintegro recuperado = reintegros.buscarPorId(reintegro.id()).orElseThrow();
    assertThat(recuperado.monto()).isEqualTo(Dinero.deCop(50_000));
    assertThat(recuperado.medio()).isEqualTo(MedioReintegro.TRANSFERENCIA_BANCARIA);
    assertThat(recuperado.motivo()).isEqualTo(MotivoReintegro.RETRACTO);
    assertThat(recuperado.origenId()).isEqualTo(solicitud.id());
    assertThat(recuperado.comprobante()).contains("TRF-9912");
    assertThat(recuperada.limiteDeReintegro()).isPresent();
  }

  @Test
  void lasSolicitudesDeUnPedidoLleganDeLaMasNuevaALaMasVieja() {
    UUID pedidoId = sembrarPedido();
    SolicitudRetracto primera = radicada(pedidoId);
    primera.transicionar(EstadoSolicitudRetracto.RECHAZADA);
    repositorio.guardar(primera);
    SolicitudRetracto segunda =
        SolicitudRetracto.radicar(
            pedidoId,
            ENTREGA,
            ENTREGA.plusSeconds(172_800),
            "admin:1",
            null,
            CalendarioHabil.sinFestivosCargados());
    repositorio.guardar(segunda);

    List<SolicitudRetracto> todas = repositorio.buscarPorPedidoId(pedidoId);

    assertThat(todas).hasSize(2);
    assertThat(todas.get(0).id()).isEqualTo(segunda.id());
    assertThat(todas.get(1).estado()).isEqualTo(EstadoSolicitudRetracto.RECHAZADA);
  }

  /**
   * La base también sostiene la invariante, no solo el dominio: una fila REEMBOLSADA sin el id de
   * su constancia no se puede rehidratar sin que el agregado reviente, y prefiero que reviente al
   * escribir. Es la invariante que sustituyó a la que daba tener el reembolso dentro de la fila. Se
   * escribe por debajo del repositorio, a propósito, porque por encima el dominio ya lo impide.
   */
  @Test
  void laBaseRechazaUnaSolicitudReembolsadaSinSuConstancia() {
    UUID pedidoId = sembrarPedido();

    assertThatThrownBy(
            () -> {
              jpa.save(
                  new SolicitudRetractoJpaEntity(
                      UUID.randomUUID(),
                      pedidoId,
                      ENTREGA,
                      "admin:1",
                      null,
                      VerdictoPlazo.EN_PLAZO.name(),
                      EstadoSolicitudRetracto.REEMBOLSADA.name(),
                      ENTREGA,
                      null));
              jpa.flush();
            })
        .hasMessageContaining("ck_retracto_reembolsada");
  }
}
