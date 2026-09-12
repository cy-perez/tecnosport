package co.tecnosport.api.infrastructure.envio;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
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
    return sembrarPedidoContraentrega(1);
  }

  /**
   * El número de pedido es único en la base, así que una prueba con varios pedidos necesita decir
   * cuál es cuál.
   */
  private UUID sembrarPedidoContraentrega(int secuencial) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, secuencial),
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

  private static EventoSeguimiento evento(
      EstadoEnvio estado, String idExterno, Instant ocurrioEn, Instant recibidoEn) {
    return new EventoSeguimiento(
        GeneradorIdentificador.nuevo(), estado, "en ruta", ocurrioEn, recibidoEn, idExterno);
  }

  private static EventoSeguimiento evento(EstadoEnvio estado, String idExterno, Instant ocurrioEn) {
    return new EventoSeguimiento(
        GeneradorIdentificador.nuevo(),
        estado,
        "en ruta",
        ocurrioEn,
        ocurrioEn.plusSeconds(30),
        idExterno);
  }

  /** Ida y vuelta del rastro completo: es lo que se lee el día de la reclamación (adr/0022). */
  @Test
  void losEventosVuelvenEnterosyEnOrden() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Instant despacho = Instant.parse("2026-09-10T14:00:00Z");
    Envio envio = Envio.crear(pedidoId, "99 minutes", "NN-1", Dinero.deCop(10_540), despacho);
    envio.registrarEvento(evento(EstadoEnvio.ENTREGADO, "ev-3", despacho.plusSeconds(7200)));
    envio.registrarEvento(evento(EstadoEnvio.RECOGIDO, "ev-1", despacho));
    envio.registrarEvento(evento(EstadoEnvio.EN_TRANSITO, "ev-2", despacho.plusSeconds(3600)));

    repositorio.guardar(envio);

    Envio encontrado = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();
    assertThat(encontrado.eventos().stream().map(EventoSeguimiento::estado))
        .containsExactly(EstadoEnvio.RECOGIDO, EstadoEnvio.EN_TRANSITO, EstadoEnvio.ENTREGADO);
    assertThat(encontrado.ultimoEstado()).contains(EstadoEnvio.ENTREGADO);
    assertThat(encontrado.eventos().get(0).recibidoEn())
        .isEqualTo(despacho.plusSeconds(30))
        .isNotEqualTo(encontrado.eventos().get(0).ocurrioEn());
  }

  /**
   * Append-only de verdad: guardar dos veces no duplica ni reescribe. Es el caso del webhook que
   * reintenta, y de cualquier acción del panel sobre un envío que ya tenía rastro.
   */
  @Test
  void guardarDosVecesNoDuplicaNiPierdeEventos() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Instant despacho = Instant.parse("2026-09-10T14:00:00Z");
    Envio envio = Envio.crear(pedidoId, "99 minutes", "NN-1", Dinero.deCop(10_540), despacho);
    envio.registrarEvento(evento(EstadoEnvio.RECOGIDO, "ev-1", despacho));
    repositorio.guardar(envio);

    Envio releido = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();
    releido.registrarEvento(evento(EstadoEnvio.ENTREGADO, "ev-2", despacho.plusSeconds(3600)));
    repositorio.guardar(releido);
    repositorio.guardar(releido);

    Envio encontrado = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();
    assertThat(encontrado.eventos()).hasSize(2);
    assertThat(encontrado.eventos().stream().map(EventoSeguimiento::idExterno))
        .containsExactly("ev-1", "ev-2");
  }

  /**
   * La consulta que alimenta la conciliación. Los dos {@code not exists} hacen cosas distintas y
   * las dos importan: uno deja fuera a los que acaban de hablar, el otro a los que ya terminaron su
   * historia — sin ese segundo, un paquete entregado se consultaría para siempre.
   */
  @Test
  void losCalladosSonLosDespachadosHaceRatoYSinEventoReciente() {
    Instant corte = Instant.parse("2026-09-12T00:00:00Z");
    UUID pedidoCallado = sembrarPedidoContraentrega(1);
    Envio callado =
        Envio.crear(
            pedidoCallado,
            "99 minutes",
            "CALLADO",
            Dinero.deCop(10_540),
            corte.minusSeconds(86_400));
    repositorio.guardar(callado);

    UUID pedidoReciente = sembrarPedidoContraentrega(2);
    Envio conEventoReciente =
        Envio.crear(
            pedidoReciente,
            "99 minutes",
            "RECIENTE",
            Dinero.deCop(10_540),
            corte.minusSeconds(86_400));
    conEventoReciente.registrarEvento(
        evento(
            EstadoEnvio.EN_TRANSITO, "ev-reciente", corte.plusSeconds(60), corte.plusSeconds(60)));
    repositorio.guardar(conEventoReciente);

    UUID pedidoEntregado = sembrarPedidoContraentrega(3);
    Envio yaEntregado =
        Envio.crear(
            pedidoEntregado,
            "99 minutes",
            "ENTREGADO",
            Dinero.deCop(10_540),
            corte.minusSeconds(172_800));
    yaEntregado.registrarEvento(
        evento(
            EstadoEnvio.ENTREGADO,
            "ev-entregado",
            corte.minusSeconds(90_000),
            corte.minusSeconds(90_000)));
    repositorio.guardar(yaEntregado);

    UUID pedidoNuevo = sembrarPedidoContraentrega(4);
    repositorio.guardar(
        Envio.crear(
            pedidoNuevo, "99 minutes", "NUEVO", Dinero.deCop(10_540), corte.plusSeconds(600)));

    assertThat(repositorio.buscarSinEventosDesde(corte).stream().map(Envio::guia))
        .containsExactly("CALLADO");
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
