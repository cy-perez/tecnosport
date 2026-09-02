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

  private TransactionTemplate transaccion;

  /** Inventario referencia una variante real (FK) — a diferencia de linea_carrito, a propósito. */
  private UUID variantePropia(String sku) {
    Instant ahora = Instant.now();
    MarcaJpaEntity marca =
        marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "Marca de prueba", ahora));
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
                0,
                null,
                "ACTIVA",
                ahora));
    return variante.getId();
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
