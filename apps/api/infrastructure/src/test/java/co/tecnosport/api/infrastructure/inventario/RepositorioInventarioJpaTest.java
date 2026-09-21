package co.tecnosport.api.infrastructure.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.inventario.ExistenciaInsuficienteException;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.infrastructure.catalogo.CategoriaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.MarcaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.ProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import co.tecnosport.api.infrastructure.inventario.entidad.MovimientoInventarioJpaEntity;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Sin @Transactional de clase, a propósito: la prueba de concurrencia necesita que la siembra de
// cada caso COMMITEE de verdad, para que los hilos de la prueba (cada uno con su propia conexión)
// vean esa fila. @Transactional de prueba envolvería todo en una única transacción del hilo
// principal que nunca hace commit — los hilos de trabajo no verían nada. Cada variante de prueba
// usa un UUID propio, así que no hace falta el rollback automático para aislar los casos.
@SpringBootTest
@Testcontainers
class RepositorioInventarioJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioInventarioJpa repositorio;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private MarcaJpaRepository marcas;
  @Autowired private CategoriaJpaRepository categorias;
  @Autowired private ProductoJpaRepository productos;
  @Autowired private VarianteJpaRepository variantes;
  @Autowired private MovimientoInventarioJpaRepository movimientosJpa;

  private TransactionTemplate transaccion;

  /**
   * Inventario referencia una variante real (FK) — a diferencia de linea_carrito, a propósito.
   *
   * <p>El nombre de la marca lleva el {@code sku} pegado por la misma razón por la que ya lo
   * llevaba el slug de la categoría: esta clase no es {@code @Transactional}, así que las filas de
   * un método siguen ahí en el siguiente, y desde {@code V54__marcas_reales.sql} el nombre de la
   * marca es único. Antes de ese índice, cada método dejaba otra "Marca de prueba" en la tabla y
   * nadie se enteraba.
   */
  private UUID variantePropia(String sku) {
    Instant ahora = Instant.now();
    MarcaJpaEntity marca =
        marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Marca de prueba " + sku, ahora));
    CategoriaJpaEntity categoria =
        categorias.save(
            new CategoriaJpaEntity(UUID.randomUUID(), "Categoría de prueba", sku, "BOLSOS", ahora));
    ProductoJpaEntity producto =
        productos.save(
            new ProductoJpaEntity(
                UUID.randomUUID(),
                "Producto de prueba",
                sku,
                "",
                marca.getId(),
                categoria.getId(),
                "PUBLICADO",
                ahora,
                ahora));
    VarianteJpaEntity variante =
        variantes.save(
            new VarianteJpaEntity(
                UUID.randomUUID(),
                producto.getId(),
                sku,
                new BigDecimal("10000"),
                new BigDecimal("0.19"),
                null,
                180,
                30,
                25,
                4,
                "ACTIVA",
                ahora));
    return variante.getId();
  }

  /**
   * El listado del panel, contra Postgres de verdad. Lo que hay que demostrar es que cada libro
   * vuelve con <b>sus</b> movimientos y no con los del vecino: el agrupamiento en memoria por
   * {@code inventarioId} es justo el sitio donde eso se puede cruzar sin que nada reviente.
   *
   * <p>Y que vuelve sin bloquear: se llama fuera de toda transacción a propósito, que es como lo va
   * a llamar el controlador de una pantalla de solo lectura. Con {@code @Lock} encima esto fallaría
   * en seco con {@code TransactionRequiredException}, que es la lección que apps/api/CLAUDE.md dejó
   * escrita en la Fase 2.
   */
  @Test
  void listarTodosDevuelveCadaLibroConSusPropiosMovimientos() {
    transaccion = new TransactionTemplate(transactionManager);
    UUID unaVariante = variantePropia("SKU-INV-LISTAR-1");
    UUID otraVariante = variantePropia("SKU-INV-LISTAR-2");
    Instant ahora = Instant.now();

    transaccion.executeWithoutResult(
        estado -> {
          Inventario uno = Inventario.crear(unaVariante);
          uno.registrarEntrada(7, "siembra de prueba", ahora);
          uno.reservar(2, Duration.ofMinutes(30), ahora);
          repositorio.guardar(uno);

          Inventario otro = Inventario.crear(otraVariante);
          otro.registrarEntrada(3, "siembra de prueba", ahora);
          repositorio.guardar(otro);
        });

    List<Inventario> todos = repositorio.listarTodos();

    Inventario uno =
        todos.stream().filter(i -> i.varianteId().equals(unaVariante)).findFirst().orElseThrow();
    Inventario otro =
        todos.stream().filter(i -> i.varianteId().equals(otraVariante)).findFirst().orElseThrow();
    assertThat(uno.saldoTotal()).isEqualTo(7);
    assertThat(uno.saldoDisponible(ahora)).isEqualTo(5);
    assertThat(otro.saldoTotal()).isEqualTo(3);
    assertThat(otro.saldoDisponible(ahora)).isEqualTo(3);
    assertThat(otro.movimientos()).hasSize(1);
  }

  /**
   * Un libro recién creado y sin un solo movimiento vuelve igual, no se pierde por el agrupamiento.
   */
  @Test
  void listarTodosDevuelveTambienLosLibrosVacios() {
    transaccion = new TransactionTemplate(transactionManager);
    UUID varianteId = variantePropia("SKU-INV-LISTAR-3");

    transaccion.executeWithoutResult(estado -> repositorio.guardar(Inventario.crear(varianteId)));

    Inventario vacio =
        repositorio.listarTodos().stream()
            .filter(i -> i.varianteId().equals(varianteId))
            .findFirst()
            .orElseThrow();

    assertThat(vacio.movimientos()).isEmpty();
    assertThat(vacio.saldoTotal()).isZero();
  }

  /**
   * Lo mismo que el listado del panel, pero acotado: lo que hay que demostrar es que el filtro
   * <b>filtra</b> —el tercer libro existe y no puede venir en la respuesta— y que cada libro sigue
   * trayendo sus propios movimientos. Con el {@code IN} devolviendo de más, o con el agrupamiento
   * cruzado, la vitrina diría que hay existencia de algo que no la tiene.
   *
   * <p>Fuera de toda transacción, como lo va a llamar el catálogo público en cada página.
   */
  @Test
  void buscarPorVarianteIdsTraeSoloLosLibrosPedidosConSusMovimientos() {
    transaccion = new TransactionTemplate(transactionManager);
    UUID pedida = variantePropia("SKU-INV-LOTE-1");
    UUID tambienPedida = variantePropia("SKU-INV-LOTE-2");
    UUID ajena = variantePropia("SKU-INV-LOTE-3");
    Instant ahora = Instant.now();

    transaccion.executeWithoutResult(
        estado -> {
          Inventario una = Inventario.crear(pedida);
          una.registrarEntrada(7, "siembra de prueba", ahora);
          una.reservar(2, Duration.ofMinutes(30), ahora);
          repositorio.guardar(una);

          Inventario otra = Inventario.crear(tambienPedida);
          otra.registrarEntrada(3, "siembra de prueba", ahora);
          repositorio.guardar(otra);

          Inventario laDeNadie = Inventario.crear(ajena);
          laDeNadie.registrarEntrada(99, "siembra de prueba", ahora);
          repositorio.guardar(laDeNadie);
        });

    List<Inventario> libros = repositorio.buscarPorVarianteIds(List.of(pedida, tambienPedida));

    assertThat(libros)
        .extracting(Inventario::varianteId)
        .containsExactlyInAnyOrder(pedida, tambienPedida);
    Inventario una =
        libros.stream().filter(i -> i.varianteId().equals(pedida)).findFirst().orElseThrow();
    Inventario otra =
        libros.stream().filter(i -> i.varianteId().equals(tambienPedida)).findFirst().orElseThrow();
    assertThat(una.saldoTotal()).isEqualTo(7);
    assertThat(una.saldoDisponible(ahora)).isEqualTo(5);
    assertThat(otra.movimientos()).hasSize(1);
    assertThat(otra.saldoTotal()).isEqualTo(3);
  }

  /**
   * Una variante sin libro no vuelve con saldo cero: no vuelve. La diferencia importa porque quien
   * llama es el que decide qué significa esa ausencia, y para la vitrina significa agotado.
   *
   * <p>Y un conjunto vacío no llega a la base: {@code IN ()} no es SQL válido.
   */
  @Test
  void buscarPorVarianteIdsOmiteLasQueNoTienenLibroYAguantaElConjuntoVacio() {
    transaccion = new TransactionTemplate(transactionManager);
    UUID conLibro = variantePropia("SKU-INV-LOTE-4");
    UUID sinLibro = variantePropia("SKU-INV-LOTE-5");

    transaccion.executeWithoutResult(
        estado -> {
          Inventario libro = Inventario.crear(conLibro);
          libro.registrarEntrada(1, "siembra de prueba", Instant.now());
          repositorio.guardar(libro);
        });

    assertThat(repositorio.buscarPorVarianteIds(List.of(conLibro, sinLibro)))
        .extracting(Inventario::varianteId)
        .containsExactly(conLibro);
    assertThat(repositorio.buscarPorVarianteIds(List.of())).isEmpty();
  }

  @Test
  void reservarConfirmarYLiberarSobrevivenElViajeAJpa() {
    transaccion = new TransactionTemplate(transactionManager);
    UUID varianteId = variantePropia("SKU-INV-T1");

    transaccion.executeWithoutResult(
        estado -> {
          Inventario inventario = Inventario.crear(varianteId);
          inventario.registrarEntrada(5, "siembra de prueba", Instant.now());
          repositorio.guardar(inventario);
        });

    UUID[] idReserva = new UUID[1];
    transaccion.executeWithoutResult(
        estado -> {
          Inventario inventario = repositorio.buscarPorVarianteId(varianteId).orElseThrow();
          idReserva[0] = inventario.reservar(2, Duration.ofMinutes(30), Instant.now()).id();
          repositorio.guardar(inventario);
        });

    transaccion.executeWithoutResult(
        estado -> {
          Inventario inventario = repositorio.buscarPorVarianteId(varianteId).orElseThrow();
          assertThat(inventario.saldoTotal()).isEqualTo(5);
          assertThat(inventario.saldoDisponible(Instant.now())).isEqualTo(3);
        });

    transaccion.executeWithoutResult(
        estado -> {
          Inventario inventario = repositorio.buscarPorVarianteId(varianteId).orElseThrow();
          inventario.confirmar(idReserva[0], Instant.now());
          repositorio.guardar(inventario);
        });

    transaccion.executeWithoutResult(
        estado -> {
          Inventario inventario = repositorio.buscarPorVarianteId(varianteId).orElseThrow();
          assertThat(inventario.saldoTotal()).isEqualTo(3);
          assertThat(inventario.saldoDisponible(Instant.now())).isEqualTo(3);
        });
  }

  /**
   * {@code guardar} escribe solo los movimientos nuevos, y esta es la forma de comprobarlo desde
   * fuera: escribir el histórico entero significaba un {@code merge} por movimiento ya guardado, y
   * un {@code merge} vuelve a insertar la fila que ya no está. Sobre una tabla de solo-agregar eso
   * es resucitar en silencio algo que alguien borró.
   *
   * <p>Es además el único síntoma observable del defecto: el resto —mil seiscientas sentencias con
   * el bloqueo tomado para escribir una— solo se ve en el perfil, no en el resultado.
   */
  @Test
  void guardarNoReescribeElHistoricoNiResucitaUnMovimientoBorrado() {
    transaccion = new TransactionTemplate(transactionManager);
    UUID varianteId = variantePropia("SKU-INV-NUEVOS");
    Instant ahora = Instant.now();

    UUID inventarioId =
        transaccion.execute(
            estado -> {
              Inventario libro = Inventario.crear(varianteId);
              libro.registrarEntrada(5, "siembra de prueba", ahora);
              libro.registrarAjuste(2, "conteo de prueba", ahora);
              repositorio.guardar(libro);
              return libro.id();
            });

    Inventario cargado =
        transaccion.execute(estado -> repositorio.buscarPorVarianteId(varianteId).orElseThrow());
    assertThat(cargado.movimientos()).hasSize(2);
    assertThat(cargado.movimientosNuevos()).isEmpty();

    // Alguien borra a mano uno de los dos movimientos que este agregado tiene en memoria.
    UUID borrado = cargado.movimientos().get(0).id();
    transaccion.executeWithoutResult(estado -> movimientosJpa.deleteById(borrado));

    cargado.registrarAjuste(1, "conteo posterior", ahora);
    transaccion.executeWithoutResult(estado -> repositorio.guardar(cargado));

    List<MovimientoInventarioJpaEntity> enLaBase = movimientosJpa.findByInventarioId(inventarioId);
    assertThat(enLaBase).hasSize(2);
    assertThat(enLaBase.stream().map(MovimientoInventarioJpaEntity::getId)).doesNotContain(borrado);
  }

  @Test
  void abrirLibroConBloqueoLoCreaSiNoExisteYDevuelveElMismoSiYaEstaba() {
    transaccion = new TransactionTemplate(transactionManager);
    UUID varianteId = variantePropia("SKU-INV-ABRIR-1");
    Instant ahora = Instant.now();

    UUID primerId =
        transaccion.execute(estado -> repositorio.abrirLibroConBloqueo(varianteId).id());

    // Con el libro ya abierto y con movimientos, vuelve el mismo agregado y no uno nuevo.
    transaccion.executeWithoutResult(
        estado -> {
          Inventario libro = repositorio.abrirLibroConBloqueo(varianteId);
          libro.registrarEntrada(4, "siembra de prueba", ahora);
          repositorio.guardar(libro);
        });

    transaccion.executeWithoutResult(
        estado -> {
          Inventario libro = repositorio.abrirLibroConBloqueo(varianteId);
          assertThat(libro.id()).isEqualTo(primerId);
          assertThat(libro.saldoTotal()).isEqualTo(4);
        });
  }

  /**
   * La carrera que motivó el método. Antes, quien necesitaba el libro de una variante que no lo
   * tenía hacía {@code buscarPorVarianteId(id).orElseGet(() -> Inventario.crear(id))}, y esa rama
   * no sostiene ningún bloqueo: dos conteos simultáneos escribían dos agregados distintos contra
   * {@code ux_inventario_variante} y el que perdía moría con una violación de integridad.
   *
   * <p>Lo que se comprueba es que los dos hilos terminan y que queda <b>un solo</b> libro.
   */
  @Test
  void dosConteosSimultaneosSobreUnaVarianteSinLibroNoCreanDosLibros() throws Exception {
    transaccion = new TransactionTemplate(transactionManager);
    UUID varianteId = variantePropia("SKU-INV-ABRIR-2");

    CountDownLatch listos = new CountDownLatch(2);
    Callable<UUID> intento =
        () -> {
          listos.countDown();
          listos.await();
          return transaccion.execute(
              estado -> {
                Inventario libro = repositorio.abrirLibroConBloqueo(varianteId);
                libro.registrarAjuste(1, "conteo simultáneo", Instant.now());
                repositorio.guardar(libro);
                return libro.id();
              });
        };

    ExecutorService ejecutor = Executors.newFixedThreadPool(2);
    List<Future<UUID>> resultados;
    try {
      resultados = ejecutor.invokeAll(List.of(intento, intento));
    } finally {
      ejecutor.shutdown();
    }

    UUID primero = resultados.get(0).get();
    UUID segundo = resultados.get(1).get();
    assertThat(primero).isEqualTo(segundo);

    // Y el libro único quedó con los dos ajustes, no con uno: el segundo esperó al primero.
    transaccion.executeWithoutResult(
        estado -> {
          Inventario libro = repositorio.buscarPorVarianteId(varianteId).orElseThrow();
          assertThat(libro.saldoTotal()).isEqualTo(2);
        });
  }

  @Test
  void dosCompradoresSimultaneosPorLaUltimaUnidadSoloUnoGana() throws Exception {
    transaccion = new TransactionTemplate(transactionManager);
    UUID varianteId = variantePropia("SKU-INV-T2");

    transaccion.executeWithoutResult(
        estado -> {
          Inventario inventario = Inventario.crear(varianteId);
          inventario.registrarEntrada(1, "última unidad", Instant.now());
          repositorio.guardar(inventario);
        });

    CountDownLatch listos = new CountDownLatch(2);
    Callable<Boolean> intento = () -> intentarReservarUnaUnidad(varianteId, listos);

    ExecutorService ejecutor = Executors.newFixedThreadPool(2);
    List<Future<Boolean>> resultados;
    try {
      resultados = ejecutor.invokeAll(List.of(intento, intento));
    } finally {
      ejecutor.shutdown();
    }

    long exitosos = 0;
    for (Future<Boolean> resultado : resultados) {
      if (resultado.get()) {
        exitosos++;
      }
    }

    assertThat(exitosos).isEqualTo(1);
    transaccion.executeWithoutResult(
        estado -> {
          Inventario inventario = repositorio.buscarPorVarianteId(varianteId).orElseThrow();
          assertThat(inventario.saldoDisponible(Instant.now())).isEqualTo(0);
        });
  }

  /**
   * {@code true} si logró reservar; {@code false} si chocó con {@link
   * ExistenciaInsuficienteException}.
   */
  private boolean intentarReservarUnaUnidad(UUID varianteId, CountDownLatch listos) {
    listos.countDown();
    try {
      listos.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    try {
      transaccion.executeWithoutResult(
          estado -> {
            Inventario inventario = repositorio.buscarPorVarianteId(varianteId).orElseThrow();
            inventario.reservar(1, Duration.ofMinutes(30), Instant.now());
            repositorio.guardar(inventario);
          });
      return true;
    } catch (ExistenciaInsuficienteException e) {
      return false;
    }
  }
}
