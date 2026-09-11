package co.tecnosport.api.infrastructure.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.pedido.PedidosPaginados;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Propagation;
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

  private static final NumeroPedido NUMERO = NumeroPedido.de(2026, 1);

  private LineaPedido linea() {
    return new LineaPedido(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new Sku("TS-CAM-AZ-M"),
        "Camiseta running Dry-Fit",
        2,
        Dinero.deCop(50_000),
        new BigDecimal("0.19"),
        "https://cdn.tecnosport.co/img.jpg",
        UUID.randomUUID());
  }

  private Pedido pedidoAlDomicilio(MetodoPago metodoPago) {
    return Pedido.crear(
        NUMERO,
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
    assertThat(encontrado.numeroPedido()).isEqualTo(NUMERO);
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
            NUMERO,
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
  void tieneRechazoEnEntregaEsFalsoSinPedidosRechazados() {
    Pedido pedido = pedidoAlDomicilio(MetodoPago.CONTRAENTREGA);
    repositorio.guardar(pedido);

    assertThat(repositorio.tieneRechazoEnEntrega("cliente@tecnosport.co")).isFalse();
  }

  @Test
  void tieneRechazoEnEntregaEsVerdaderoTrasUnRechazo() {
    Pedido pedido = pedidoAlDomicilio(MetodoPago.CONTRAENTREGA);
    Instant ahora = Instant.now();
    pedido.transicionar(EstadoPedido.EN_PREPARACION, "sistema", "preparación", ahora);
    pedido.transicionar(EstadoPedido.DESPACHADO, "sistema", "despacho", ahora);
    pedido.transicionar(EstadoPedido.RECHAZADO_EN_ENTREGA, "sistema", "cliente no recibió", ahora);
    repositorio.guardar(pedido);

    assertThat(repositorio.tieneRechazoEnEntrega("cliente@tecnosport.co")).isTrue();
    assertThat(repositorio.tieneRechazoEnEntrega("otro@tecnosport.co")).isFalse();
  }

  @Test
  void unIdInexistenteNoSeEncuentra() {
    Optional<Pedido> encontrado = repositorio.buscarPorId(UUID.randomUUID());

    assertThat(encontrado).isEmpty();
  }

  private Pedido pedidoConNumero(int secuencial, MetodoPago metodoPago) {
    return Pedido.crear(
        NumeroPedido.de(2026, secuencial),
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
  void buscarTodosPaginadoSinFiltroOrdenaPorMasRecientePrimero() throws InterruptedException {
    Pedido primero = pedidoConNumero(101, MetodoPago.NEQUI);
    repositorio.guardar(primero);
    Thread.sleep(10);
    Pedido segundo = pedidoConNumero(102, MetodoPago.NEQUI);
    repositorio.guardar(segundo);

    PedidosPaginados resultado = repositorio.buscarTodosPaginado(0, 10, null);

    assertThat(resultado.totalPedidos()).isEqualTo(2);
    assertThat(resultado.items().get(0).id()).isEqualTo(segundo.id());
    assertThat(resultado.items().get(1).id()).isEqualTo(primero.id());
  }

  @Test
  void buscarTodosPaginadoFiltraPorEstadoYOrdenaPorMasAntiguoPrimero() throws InterruptedException {
    Pedido pendienteViejo = pedidoConNumero(103, MetodoPago.CONTRAENTREGA);
    pendienteViejo.transicionar(
        EstadoPedido.EN_PREPARACION, "admin:test", "verificado", Instant.now());
    pendienteViejo.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despachado", Instant.now());
    pendienteViejo.transicionar(EstadoPedido.ENTREGADO, "admin:test", "entregado", Instant.now());
    pendienteViejo.transicionar(
        EstadoPedido.RECAUDO_PENDIENTE, "admin:test", "recaudo pendiente", Instant.now());
    repositorio.guardar(pendienteViejo);
    Thread.sleep(10);
    Pedido pendienteReciente = pedidoConNumero(104, MetodoPago.CONTRAENTREGA);
    pendienteReciente.transicionar(
        EstadoPedido.EN_PREPARACION, "admin:test", "verificado", Instant.now());
    pendienteReciente.transicionar(
        EstadoPedido.DESPACHADO, "admin:test", "despachado", Instant.now());
    pendienteReciente.transicionar(
        EstadoPedido.ENTREGADO, "admin:test", "entregado", Instant.now());
    pendienteReciente.transicionar(
        EstadoPedido.RECAUDO_PENDIENTE, "admin:test", "recaudo pendiente", Instant.now());
    repositorio.guardar(pendienteReciente);
    repositorio.guardar(pedidoConNumero(105, MetodoPago.NEQUI));

    PedidosPaginados resultado =
        repositorio.buscarTodosPaginado(0, 10, EstadoPedido.RECAUDO_PENDIENTE);

    assertThat(resultado.totalPedidos()).isEqualTo(2);
    assertThat(resultado.items().get(0).id()).isEqualTo(pendienteViejo.id());
    assertThat(resultado.items().get(1).id()).isEqualTo(pendienteReciente.id());
  }

  @Test
  void siguienteNumeroFormateaYAvanzaElContador() {
    NumeroPedido primero = repositorio.siguienteNumero(2050);
    NumeroPedido segundo = repositorio.siguienteNumero(2050);

    assertThat(primero.valor()).isEqualTo("TS-2050-000001");
    assertThat(segundo.valor()).isEqualTo("TS-2050-000002");
  }

  @Test
  void anioDistintoArrancaSuPropioContadorEnUno() {
    repositorio.siguienteNumero(2051);
    NumeroPedido primeroDelSiguienteAnio = repositorio.siguienteNumero(2052);

    assertThat(primeroDelSiguienteAnio.valor()).isEqualTo("TS-2052-000001");
  }

  // Sin @Transactional de clase para este método (misma razón que
  // RepositorioInventarioJpaTest): cada hilo necesita que su llamada haga commit de verdad para
  // que la fila de secuencia_pedido quede serializada entre transacciones reales, no dentro de la
  // única transacción (nunca comprometida) del hilo principal.
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void siguienteNumeroEsAtomicoBajoConcurrencia() throws Exception {
    int anio = 2099;
    int hilos = 20;
    CountDownLatch listos = new CountDownLatch(hilos);
    Callable<Long> pedirNumero =
        () -> {
          listos.countDown();
          listos.await();
          return Long.parseLong(repositorio.siguienteNumero(anio).valor().substring(8));
        };

    ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
    List<Future<Long>> resultados;
    try {
      resultados = ejecutor.invokeAll(Collections.nCopies(hilos, pedirNumero));
    } finally {
      ejecutor.shutdown();
    }

    Set<Long> secuenciales = ConcurrentHashMap.newKeySet();
    for (Future<Long> resultado : resultados) {
      secuenciales.add(resultado.get());
    }

    Set<Long> esperados = LongStream.rangeClosed(1, hilos).boxed().collect(Collectors.toSet());
    assertThat(secuenciales).isEqualTo(esperados);
  }

  // --- El vigilante del plazo de entrega (ADR-0028) ---

  private Pedido pedidoCreadoEn(long secuencial, Instant creadoEn, MetodoPago metodoPago) {
    return Pedido.crear(
        NumeroPedido.de(2026, secuencial),
        null,
        new CorreoElectronico("cliente@tecnosport.co"),
        List.of(linea()),
        TipoEntrega.ENVIO_A_DOMICILIO,
        DIRECCION_MEDELLIN,
        metodoPago,
        "cliente@tecnosport.co",
        creadoEn);
  }

  @Test
  void elReclamoSeLeeDeVueltaEnElAgregado() {
    Instant hace40Dias = Instant.now().minus(40, java.time.temporal.ChronoUnit.DAYS);
    Pedido pedido = pedidoCreadoEn(700, hace40Dias, MetodoPago.CONTRAENTREGA);
    repositorio.guardar(pedido);
    Instant aviso = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

    assertThat(repositorio.reclamarAvisoDePlazo(pedido.id(), aviso)).isTrue();

    assertThat(repositorio.buscarPorId(pedido.id()).orElseThrow().avisoDePlazoEnviadoEn())
        .contains(aviso);
  }

  /** El segundo que lo intente pierde: es lo único que impide dos correos al mismo comprador. */
  @Test
  void soloElPrimerReclamoGana() {
    Pedido pedido =
        pedidoCreadoEn(
            701, Instant.now().minus(40, java.time.temporal.ChronoUnit.DAYS), MetodoPago.NEQUI);
    repositorio.guardar(pedido);
    Instant primero = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

    assertThat(repositorio.reclamarAvisoDePlazo(pedido.id(), primero)).isTrue();
    assertThat(repositorio.reclamarAvisoDePlazo(pedido.id(), primero.plusSeconds(3600))).isFalse();

    // Y la fecha sigue siendo la del primero: cuándo se avisó por primera vez es el dato que
    // importa si alguien reclama.
    assertThat(repositorio.buscarPorId(pedido.id()).orElseThrow().avisoDePlazoEnviadoEn())
        .contains(primero);
  }

  /**
   * La razón por la que la columna es {@code updatable = false}: quien despacha el pedido lo cargó
   * antes del reclamo, así que su copia en memoria lo tiene en nulo. Si {@code guardar} escribiera
   * esa columna, el reclamo desaparecería y el comprador recibiría el mismo correo otra vez.
   */
  @Test
  void guardarElPedidoNoPisaUnReclamoYaHecho() {
    Pedido pedido =
        pedidoCreadoEn(
            702, Instant.now().minus(40, java.time.temporal.ChronoUnit.DAYS), MetodoPago.NEQUI);
    repositorio.guardar(pedido);
    Instant aviso = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    repositorio.reclamarAvisoDePlazo(pedido.id(), aviso);

    // `pedido` es la copia vieja, sin aviso, como la tendría cualquier otra operación en vuelo.
    assertThat(pedido.avisoDePlazoEnviadoEn()).isEmpty();
    repositorio.guardar(pedido);

    // Se comprueba con otro reclamo y no leyendo el agregado, y la diferencia importa: el reclamo
    // es una sentencia masiva que va a la base de verdad, mientras que una lectura dentro de esta
    // misma transacción devolvería la copia que Hibernate tiene en memoria. Si `guardar` hubiera
    // pisado la columna, este segundo reclamo la encontraría en nulo y ganaría.
    assertThat(repositorio.reclamarAvisoDePlazo(pedido.id(), aviso.plusSeconds(60))).isFalse();
  }

  @Test
  void reclamarUnPedidoQueNoExisteNoGanaNada() {
    assertThat(repositorio.reclamarAvisoDePlazo(UUID.randomUUID(), Instant.now())).isFalse();
  }

  @Test
  void unPedidoNuevoSeGuardaSinAviso() {
    Pedido pedido = pedidoCreadoEn(701, Instant.now(), MetodoPago.CONTRAENTREGA);

    repositorio.guardar(pedido);

    assertThat(repositorio.buscarPorId(pedido.id()).orElseThrow().avisoDePlazoEnviadoEn())
        .isEmpty();
  }

  @Test
  void laConsultaDelVigilanteTraeLosViejosSinAvisoYEnLosEstadosPedidos() {
    Instant corte = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
    Instant viejo = corte.minus(10, java.time.temporal.ChronoUnit.DAYS);

    Pedido candidato = pedidoCreadoEn(710, viejo, MetodoPago.CONTRAENTREGA);
    repositorio.guardar(candidato);

    Pedido reciente = pedidoCreadoEn(711, Instant.now(), MetodoPago.CONTRAENTREGA);
    repositorio.guardar(reciente);

    Pedido yaAvisado = pedidoCreadoEn(712, viejo, MetodoPago.CONTRAENTREGA);
    repositorio.guardar(yaAvisado);
    repositorio.reclamarAvisoDePlazo(yaAvisado.id(), Instant.now());

    Pedido enOtroEstado = pedidoCreadoEn(713, viejo, MetodoPago.NEQUI);
    repositorio.guardar(enOtroEstado);

    List<Pedido> encontrados =
        repositorio.buscarSinAvisoDePlazo(
            Set.of(EstadoPedido.CONFIRMADO_CONTRAENTREGA, EstadoPedido.PAGADO), corte);

    assertThat(encontrados).extracting(Pedido::id).containsExactly(candidato.id());
  }

  @Test
  void laConsultaDelVigilanteReconstruyeElPedidoCompleto() {
    Instant viejo = Instant.now().minus(40, java.time.temporal.ChronoUnit.DAYS);
    Pedido guardado = pedidoCreadoEn(720, viejo, MetodoPago.CONTRAENTREGA);
    repositorio.guardar(guardado);

    Pedido encontrado =
        repositorio
            .buscarSinAvisoDePlazo(Set.of(EstadoPedido.CONFIRMADO_CONTRAENTREGA), Instant.now())
            .get(0);

    // El historial es lo que decide si el plazo venció: sin él, el vigilante no sabría desde cuándo
    // contar.
    assertThat(encontrado.historial()).hasSize(1);
    assertThat(encontrado.lineas()).hasSize(1);
    assertThat(encontrado.fechaDeInicioDelPlazoDeEntrega()).isPresent();
  }

  @Test
  void sinEstadosLaConsultaNoVaALaBase() {
    assertThat(repositorio.buscarSinAvisoDePlazo(Set.of(), Instant.now())).isEmpty();
  }
}
