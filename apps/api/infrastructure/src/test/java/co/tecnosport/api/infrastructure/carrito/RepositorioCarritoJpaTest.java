package co.tecnosport.api.infrastructure.carrito;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.carrito.Carrito;
import java.time.Duration;
import java.time.Instant;
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

// @Transactional de clase: cada prueba en su propia transacción, revertida al terminar. No hay
// concurrencia que probar aquí (a diferencia de RepositorioInventarioJpaTest) así que sí se puede
// usar, igual que RepositorioProductosJpaTest.
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioCarritoJpaTest {

  private static final Instant AHORA = Instant.parse("2026-09-07T12:00:00Z");

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioCarritoJpa repositorio;

  @Test
  void guardarYBuscarUnCarritoAnonimoVacio() {
    Carrito carrito = Carrito.crear(null, Instant.now());

    repositorio.guardar(carrito);

    Optional<Carrito> encontrado = repositorio.buscarPorId(carrito.id());
    assertThat(encontrado).isPresent();
    assertThat(encontrado.orElseThrow().usuarioId()).isEmpty();
    assertThat(encontrado.orElseThrow().lineas()).isEmpty();
  }

  @Test
  void guardarUnCarritoDeUsuarioConservaElUsuarioId() {
    UUID usuarioId = UUID.randomUUID();
    Carrito carrito = Carrito.crear(usuarioId, Instant.now());

    repositorio.guardar(carrito);

    Carrito encontrado = repositorio.buscarPorId(carrito.id()).orElseThrow();
    assertThat(encontrado.usuarioId()).contains(usuarioId);
  }

  @Test
  void agregarUnaLineaYGuardarLaPersiste() {
    Carrito carrito = Carrito.crear(null, Instant.now());
    UUID varianteId = UUID.randomUUID();
    carrito.agregarLinea(varianteId, 2, AHORA);

    repositorio.guardar(carrito);

    Carrito encontrado = repositorio.buscarPorId(carrito.id()).orElseThrow();
    assertThat(encontrado.lineas()).hasSize(1);
    assertThat(encontrado.lineas().get(0).varianteId()).isEqualTo(varianteId);
    assertThat(encontrado.lineas().get(0).cantidad()).isEqualTo(2);
  }

  @Test
  void actualizarCantidadYGuardarReemplazaLoPersistido() {
    Carrito carrito = Carrito.crear(null, Instant.now());
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);
    repositorio.guardar(carrito);
    UUID lineaId = carrito.lineas().get(0).id();

    carrito.actualizarCantidad(lineaId, 9, AHORA);
    repositorio.guardar(carrito);

    Carrito encontrado = repositorio.buscarPorId(carrito.id()).orElseThrow();
    assertThat(encontrado.lineas()).hasSize(1);
    assertThat(encontrado.lineas().get(0).cantidad()).isEqualTo(9);
  }

  @Test
  void eliminarLineaYGuardarLaQuitaDeLoPersistido() {
    Carrito carrito = Carrito.crear(null, Instant.now());
    carrito.agregarLinea(UUID.randomUUID(), 1, AHORA);
    repositorio.guardar(carrito);
    UUID lineaId = carrito.lineas().get(0).id();

    carrito.eliminarLinea(lineaId, AHORA);
    repositorio.guardar(carrito);

    Carrito encontrado = repositorio.buscarPorId(carrito.id()).orElseThrow();
    assertThat(encontrado.lineas()).isEmpty();
  }

  @Test
  void dosCarritosNoSePisanLasLineasEntreSi() {
    Carrito carritoA = Carrito.crear(null, Instant.now());
    carritoA.agregarLinea(UUID.randomUUID(), 1, AHORA);
    repositorio.guardar(carritoA);

    Carrito carritoB = Carrito.crear(null, Instant.now());
    carritoB.agregarLinea(UUID.randomUUID(), 5, AHORA);
    carritoB.agregarLinea(UUID.randomUUID(), 3, AHORA);
    repositorio.guardar(carritoB);

    assertThat(repositorio.buscarPorId(carritoA.id()).orElseThrow().lineas()).hasSize(1);
    assertThat(repositorio.buscarPorId(carritoB.id()).orElseThrow().lineas()).hasSize(2);
  }

  @Test
  void unIdInexistenteNoSeEncuentra() {
    assertThat(repositorio.buscarPorId(UUID.randomUUID())).isEmpty();
  }

  /**
   * La fecha de actividad tiene que sobrevivir el viaje a Postgres: es la única que mira la purga,
   * y si el mapeo la perdiera se borrarían carritos vivos.
   */
  @Test
  void laFechaDeActividadVuelveDeLaBase() {
    Carrito carrito = Carrito.crear(null, AHORA);
    Instant despues = AHORA.plus(Duration.ofDays(20));
    carrito.agregarLinea(UUID.randomUUID(), 1, despues);

    repositorio.guardar(carrito);

    Carrito recuperado = repositorio.buscarPorId(carrito.id()).orElseThrow();
    assertThat(recuperado.actualizadoEn()).isEqualTo(despues);
    assertThat(recuperado.creadoEn()).isEqualTo(AHORA);
  }

  @Test
  void purgarBorraSoloLosCarritosSinActividadReciente() {
    Carrito vencido = Carrito.crear(null, AHORA.minus(Duration.ofDays(40)));
    Carrito vivo = Carrito.crear(null, AHORA.minus(Duration.ofDays(2)));
    repositorio.guardar(vencido);
    repositorio.guardar(vivo);

    int borrados = repositorio.eliminarInactivosDesde(AHORA.minus(Duration.ofDays(30)));

    assertThat(borrados).isEqualTo(1);
    assertThat(repositorio.buscarPorId(vencido.id())).isEmpty();
    assertThat(repositorio.buscarPorId(vivo.id())).isPresent();
  }

  /**
   * La cascada la declara el esquema (linea_carrito ... on delete cascade), no JPA: un delete de
   * JPQL no dispara la cascada del ORM. Si la del esquema no estuviera, este borrado fallaría con
   * violación de clave foránea en vez de llevarse las líneas.
   */
  @Test
  void purgarSeLlevaTambienLasLineasDelCarrito() {
    Carrito vencido = Carrito.crear(null, AHORA.minus(Duration.ofDays(40)));
    vencido.agregarLinea(UUID.randomUUID(), 2, AHORA.minus(Duration.ofDays(40)));
    repositorio.guardar(vencido);

    int borrados = repositorio.eliminarInactivosDesde(AHORA.minus(Duration.ofDays(30)));

    assertThat(borrados).isEqualTo(1);
    assertThat(repositorio.buscarPorId(vencido.id())).isEmpty();
  }

  /**
   * El caso que justifica la columna de actividad, contra Postgres real: creado hace 200 días pero
   * usado ayer, no se borra.
   */
  @Test
  void purgarNoSeLlevaUnCarritoViejoQueSeSigueUsando() {
    Carrito viejoPeroVivo = Carrito.crear(null, AHORA.minus(Duration.ofDays(200)));
    viejoPeroVivo.agregarLinea(UUID.randomUUID(), 1, AHORA.minus(Duration.ofDays(1)));
    repositorio.guardar(viejoPeroVivo);

    int borrados = repositorio.eliminarInactivosDesde(AHORA.minus(Duration.ofDays(30)));

    assertThat(borrados).isZero();
    assertThat(repositorio.buscarPorId(viejoPeroVivo.id())).isPresent();
  }
}
