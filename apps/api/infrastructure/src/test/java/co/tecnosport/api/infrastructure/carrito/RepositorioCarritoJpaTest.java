package co.tecnosport.api.infrastructure.carrito;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.carrito.Carrito;
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
    carrito.agregarLinea(varianteId, 2);

    repositorio.guardar(carrito);

    Carrito encontrado = repositorio.buscarPorId(carrito.id()).orElseThrow();
    assertThat(encontrado.lineas()).hasSize(1);
    assertThat(encontrado.lineas().get(0).varianteId()).isEqualTo(varianteId);
    assertThat(encontrado.lineas().get(0).cantidad()).isEqualTo(2);
  }

  @Test
  void actualizarCantidadYGuardarReemplazaLoPersistido() {
    Carrito carrito = Carrito.crear(null, Instant.now());
    carrito.agregarLinea(UUID.randomUUID(), 1);
    repositorio.guardar(carrito);
    UUID lineaId = carrito.lineas().get(0).id();

    carrito.actualizarCantidad(lineaId, 9);
    repositorio.guardar(carrito);

    Carrito encontrado = repositorio.buscarPorId(carrito.id()).orElseThrow();
    assertThat(encontrado.lineas()).hasSize(1);
    assertThat(encontrado.lineas().get(0).cantidad()).isEqualTo(9);
  }

  @Test
  void eliminarLineaYGuardarLaQuitaDeLoPersistido() {
    Carrito carrito = Carrito.crear(null, Instant.now());
    carrito.agregarLinea(UUID.randomUUID(), 1);
    repositorio.guardar(carrito);
    UUID lineaId = carrito.lineas().get(0).id();

    carrito.eliminarLinea(lineaId);
    repositorio.guardar(carrito);

    Carrito encontrado = repositorio.buscarPorId(carrito.id()).orElseThrow();
    assertThat(encontrado.lineas()).isEmpty();
  }

  @Test
  void dosCarritosNoSePisanLasLineasEntreSi() {
    Carrito carritoA = Carrito.crear(null, Instant.now());
    carritoA.agregarLinea(UUID.randomUUID(), 1);
    repositorio.guardar(carritoA);

    Carrito carritoB = Carrito.crear(null, Instant.now());
    carritoB.agregarLinea(UUID.randomUUID(), 5);
    carritoB.agregarLinea(UUID.randomUUID(), 3);
    repositorio.guardar(carritoB);

    assertThat(repositorio.buscarPorId(carritoA.id()).orElseThrow().lineas()).hasSize(1);
    assertThat(repositorio.buscarPorId(carritoB.id()).orElseThrow().lineas()).hasSize(2);
  }

  @Test
  void unIdInexistenteNoSeEncuentra() {
    assertThat(repositorio.buscarPorId(UUID.randomUUID())).isEmpty();
  }
}
