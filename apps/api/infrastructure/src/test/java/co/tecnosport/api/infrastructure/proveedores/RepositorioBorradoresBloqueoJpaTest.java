package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoSubida;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/**
 * El bloqueo de {@code buscarPorIdParaActualizar}, con dos transacciones de verdad.
 *
 * <p>Sin {@code @Transactional} de clase, por lo mismo que {@code
 * RepositorioCorreosPendientesJpaTest}: hay que ver lo que la otra transacción dejó comprometido.
 * El escenario es el que encontró la revisión: una confirmación de foto que leyó el borrador en
 * revisión mientras otra lo aprobaba, y al guardar lo devolvía a revisión.
 */
@SpringBootTest
@Testcontainers
class RepositorioBorradoresBloqueoJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  private static final Instant T = Instant.parse("2026-10-06T15:00:00Z");

  @Autowired private RepositorioProveedoresJpa proveedores;
  @Autowired private RepositorioLotesIngestaJpa lotes;
  @Autowired private RepositorioMensajesProveedorJpa mensajes;
  @Autowired private RepositorioPublicacionesProveedorJpa publicaciones;
  @Autowired private RepositorioBorradoresJpa borradores;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void quienLlegaSegundoEsperaYLeeLoQueDejoElPrimero() throws Exception {
    TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
    UUID id = transaccion.execute(estado -> unBorradorEnRevision());
    CountDownLatch primeroBloqueo = new CountDownLatch(1);
    CountDownLatch soltarPrimero = new CountDownLatch(1);

    try (ExecutorService ejecutor = Executors.newFixedThreadPool(2)) {
      // El primero: lo bloquea, espera la señal y lo rechaza.
      Future<?> primero =
          ejecutor.submit(
              () ->
                  transaccion.executeWithoutResult(
                      estado -> {
                        BorradorProducto borrador =
                            borradores.buscarPorIdParaActualizar(id).orElseThrow();
                        primeroBloqueo.countDown();
                        esperar(soltarPrimero);
                        borrador.rechazar("No es nuestro.");
                        borradores.actualizar(borrador);
                      }));
      esperar(primeroBloqueo);

      // El segundo: quiere subirle una foto. Tiene que quedarse esperando la fila.
      Future<EstadoBorrador> segundo =
          ejecutor.submit(
              () ->
                  transaccion.execute(
                      estado -> borradores.buscarPorIdParaActualizar(id).orElseThrow().estado()));
      Thread.sleep(500);
      assertThat(segundo.isDone()).as("el segundo espera el bloqueo").isFalse();

      soltarPrimero.countDown();
      primero.get(10, TimeUnit.SECONDS);
      // Lee lo comprometido por el primero, no lo que había cuando empezó a esperar: con eso el
      // caso de uso ve que ya no está en revisión y no le sube nada.
      assertThat(segundo.get(10, TimeUnit.SECONDS)).isEqualTo(EstadoBorrador.RECHAZADO);
    }
  }

  @Test
  void elBloqueoTraeElBorradorConSusFotosSubidas() {
    TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
    UUID id = transaccion.execute(estado -> unBorradorEnRevision());
    FotoSubida foto = new FotoSubida(UUID.randomUUID(), "p/borradores/" + id + "/1.jpg", T);
    transaccion.executeWithoutResult(
        estado -> {
          BorradorProducto borrador = borradores.buscarPorIdParaActualizar(id).orElseThrow();
          borrador.agregarFotoSubida(foto);
          borradores.actualizar(borrador);
        });

    BorradorProducto leido =
        transaccion.execute(estado -> borradores.buscarPorIdParaActualizar(id).orElseThrow());

    assertThat(leido.fotosSubidas()).containsExactly(foto);
  }

  private UUID unBorradorEnRevision() {
    Proveedor proveedor =
        Proveedor.crear(
            "Bolsos " + UUID.randomUUID(),
            LineaCatalogo.BOLSOS,
            "+57 300",
            "Bolsos " + UUID.randomUUID(),
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    proveedores.guardar(proveedor);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedor.id(), "p/exportaciones/a.zip", T);
    lotes.guardar(lote);
    MensajeProveedor principal =
        MensajeProveedor.texto(
            proveedor.id(), lote.id(), new IdExternoDeMensaje("p"), T, "Bolso 💰 53.000");
    mensajes.guardarTodos(List.of(principal));
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(principal);
    publicaciones.guardarTodas(List.of(publicacion));
    BorradorProducto borrador =
        BorradorProducto.nuevo(
            publicacion.id(),
            proveedor.id(),
            new ProductoExtraido(
                true,
                false,
                "Bolso",
                LineaCatalogo.BOLSOS,
                TipoProductoProveedor.BOLSO,
                Dinero.deCop(53000),
                null,
                1,
                null,
                null,
                "Bolso de dama.",
                null,
                false,
                new BigDecimal("0.9"),
                null),
            "{}",
            Dinero.deCop(53000),
            Dinero.deCop(70000),
            null,
            null,
            Set.of(),
            T);
    borradores.guardar(borrador);
    return borrador.id();
  }

  private static void esperar(CountDownLatch senal) {
    try {
      if (!senal.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("La señal no llegó.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
