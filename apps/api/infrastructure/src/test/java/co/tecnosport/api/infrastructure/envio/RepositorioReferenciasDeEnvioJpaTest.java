package co.tecnosport.api.infrastructure.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.envio.MedidasDeReferencia;
import co.tecnosport.api.domain.envio.PesoDeReferencia;
import co.tecnosport.api.infrastructure.catalogo.RepositorioCategoriasJpa;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Las referencias de envío contra la base real (adr/0071). Lo que un doble no puede decir: qué
 * siembra {@code V89} sobre el árbol que dejan {@code V63} y {@code V64}, que la bolsa es una sola
 * fila por restricción de la base, y que borrar una categoría se lleva su peso.
 */
@SpringBootTest
@Testcontainers
@Transactional
class RepositorioReferenciasDeEnvioJpaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private RepositorioReferenciasDeEnvioJpa referencias;
  @Autowired private RepositorioCategoriasJpa categorias;
  @Autowired private JdbcTemplate jdbc;

  /** Las medidas que dio el negocio: ancho 30, largo 40 y alto 10 cm. */
  @Test
  void la_migracion_siembra_las_medidas_de_la_bolsa() {
    assertThat(referencias.medidas()).contains(new MedidasDeReferencia(40, 30, 10));
  }

  /** Por slug, sobre el árbol sembrado: un slug que no existe en la base no inserta nada. */
  @Test
  void la_migracion_siembra_los_pesos_por_slug() {
    Map<String, Integer> porSlug =
        referencias.pesos().stream()
            .collect(
                Collectors.toMap(
                    peso -> categorias.buscarPorId(peso.categoriaId()).orElseThrow().slug().valor(),
                    PesoDeReferencia::pesoGramos));

    assertThat(porSlug)
        .containsEntry("ropa-dama-jeans", 700)
        .containsEntry("ropa-caballero-camisetas", 300)
        .containsEntry("calzado-unisex", 700)
        .containsEntry("bolsos-caballero-morrales", 1000)
        .containsEntry("bolsos-dama-manos-libres", 800)
        .doesNotContainKey("ropa-dama")
        .doesNotContainKey("audifonos");
  }

  @Test
  void guardar_las_medidas_reemplaza_la_unica_fila() {
    referencias.guardarMedidas(new MedidasDeReferencia(40, 30, 5));

    assertThat(referencias.medidas()).contains(new MedidasDeReferencia(40, 30, 5));
    assertThat(jdbc.queryForObject("select count(*) from envio_medidas_referencia", Long.class))
        .isEqualTo(1L);
  }

  /** La restricción que sostiene "una sola bolsa" vive en la base, no en el mapeador. */
  @Test
  void la_base_no_admite_una_segunda_bolsa() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into envio_medidas_referencia (id, largo_cm, ancho_cm, alto_cm)"
                        + " values (2, 40, 30, 10)"))
        .hasMessageContaining("envio_medidas_referencia");
  }

  @Test
  void guardar_un_peso_inserta_y_luego_reemplaza() {
    Categoria polos = categoriaNueva("ropa-dama-polos-prueba");

    referencias.guardarPeso(new PesoDeReferencia(polos.id(), 300));
    referencias.guardarPeso(new PesoDeReferencia(polos.id(), 350));

    assertThat(referencias.pesos())
        .filteredOn(peso -> peso.categoriaId().equals(polos.id()))
        .containsExactly(new PesoDeReferencia(polos.id(), 350));
  }

  @Test
  void quitar_un_peso_lo_borra_y_quitar_uno_que_no_esta_no_falla() {
    Categoria polos = categoriaNueva("ropa-dama-polos-prueba");
    referencias.guardarPeso(new PesoDeReferencia(polos.id(), 300));

    referencias.quitarPeso(polos.id());
    referencias.quitarPeso(UUID.randomUUID());

    assertThat(referencias.pesos()).noneMatch(peso -> peso.categoriaId().equals(polos.id()));
  }

  @Test
  void borrar_la_categoria_se_lleva_su_peso() {
    Categoria polos = categoriaNueva("ropa-dama-polos-prueba");
    referencias.guardarPeso(new PesoDeReferencia(polos.id(), 300));

    categorias.eliminar(polos.id());

    assertThat(referencias.pesos()).noneMatch(peso -> peso.categoriaId().equals(polos.id()));
  }

  private Categoria categoriaNueva(String slug) {
    Categoria categoria = Categoria.crear("Polos", new Slug(slug), LineaCatalogo.ROPA);
    categorias.guardar(categoria);
    return categoria;
  }
}
