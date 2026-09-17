package co.tecnosport.api.infrastructure.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.envio.EmisionYaEnCursoException;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.envio.EmisionDeGuia;
import co.tecnosport.api.domain.envio.EstadoEmision;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.infrastructure.pedido.RepositorioPedidosJpa;
import jakarta.persistence.EntityManager;
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
class RepositorioEmisionesJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioEmisionesJpa repositorio;
  @Autowired private RepositorioPedidosJpa pedidos;
  @Autowired private EntityManager entityManager;

  private static final Direccion MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");
  private static final Instant AHORA = Instant.parse("2026-09-16T23:41:59Z");

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
                    new Sku("TS-CEL-1"),
                    "Celular de prueba",
                    1,
                    Dinero.deCop(120_000),
                    new BigDecimal("0.19"),
                    null,
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedidos.guardar(pedido);
    return pedido.id();
  }

  /** El camino normal: se pide, la plataforma acepta, y la fila queda con sus identificadores. */
  private static EmisionDeGuia enCurso(
      UUID pedidoId, String transportadora, String idTarifa, String... envios) {
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedidoId, transportadora, idTarifa, "admin:test", AHORA);
    emision.aceptada(List.of(envios), AHORA);
    return emision;
  }

  /**
   * La fila existe antes de que la plataforma cobre, sin identificadores todavía. Es lo que impide
   * que un reinicio entre el cobro y la respuesta deje una guía pagada sin nada que la nombre.
   */
  @Test
  void unaSolicitudSeGuardaSinEnviosYBloqueaIgual() {
    UUID pedidoId = sembrarPedido(20);
    repositorio.guardar(
        EmisionDeGuia.solicitar(pedidoId, "Servientrega", "rate", "admin:test", AHORA));

    EmisionDeGuia leida = repositorio.buscarAbiertaDePedido(pedidoId).orElseThrow();

    assertThat(leida.estado()).isEqualTo(EstadoEmision.SOLICITADA);
    assertThat(leida.enviosEnPlataforma()).isEmpty();
    assertThat(leida.idTarifa()).isEqualTo("rate");
    assertThat(leida.actor()).isEqualTo("admin:test");
  }

  /** Una indeterminada tampoco deja emitir: pudo cobrarse y nadie lo sabe todavía. */
  @Test
  void unaIndeterminadaSigueBloqueando() {
    UUID pedidoId = sembrarPedido(21);
    EmisionDeGuia emision =
        EmisionDeGuia.solicitar(pedidoId, "Servientrega", "rate", "admin:test", AHORA);
    repositorio.guardar(emision);
    emision.indeterminada("408", AHORA.plusSeconds(40));
    repositorio.guardar(emision);

    assertThat(repositorio.buscarAbiertaDePedido(pedidoId).orElseThrow().estado())
        .isEqualTo(EstadoEmision.INDETERMINADA);
    assertThatThrownBy(() -> repositorio.guardar(enCurso(pedidoId, "Envía", "rate-2", "otro")))
        .isInstanceOf(EmisionYaEnCursoException.class);
  }

  /** Las que nunca registraron respuesta: el proceso murió en la mitad. */
  @Test
  void lasSolicitudesViejasSeEncuentranParaAbandonarlas() {
    UUID viejo = sembrarPedido(22);
    UUID reciente = sembrarPedido(23);
    repositorio.guardar(
        EmisionDeGuia.solicitar(viejo, "Servientrega", "rate-viejo", "admin:test", AHORA));
    repositorio.guardar(
        EmisionDeGuia.solicitar(
            reciente, "Servientrega", "rate-nuevo", "admin:test", AHORA.plusSeconds(3_600)));

    List<EmisionDeGuia> encontradas =
        repositorio.buscarSolicitadasAntesDe(AHORA.plusSeconds(600), 10);

    assertThat(encontradas).hasSize(1);
    assertThat(encontradas.getFirst().idTarifa()).isEqualTo("rate-viejo");
  }

  @Test
  void laEmisionVuelveEnteraConSusEnviosEnOrden() {
    UUID pedidoId = sembrarPedido(1);
    repositorio.guardar(
        enCurso(
            pedidoId,
            "Servientrega",
            "rate-de-hoy",
            "8bf880c9-5334-49bf-a006-036b3759d8e3",
            "da585a66-1f49-4f40-93e2-ecb58b239566"));

    EmisionDeGuia leida = repositorio.buscarAbiertaDePedido(pedidoId).orElseThrow();

    assertThat(leida.transportadora()).isEqualTo("Servientrega");
    assertThat(leida.idTarifa()).isEqualTo("rate-de-hoy");
    assertThat(leida.estado()).isEqualTo(EstadoEmision.EN_CURSO);
    assertThat(leida.solicitadaEn()).isEqualTo(AHORA);
    assertThat(leida.resueltaEn()).isEmpty();
    // El orden es el de los bultos: es lo que permite decir cuál guía es cuál paquete.
    assertThat(leida.enviosEnPlataforma())
        .containsExactly(
            "8bf880c9-5334-49bf-a006-036b3759d8e3", "da585a66-1f49-4f40-93e2-ecb58b239566");
  }

  @Test
  void resolverlaGuardaElEstadoElDetalleYLaFecha() {
    UUID pedidoId = sembrarPedido(2);
    EmisionDeGuia emision = enCurso(pedidoId, "Coordinadora", "rate", "e47c61d3");
    repositorio.guardar(emision);

    emision.resolver(EstadoEmision.FALLIDA, "CARRIER_RESPONSE_ERROR", AHORA.plusSeconds(260));
    repositorio.guardar(emision);

    assertThat(repositorio.buscarAbiertaDePedido(pedidoId)).isEmpty();
    assertThat(repositorio.buscarEnCurso(10)).isEmpty();
  }

  /**
   * Los identificadores de la plataforma se escriben una vez y no se reescriben al resolver: son el
   * único rastro de que se comprometió plata, y borrarlos y volver a insertarlos en cada guardado
   * abriría una ventana para perderlos.
   */
  @Test
  void resolverlaNoTocaLosEnviosDeLaPlataforma() {
    UUID pedidoId = sembrarPedido(3);
    EmisionDeGuia emision = enCurso(pedidoId, "Servientrega", "rate", "8bf880c9", "da585a66");
    repositorio.guardar(emision);

    emision.resolver(EstadoEmision.PARCIAL, "una viva, una muerta", AHORA.plusSeconds(30));
    repositorio.guardar(emision);
    entityManager.flush();
    entityManager.clear();

    EmisionDeGuia leida = repositorio.buscarEnCurso(10).stream().findFirst().orElse(null);
    assertThat(leida).isNull();
    assertThat(
            entityManager
                .createQuery(
                    "select count(e) from EnvioEnPlataformaJpaEntity e where e.emisionId = :id",
                    Long.class)
                .setParameter("id", emision.id())
                .getSingleResult())
        .isEqualTo(2L);
  }

  /**
   * La puerta que cuesta plata, y la de verdad no es la del caso de uso sino esta: la plataforma
   * cobra al crear, y entre leer "no hay ninguna en curso" y escribir la nueva cabe un segundo clic
   * en el panel. Índice único parcial, porque un pedido sí puede acumular varias resueltas.
   */
  @Test
  void unPedidoNoPuedeTenerDosEmisionesAbiertas() {
    UUID pedidoId = sembrarPedido(4);
    repositorio.guardar(enCurso(pedidoId, "Servientrega", "rate-1", "uno"));

    // Y la violación de unicidad NO sale cruda: el repositorio la traduce, porque una excepción de
    // JPA que se escapa de infrastructure termina en un 500 generico que no explica nada.
    assertThatThrownBy(
            () -> repositorio.guardar(enCurso(pedidoId, "Servientrega", "rate-2", "dos")))
        .isInstanceOf(EmisionYaEnCursoException.class);
  }

  /** Un fallo se reintenta: la plataforma reembolsa el intento muerto. */
  @Test
  void unPedidoSiPuedeAcumularVariasResueltas() {
    UUID pedidoId = sembrarPedido(5);
    EmisionDeGuia primera = enCurso(pedidoId, "Coordinadora", "rate-1", "uno");
    repositorio.guardar(primera);
    primera.resolver(EstadoEmision.FALLIDA, "murió", AHORA.plusSeconds(260));
    repositorio.guardar(primera);

    EmisionDeGuia segunda = enCurso(pedidoId, "Servientrega", "rate-2", "dos");
    repositorio.guardar(segunda);
    entityManager.flush();

    assertThat(repositorio.buscarAbiertaDePedido(pedidoId).orElseThrow().idTarifa())
        .isEqualTo("rate-2");
    assertThat(repositorio.buscarDePedido(pedidoId)).hasSize(2);
  }

  /** De la más vieja a la más nueva, y acotado: cada envío es una llamada al proveedor. */
  @Test
  void lasEnCursoVuelvenDeLaMasViejaALaMasNuevaYAcotadas() {
    for (int i = 1; i <= 4; i++) {
      EmisionDeGuia emision =
          EmisionDeGuia.solicitar(
              sembrarPedido(10 + i),
              "Servientrega",
              "rate-" + i,
              "admin:test",
              AHORA.plusSeconds(i * 60L));
      emision.aceptada(List.of("envio-" + i), AHORA.plusSeconds(i * 60L));
      repositorio.guardar(emision);
    }

    List<EmisionDeGuia> lote = repositorio.buscarEnCurso(3);

    assertThat(lote).hasSize(3);
    assertThat(lote.stream().map(EmisionDeGuia::idTarifa))
        .containsExactly("rate-1", "rate-2", "rate-3");
  }
}
