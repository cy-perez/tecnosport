package co.tecnosport.api.infrastructure.envio;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.envio.TipoDeRevision;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.infrastructure.pedido.RepositorioPedidosJpa;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

/**
 * El acuse contra la base real. Lo que importa comprobar aquí es lo que un doble de prueba no puede
 * decir: que las dos columnas de referencia son excluyentes de verdad —lo impone una restricción de
 * la base, no el mapeador— y que acusar dos veces deja dos filas, porque el rastro es append-only.
 */
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioAcusesJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioAcusesJpa acuses;
  @Autowired private RepositorioAvisosJpa avisos;
  @Autowired private RepositorioEnviosJpa envios;
  @Autowired private RepositorioEmisionesJpa emisiones;
  @Autowired private RepositorioPedidosJpa pedidos;

  private static final Instant AHORA = Instant.parse("2026-09-17T15:00:00Z");

  private static final Direccion MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null);

  private UUID sembrarPedido(int secuencial) {
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
                    null,
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA.minusSeconds(86_400));
    pedidos.guardar(pedido);
    return pedido.id();
  }

  private GuiaEnvio sembrarGuia(int secuencial, String numero) {
    UUID pedidoId = sembrarPedido(secuencial);
    Envio envio =
        Envio.crear(
            pedidoId,
            List.of(GuiaEnvio.crear("Servientrega", numero, Dinero.deCop(8_200))),
            AHORA.minusSeconds(3600));
    envios.guardar(envio);
    return envio.guias().getFirst();
  }

  @Test
  void elAcuseDeUnaGuiaVaYVuelveConSuInstante() {
    GuiaEnvio guia = sembrarGuia(201, "AC-1");

    acuses.guardar(
        AcuseDeRevision.deGuia(guia.id(), "admin:7", "Reclamé a la transportadora.", AHORA));

    Map<UUID, Instant> ultimas = acuses.ultimaRevisionDe(TipoDeRevision.GUIA, List.of(guia.id()));
    assertThat(ultimas).containsEntry(guia.id(), AHORA);
  }

  /** Append-only: el segundo acuse no pisa al primero, y el que gana es el más reciente. */
  @Test
  void dosAcusesDeLaMismaGuiaDejanDosFilasYGanaElUltimo() {
    GuiaEnvio guia = sembrarGuia(202, "AC-2");

    acuses.guardar(AcuseDeRevision.deGuia(guia.id(), "admin:7", null, AHORA));
    acuses.guardar(AcuseDeRevision.deGuia(guia.id(), "admin:9", null, AHORA.plusSeconds(3600)));

    assertThat(acuses.ultimaRevisionDe(TipoDeRevision.GUIA, List.of(guia.id())))
        .containsEntry(guia.id(), AHORA.plusSeconds(3600));
  }

  /** Los dos tipos comparten tabla y no se mezclan: preguntar por uno no devuelve el otro. */
  @Test
  void elAcuseDeUnaEmisionNoAparecePreguntandoPorGuias() {
    UUID pedidoId = sembrarPedido(203);
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedidoId, "Coordinadora", "tarifa-1", "admin:7", AHORA);
    emision.indeterminada("la llamada no terminó", AHORA.plusSeconds(30));
    emisiones.guardar(emision);

    acuses.guardar(AcuseDeRevision.deEmision(emision.id(), "admin:7", null, AHORA));

    assertThat(acuses.ultimaRevisionDe(TipoDeRevision.EMISION, List.of(emision.id())))
        .containsEntry(emision.id(), AHORA);
    assertThat(acuses.ultimaRevisionDe(TipoDeRevision.GUIA, List.of(emision.id()))).isEmpty();
  }

  @Test
  void sinReferenciasNoPreguntaNada() {
    assertThat(acuses.ultimaRevisionDe(TipoDeRevision.GUIA, List.of())).isEmpty();
  }

  /** Las emisiones que piden ojo humano son las dos, y ninguna más. */
  @Test
  void laConsultaDeEmisionesTraeSoloIndeterminadasYParciales() {
    UUID conProblema = sembrarPedido(204);
    EmisionDeGuia indeterminada =
        EmisionDeGuia.solicitar(conProblema, "Coordinadora", "tarifa-1", "admin:7", AHORA);
    indeterminada.indeterminada("la llamada no terminó", AHORA.plusSeconds(30));
    emisiones.guardar(indeterminada);

    UUID sana = sembrarPedido(205);
    EmisionDeGuia emitida = EmisionDeGuia.solicitar(sana, "Envía", "tarifa-2", "admin:7", AHORA);
    emitida.aceptada(List.of("env-1"), AHORA);
    emitida.resolver(EstadoEmision.EMITIDA, null, AHORA.plusSeconds(60));
    emisiones.guardar(emitida);

    assertThat(emisiones.buscarQueExigenOjoHumano(50).stream().map(EmisionDeGuia::id))
        .containsExactly(indeterminada.id());
  }

  /**
   * El reclamo del aviso, que es lo que impide que el vigilante mande el mismo correo cada vuelta.
   * Se prueba contra la base real porque lo que decide es una sentencia con {@code on conflict}: un
   * doble puede imitar la condición, pero no que sea una sola escritura atómica.
   */
  @Test
  void elAvisoSeReclamaUnaSolaVezPorNovedad() {
    GuiaEnvio guia = sembrarGuia(206, "AV-1");
    Instant novedad = AHORA.minusSeconds(3600);

    assertThat(avisos.reclamarAviso(TipoDeRevision.GUIA, guia.id(), novedad, AHORA)).isTrue();
    assertThat(avisos.reclamarAviso(TipoDeRevision.GUIA, guia.id(), novedad, AHORA.plusSeconds(60)))
        .isFalse();
  }

  /**
   * Y vuelve a armarse con una novedad posterior: un paquete que empeora no puede pasar callado
   * solo porque ya se avisó de su estado anterior.
   */
  @Test
  void unaNovedadPosteriorVuelveAArmarElAviso() {
    GuiaEnvio guia = sembrarGuia(207, "AV-2");

    assertThat(
            avisos.reclamarAviso(TipoDeRevision.GUIA, guia.id(), AHORA.minusSeconds(7200), AHORA))
        .isTrue();
    assertThat(
            avisos.reclamarAviso(
                TipoDeRevision.GUIA, guia.id(), AHORA.plusSeconds(60), AHORA.plusSeconds(120)))
        .isTrue();
  }

  /** Avisar de una guía no dice nada de una emisión con el mismo identificador. */
  @Test
  void elAvisoDeUnaGuiaNoTapaElDeUnaEmision() {
    GuiaEnvio guia = sembrarGuia(208, "AV-3");
    Instant novedad = AHORA.minusSeconds(3600);

    assertThat(avisos.reclamarAviso(TipoDeRevision.GUIA, guia.id(), novedad, AHORA)).isTrue();
    assertThat(avisos.reclamarAviso(TipoDeRevision.EMISION, guia.id(), novedad, AHORA)).isTrue();
  }
}
