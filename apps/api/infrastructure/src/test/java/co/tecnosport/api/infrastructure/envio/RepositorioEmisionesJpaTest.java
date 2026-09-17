package co.tecnosport.api.infrastructure.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataIntegrityViolationException;
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
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");
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

  @Test
  void laEmisionVuelveEnteraConSusEnviosEnOrden() {
    UUID pedidoId = sembrarPedido(1);
    repositorio.guardar(
        EmisionDeGuia.solicitada(
            pedidoId,
            "Servientrega",
            "rate-de-hoy",
            List.of("8bf880c9-5334-49bf-a006-036b3759d8e3", "da585a66-1f49-4f40-93e2-ecb58b239566"),
            AHORA));

    EmisionDeGuia leida = repositorio.buscarEnCursoDePedido(pedidoId).orElseThrow();

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
    EmisionDeGuia emision =
        EmisionDeGuia.solicitada(pedidoId, "Coordinadora", "rate", List.of("e47c61d3"), AHORA);
    repositorio.guardar(emision);

    emision.resolver(EstadoEmision.FALLIDA, "CARRIER_RESPONSE_ERROR", AHORA.plusSeconds(260));
    repositorio.guardar(emision);

    assertThat(repositorio.buscarEnCursoDePedido(pedidoId)).isEmpty();
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
    EmisionDeGuia emision =
        EmisionDeGuia.solicitada(
            pedidoId, "Servientrega", "rate", List.of("8bf880c9", "da585a66"), AHORA);
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
    repositorio.guardar(
        EmisionDeGuia.solicitada(pedidoId, "Servientrega", "rate-1", List.of("uno"), AHORA));

    // Revienta en el `saveAndFlush` del repositorio, no en un volcado posterior: la restricción es
    // de la base y salta en cuanto la fila intenta entrar.
    assertThatThrownBy(
            () ->
                repositorio.guardar(
                    EmisionDeGuia.solicitada(
                        pedidoId, "Servientrega", "rate-2", List.of("dos"), AHORA)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** Un fallo se reintenta: la plataforma reembolsa el intento muerto. */
  @Test
  void unPedidoSiPuedeAcumularVariasResueltas() {
    UUID pedidoId = sembrarPedido(5);
    EmisionDeGuia primera =
        EmisionDeGuia.solicitada(pedidoId, "Coordinadora", "rate-1", List.of("uno"), AHORA);
    repositorio.guardar(primera);
    primera.resolver(EstadoEmision.FALLIDA, "murió", AHORA.plusSeconds(260));
    repositorio.guardar(primera);

    EmisionDeGuia segunda =
        EmisionDeGuia.solicitada(
            pedidoId, "Servientrega", "rate-2", List.of("dos"), AHORA.plusSeconds(300));
    repositorio.guardar(segunda);
    entityManager.flush();

    assertThat(repositorio.buscarEnCursoDePedido(pedidoId).orElseThrow().idTarifa())
        .isEqualTo("rate-2");
  }

  /** De la más vieja a la más nueva, y acotado: cada envío es una llamada al proveedor. */
  @Test
  void lasEnCursoVuelvenDeLaMasViejaALaMasNuevaYAcotadas() {
    for (int i = 1; i <= 4; i++) {
      repositorio.guardar(
          EmisionDeGuia.solicitada(
              sembrarPedido(10 + i),
              "Servientrega",
              "rate-" + i,
              List.of("envio-" + i),
              AHORA.plusSeconds(i * 60L)));
    }

    List<EmisionDeGuia> lote = repositorio.buscarEnCurso(3);

    assertThat(lote).hasSize(3);
    assertThat(lote.stream().map(EmisionDeGuia::idTarifa))
        .containsExactly("rate-1", "rate-2", "rate-3");
  }
}
