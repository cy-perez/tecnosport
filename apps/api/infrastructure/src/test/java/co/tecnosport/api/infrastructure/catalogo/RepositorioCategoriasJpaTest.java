package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
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

@SpringBootTest
@Testcontainers
@Transactional
class RepositorioCategoriasJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioCategoriasJpa repositorio;
  @Autowired private CategoriaJpaRepository categorias;

  /**
   * Afirma el <b>orden</b> y la <b>presencia</b>, no el contenido exacto de la tabla. Usaba {@code
   * containsExactly} con las dos filas que ella misma crea, lo que daba por hecho que la tabla
   * arranca vacía — y dejó de ser cierto cuando {@code V38__linea_tecnologia.sql} pasó a sembrar
   * las diez categorías tecnológicas, que son dato real del negocio y no siembra de desarrollo.
   *
   * <p>Una prueba de repositorio no debería depender de que nadie más haya escrito en la tabla: lo
   * que tiene que demostrar es que {@code listarTodas} devuelve lo que hay, ordenado por nombre.
   */
  @Test
  void listarTodasDevuelveLasCategoriasOrdenadasPorNombre() {
    categorias.save(
        new CategoriaJpaEntity(
            UUID.randomUUID(), "Zzz última", "zzz-ultima-tc1", "TECNOLOGIA", Instant.now()));
    categorias.save(
        new CategoriaJpaEntity(
            UUID.randomUUID(), "Aaa primera", "aaa-primera-tc1", "BOLSOS", Instant.now()));

    List<Categoria> resultado = repositorio.listarTodas();

    assertThat(resultado).extracting(Categoria::nombre).contains("Aaa primera", "Zzz última");
    assertThat(resultado).extracting(Categoria::nombre).isSorted();
    assertThat(resultado)
        .extracting(Categoria::linea)
        .contains(LineaCatalogo.BOLSOS, LineaCatalogo.TECNOLOGIA);
  }

  @Test
  void buscarPorIdDevuelveVacioSiNoExiste() {
    Optional<Categoria> resultado = repositorio.buscarPorId(UUID.randomUUID());

    assertThat(resultado).isEmpty();
  }

  @Test
  void buscarPorIdDevuelveLaCategoria() {
    CategoriaJpaEntity guardada =
        categorias.save(
            new CategoriaJpaEntity(
                UUID.randomUUID(), "Bolsos", "bolsos-tc2", "BOLSOS", Instant.now()));

    Optional<Categoria> resultado = repositorio.buscarPorId(guardada.getId());

    assertThat(resultado).isPresent();
    assertThat(resultado.orElseThrow().nombre()).isEqualTo("Bolsos");
  }
}
