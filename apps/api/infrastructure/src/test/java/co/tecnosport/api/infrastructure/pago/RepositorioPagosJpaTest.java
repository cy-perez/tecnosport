package co.tecnosport.api.infrastructure.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.application.pago.EventoDePagoYaRegistradoException;
import co.tecnosport.api.application.pago.ReferenciaDePagoYaExisteException;
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
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");
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
                    "https://cdn.tecnosport.co/img.jpg",
                    UUID.randomUUID())),
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

  /**
   * Un pago recién creado no sabe con qué se cobró, y eso tiene que sobrevivir la ida y vuelta a la
   * base: {@code null} es "todavía no se sabe" y no se puede confundir con "coincide".
   */
  @Test
  void unPagoSinMedioReportadoVuelveSinMedioReportado() {
    UUID pedidoId = crearYGuardarPedido();
    ReferenciaPago referencia = new ReferenciaPago("TS-" + UUID.randomUUID());
    Pago pago =
        Pago.crear(pedidoId, referencia, MetodoPago.NEQUI, Dinero.deCop(100_000), Instant.now());

    repositorio.guardar(pago);

    assertThat(
            repositorio.buscarPorReferencia(referencia).orElseThrow().medioReportadoPorLaPasarela())
        .isEmpty();
  }

  /**
   * La columna de la V39. Guarda el valor crudo de Wompi, no traducido: uno que hoy no sepamos
   * traducir tiene que quedar igual en vez de perderse en el mapeo.
   */
  @Test
  void elMedioReportadoPorLaPasarelaSobreviveLaIdaYVuelta() {
    UUID pedidoId = crearYGuardarPedido();
    ReferenciaPago referencia = new ReferenciaPago("TS-" + UUID.randomUUID());
    Pago pago =
        Pago.crear(pedidoId, referencia, MetodoPago.NEQUI, Dinero.deCop(100_000), Instant.now());
    pago.registrarMedioReportadoPorLaPasarela("BANCOLOMBIA_TRANSFER");

    repositorio.guardar(pago);

    Pago encontrado = repositorio.buscarPorReferencia(referencia).orElseThrow();
    assertThat(encontrado.medioReportadoPorLaPasarela()).contains("BANCOLOMBIA_TRANSFER");
    // El método elegido no se toca: son dos hechos distintos.
    assertThat(encontrado.metodoPago()).isEqualTo(MetodoPago.NEQUI);
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

  /**
   * <b>El índice único de {@code evento_pago} sale de aquí con nombre, no como un 500.</b> Lo que
   * lo dispara en producción son dos notificaciones simultáneas de la misma transacción: las dos
   * leen el pago pendiente, las dos lo aplican y la segunda choca al volcar. Pasó el 23 de
   * septiembre de 2026 en las dos corridas de prueba contra dev, y la pasarela recibió un 500 por
   * "Error inesperado sin manejar" — que es justo lo que la hace reintentar.
   *
   * <p><b>El límite de esta prueba, dicho en voz alta:</b> esa carrera necesita dos transacciones a
   * la vez y no se puede forzar desde una sola conexión sin dejar una prueba que a veces no choca.
   * Lo que se prueba aquí es lo mismo que la carrera produce —el mismo índice violado en el mismo
   * {@code guardar}— por el único camino determinista que hay: un pago que trae el mismo evento dos
   * veces. Si alguien quita la traducción, esto se vuelve rojo.
   */
  @Test
  void unEventoRepetidoChocaConNombrePropioYNoComoUnErrorDeJpa() {
    UUID pedidoId = crearYGuardarPedido();
    ReferenciaPago referencia = new ReferenciaPago("TS-" + UUID.randomUUID());
    EventoPago mismoEvento =
        new EventoPago("6ab40fd77705464e7c900c70:Approved", EstadoPago.APROBADO, Instant.now());
    Pago pagoConElEventoDosVeces =
        new Pago(
            UUID.randomUUID(),
            pedidoId,
            referencia,
            MetodoPago.SISTECREDITO,
            Dinero.deCop(100_000),
            EstadoPago.APROBADO,
            List.of(mismoEvento, mismoEvento),
            Instant.now(),
            Instant.now(),
            "6ab40fd77705464e7c900c70",
            "sistecredito");

    assertThatThrownBy(() -> repositorio.guardar(pagoConElEventoDosVeces))
        .isInstanceOf(EventoDePagoYaRegistradoException.class)
        .hasMessageContaining("6ab40fd77705464e7c900c70:Approved")
        .hasMessageContaining(referencia.valor());
  }

  /**
   * El hermano de la prueba de arriba, y el que faltaba. Dos peticiones de intento sobre el mismo
   * pedido calculan el mismo número —sale de contar los pagos— y construyen la misma referencia.
   * Esa violación es del {@code unique} de {@code pago}, no del de {@code evento_pago}, y salía con
   * el nombre del otro: {@code guardar} programaba el {@code insert} del pago con un {@code save} a
   * secas y quien lo ejecutaba era el {@code saveAllAndFlush} de los eventos, dentro del {@code
   * try}. Con eso, una referencia repetida se reportaba como "el evento X ya estaba registrado" con
   * un id "desconocido" —un 500 que manda a buscar algo que no existe— y por el webhook, donde
   * {@code PagoControlador} atrapa esa excepción, se contestaba <b>200 "ya procesado"</b> a una
   * escritura que había fallado.
   */
  @Test
  void unaReferenciaRepetidaChocaConSuPropioNombreYNoConElDelEvento() {
    UUID pedidoId = crearYGuardarPedido();
    ReferenciaPago referencia = new ReferenciaPago("TS-" + UUID.randomUUID());
    repositorio.guardar(
        new Pago(
            UUID.randomUUID(),
            pedidoId,
            referencia,
            MetodoPago.NEQUI,
            Dinero.deCop(100_000),
            EstadoPago.PENDIENTE,
            List.of(),
            Instant.now(),
            Instant.now(),
            null,
            null));

    // El segundo intento: otro pago, del mismo pedido, con la referencia que el otro ya ocupó.
    Pago elQuePerdioLaCarrera =
        new Pago(
            UUID.randomUUID(),
            pedidoId,
            referencia,
            MetodoPago.NEQUI,
            Dinero.deCop(100_000),
            EstadoPago.PENDIENTE,
            List.of(),
            Instant.now(),
            Instant.now(),
            null,
            null);

    assertThatThrownBy(() -> repositorio.guardar(elQuePerdioLaCarrera))
        .isInstanceOf(ReferenciaDePagoYaExisteException.class)
        .hasMessageContaining(referencia.valor());
  }

  @Test
  void registrarIdTransaccionPasarelaSePersiste() {
    UUID pedidoId = crearYGuardarPedido();
    ReferenciaPago referencia = new ReferenciaPago("TS-" + UUID.randomUUID());
    Pago pago =
        Pago.crear(pedidoId, referencia, MetodoPago.NEQUI, Dinero.deCop(100_000), Instant.now());
    pago.registrarIdTransaccionPasarela("1234-1610641025-49201");

    repositorio.guardar(pago);

    Pago encontrado = repositorio.buscarPorReferencia(referencia).orElseThrow();
    assertThat(encontrado.idTransaccionPasarela()).contains("1234-1610641025-49201");
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
    califica.registrarIdTransaccionPasarela("wompi-tx-califica");
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
    muyReciente.registrarIdTransaccionPasarela("wompi-tx-reciente");
    repositorio.guardar(muyReciente);

    Pago yaAprobado =
        Pago.crear(
            pedidoId,
            new ReferenciaPago("TS-" + UUID.randomUUID()),
            MetodoPago.NEQUI,
            Dinero.deCop(100_000),
            viejo);
    yaAprobado.registrarIdTransaccionPasarela("wompi-tx-aprobado");
    yaAprobado.aplicarEvento(new EventoPago("evt-aprobado", EstadoPago.APROBADO, viejo));
    repositorio.guardar(yaAprobado);

    List<Pago> pendientes = repositorio.buscarPendientesParaConciliar(umbral);

    assertThat(pendientes)
        .extracting(p -> p.referencia().valor())
        .containsExactly(califica.referencia().valor());
  }
}
