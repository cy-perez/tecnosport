package co.tecnosport.api.infrastructure.envio;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEnvio;
import co.tecnosport.api.domain.envio.EventoSeguimiento;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.infrastructure.envio.entidad.EnvioJpaEntity;
import co.tecnosport.api.infrastructure.envio.entidad.GuiaEnvioJpaEntity;
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
  @Autowired private GuiaEnvioJpaRepository guiaJpaRepository;

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

  /** Los envíos de estas pruebas llevan una guía salvo donde se diga lo contrario. */
  private static String unicaGuiaDe(Envio envio) {
    return envio.guias().getFirst().numero();
  }

  private static List<EventoSeguimiento> eventosDe(Envio envio) {
    return envio.guias().getFirst().eventos();
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
    Envio envio =
        Envio.crear(
            pedidoId,
            List.of(GuiaEnvio.crear("99 minutes", "NN-1", Dinero.deCop(10_540))),
            despacho);
    envio.registrarEvento(
        unicaGuiaDe(envio), evento(EstadoEnvio.ENTREGADO, "ev-3", despacho.plusSeconds(7200)));
    envio.registrarEvento(unicaGuiaDe(envio), evento(EstadoEnvio.RECOGIDO, "ev-1", despacho));
    envio.registrarEvento(
        unicaGuiaDe(envio), evento(EstadoEnvio.EN_TRANSITO, "ev-2", despacho.plusSeconds(3600)));

    repositorio.guardar(envio);

    Envio encontrado = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();
    assertThat(eventosDe(encontrado).stream().map(EventoSeguimiento::estado))
        .containsExactly(EstadoEnvio.RECOGIDO, EstadoEnvio.EN_TRANSITO, EstadoEnvio.ENTREGADO);
    assertThat(encontrado.ultimoEstado()).contains(EstadoEnvio.ENTREGADO);
    assertThat(eventosDe(encontrado).get(0).recibidoEn())
        .isEqualTo(despacho.plusSeconds(30))
        .isNotEqualTo(eventosDe(encontrado).get(0).ocurrioEn());
  }

  /**
   * El caso que estrena adr/0031: dos bultos son dos guías, cada una con su cobro y su rastro. Si
   * los eventos volvieran mezclados, el comprador leería que le entregaron un paquete que sigue en
   * camino.
   */
  @Test
  void unEnvioDeDosGuiasVuelveConCadaRastroEnSuSitio() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Instant despacho = Instant.parse("2026-09-10T14:00:00Z");
    Envio envio =
        Envio.crear(
            pedidoId,
            List.of(
                GuiaEnvio.crear("Servientrega", "SE-1", Dinero.deCop(8_200)),
                GuiaEnvio.crear("Coordinadora", "CO-2", Dinero.deCop(5_991))),
            despacho);
    envio.registrarEvento("SE-1", evento(EstadoEnvio.ENTREGADO, "ev-se", despacho));
    envio.registrarEvento("CO-2", evento(EstadoEnvio.EN_TRANSITO, "ev-co", despacho));

    repositorio.guardar(envio);

    Envio encontrado = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();
    assertThat(encontrado.guias()).hasSize(2);
    assertThat(encontrado.costoEnvio()).isEqualTo(Dinero.deCop(14_191));
    assertThat(encontrado.guiaDe("SE-1").orElseThrow().ultimoEstado())
        .contains(EstadoEnvio.ENTREGADO);
    assertThat(encontrado.guiaDe("CO-2").orElseThrow().ultimoEstado())
        .contains(EstadoEnvio.EN_TRANSITO);
    assertThat(encontrado.guiaDe("SE-1").orElseThrow().terminada()).isTrue();
    assertThat(encontrado.guiaDe("CO-2").orElseThrow().terminada()).isFalse();
  }

  /**
   * Cualquiera de las dos guías lleva al mismo envío: es como el webhook resuelve un evento, con el
   * número y nada más.
   */
  @Test
  void elEnvioSeEncuentraPorCualquieraDeSusGuias() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Envio envio =
        Envio.crear(
            pedidoId,
            List.of(
                GuiaEnvio.crear("Servientrega", "SE-1", Dinero.deCop(8_200)),
                GuiaEnvio.crear("Coordinadora", "CO-2", Dinero.deCop(5_991))),
            Instant.parse("2026-09-10T14:00:00Z"));
    repositorio.guardar(envio);

    assertThat(repositorio.buscarPorGuia("SE-1").orElseThrow().id()).isEqualTo(envio.id());
    assertThat(repositorio.buscarPorGuia("CO-2").orElseThrow().id()).isEqualTo(envio.id());
    assertThat(repositorio.buscarPorGuia("NO-EXISTE")).isEmpty();
  }

  /**
   * Con una guía entregada y otra viva, el envío sigue siendo de los que hay que preguntar. Medirlo
   * sobre el envío entero lo daría por terminado y dejaría la segunda sin conciliar para siempre.
   */
  @Test
  void unEnvioConUnaGuiaEntregadaYOtraVivaSigueEnLaConciliacion() {
    Instant corte = Instant.parse("2026-09-12T00:00:00Z");
    UUID pedidoId = sembrarPedidoContraentrega();
    Envio envio =
        Envio.crear(
            pedidoId,
            List.of(
                GuiaEnvio.crear("Servientrega", "SE-1", Dinero.deCop(8_200)),
                GuiaEnvio.crear("Coordinadora", "CO-2", Dinero.deCop(5_991))),
            corte.minusSeconds(86_400));
    envio.registrarEvento(
        "SE-1", evento(EstadoEnvio.ENTREGADO, "ev-se", corte.minusSeconds(90_000)));
    repositorio.guardar(envio);

    assertThat(repositorio.buscarSinEventosDesde(corte, 25).stream().map(Envio::id))
        .containsExactly(envio.id());
  }

  /**
   * Append-only de verdad: guardar dos veces no duplica ni reescribe. Es el caso del webhook que
   * reintenta, y de cualquier acción del panel sobre un envío que ya tenía rastro.
   */
  @Test
  void guardarDosVecesNoDuplicaNiPierdeEventos() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Instant despacho = Instant.parse("2026-09-10T14:00:00Z");
    Envio envio =
        Envio.crear(
            pedidoId,
            List.of(GuiaEnvio.crear("99 minutes", "NN-1", Dinero.deCop(10_540))),
            despacho);
    envio.registrarEvento(unicaGuiaDe(envio), evento(EstadoEnvio.RECOGIDO, "ev-1", despacho));
    repositorio.guardar(envio);

    Envio releido = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();
    releido.registrarEvento(
        unicaGuiaDe(releido), evento(EstadoEnvio.ENTREGADO, "ev-2", despacho.plusSeconds(3600)));
    repositorio.guardar(releido);
    repositorio.guardar(releido);

    Envio encontrado = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();
    assertThat(eventosDe(encontrado)).hasSize(2);
    assertThat(eventosDe(encontrado).stream().map(EventoSeguimiento::idExterno))
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
            List.of(GuiaEnvio.crear("99 minutes", "CALLADO", Dinero.deCop(10_540))),
            corte.minusSeconds(86_400));
    repositorio.guardar(callado);

    UUID pedidoReciente = sembrarPedidoContraentrega(2);
    Envio conEventoReciente =
        Envio.crear(
            pedidoReciente,
            List.of(GuiaEnvio.crear("99 minutes", "RECIENTE", Dinero.deCop(10_540))),
            corte.minusSeconds(86_400));
    conEventoReciente.registrarEvento(
        unicaGuiaDe(conEventoReciente),
        evento(
            EstadoEnvio.EN_TRANSITO, "ev-reciente", corte.plusSeconds(60), corte.plusSeconds(60)));
    repositorio.guardar(conEventoReciente);

    UUID pedidoEntregado = sembrarPedidoContraentrega(3);
    Envio yaEntregado =
        Envio.crear(
            pedidoEntregado,
            List.of(GuiaEnvio.crear("99 minutes", "ENTREGADO", Dinero.deCop(10_540))),
            corte.minusSeconds(172_800));
    yaEntregado.registrarEvento(
        unicaGuiaDe(yaEntregado),
        evento(
            EstadoEnvio.ENTREGADO,
            "ev-entregado",
            corte.minusSeconds(90_000),
            corte.minusSeconds(90_000)));
    repositorio.guardar(yaEntregado);

    UUID pedidoNuevo = sembrarPedidoContraentrega(4);
    repositorio.guardar(
        Envio.crear(
            pedidoNuevo,
            List.of(GuiaEnvio.crear("99 minutes", "NUEVO", Dinero.deCop(10_540))),
            corte.plusSeconds(600)));

    assertThat(
            repositorio.buscarSinEventosDesde(corte, 25).stream()
                .map(RepositorioEnviosJpaTest::unicaGuiaDe))
        .containsExactly("CALLADO");
  }

  /**
   * El tope existe porque cada fila que salga de aquí se convierte en una llamada al proveedor. Un
   * respaldo tras una caída del webhook no puede volverse mil llamadas seguidas.
   */
  @Test
  void elLoteDeCalladosRespetaElTope() {
    Instant corte = Instant.parse("2026-09-12T00:00:00Z");
    for (int i = 1; i <= 4; i++) {
      UUID pedidoId = sembrarPedidoContraentrega(i);
      repositorio.guardar(
          Envio.crear(
              pedidoId,
              List.of(GuiaEnvio.crear("99 minutes", "CALLADO-" + i, Dinero.deCop(10_540))),
              corte.minusSeconds(86_400L * i)));
    }

    assertThat(repositorio.buscarSinEventosDesde(corte, 2)).hasSize(2);
    assertThat(repositorio.buscarSinEventosDesde(corte, 25)).hasSize(4);
  }

  @Test
  void guardaUnEnvioConSusDatos() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Envio envio =
        Envio.crear(
            pedidoId,
            List.of(GuiaEnvio.crear("Servientrega", "SE123456", Dinero.deCop(15_000))),
            Instant.now());

    repositorio.guardar(envio);

    EnvioJpaEntity encontrado = envioJpaRepository.findById(envio.id()).orElseThrow();
    assertThat(encontrado.getPedidoId()).isEqualTo(pedidoId);
    GuiaEnvioJpaEntity guia = guiaJpaRepository.findByNumero("SE123456").orElseThrow();
    assertThat(guia.getEnvioId()).isEqualTo(envio.id());
    assertThat(guia.getTransportadora()).isEqualTo("Servientrega");
    assertThat(guia.getCostoEnvio()).isEqualByComparingTo(new BigDecimal("15000.00"));
  }

  @Test
  void buscarPorPedidoIdEncuentraElEnvioDelDespacho() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Envio envio =
        Envio.crear(
            pedidoId,
            List.of(GuiaEnvio.crear("Servientrega", "SE123456", Dinero.deCop(15_000))),
            Instant.now());
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
        Envio.crear(
            pedidoId,
            List.of(GuiaEnvio.crear("Servientrega", "SE123456", Dinero.deCop(15_000))),
            despachadoEn);
    repositorio.guardar(envio);

    Instant conciliadoEn = despachadoEn.plusSeconds(3600);
    envio.conciliarRecaudo(Dinero.deCop(5_000), conciliadoEn);
    repositorio.guardar(envio);

    Envio encontrado = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();
    assertThat(encontrado.comisionRecaudo()).contains(Dinero.deCop(5_000));
    assertThat(encontrado.recaudoConciliadoEn()).contains(conciliadoEn);
  }

  /**
   * El código de la transportadora va y vuelve, y su ausencia también. Son los dos casos reales:
   * una guía emitida por la plataforma lo trae, y una tecleada en el panel no — y de esa segunda
   * depende que la conciliación sepa que no tiene a quién preguntarle (adr/0022).
   */
  @Test
  void elCodigoDeTransportadoraVaYVuelveYPuedeFaltar() {
    UUID pedidoId = sembrarPedidoContraentrega();
    Instant despacho = Instant.parse("2026-09-10T14:00:00Z");
    Envio envio =
        Envio.crear(
            pedidoId,
            List.of(
                GuiaEnvio.crear("Servientrega", "servientrega", "SE-1", Dinero.deCop(8_200)),
                GuiaEnvio.crear("Coordinadora", "CO-2", Dinero.deCop(5_991))),
            despacho);

    repositorio.guardar(envio);

    Envio encontrado = repositorio.buscarPorPedidoId(pedidoId).orElseThrow();
    assertThat(encontrado.guiaDe("SE-1").orElseThrow().codigoTransportadora())
        .contains("servientrega");
    assertThat(encontrado.guiaDe("SE-1").orElseThrow().conciliable()).isTrue();
    assertThat(encontrado.guiaDe("CO-2").orElseThrow().codigoTransportadora()).isEmpty();
    assertThat(encontrado.guiaDe("CO-2").orElseThrow().conciliable()).isFalse();
  }
}
