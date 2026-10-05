package co.tecnosport.api.infrastructure.catalogo.siembra;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.infrastructure.catalogo.AtributoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.CategoriaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.ImagenProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.MarcaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.ProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.SetRotacionJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteAtributoValorJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteImagenJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.entidad.AtributoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import co.tecnosport.api.infrastructure.inventario.RepositorioInventarioJpa;
import java.time.Instant;
import java.util.List;
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

/**
 * La siembra sobre una base que ya tiene marcas y atributos pero ningún producto: lo que deja el
 * panel, o la carga del catálogo, antes de publicar. Aparte de {@link SembradorCatalogoTest} porque
 * allí cada prueba arranca con la siembra ya hecha, y lo que importa aquí es lo que había antes de
 * sembrar.
 */
@SpringBootTest
@Testcontainers
@Transactional
class SembradorSobreBaseConDatosTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private MarcaJpaRepository marcas;
  @Autowired private CategoriaJpaRepository categorias;
  @Autowired private AtributoJpaRepository atributos;
  @Autowired private ProductoJpaRepository productos;
  @Autowired private VarianteJpaRepository variantes;
  @Autowired private VarianteAtributoValorJpaRepository valoresAtributo;
  @Autowired private ImagenProductoJpaRepository imagenes;
  @Autowired private VarianteImagenJpaRepository variantesDeImagen;
  @Autowired private SetRotacionJpaRepository setsRotacion;
  @Autowired private RepositorioInventarioJpa inventarios;

  /**
   * El arranque local caía entero con {@code marca_nombre_unico}: la guarda solo mira si hay
   * productos, y la marca ya estaba. En otras mayúsculas a propósito, porque así la compara el
   * índice de {@code V56}.
   */
  @Test
  void reutiliza_la_marca_y_la_garantia_que_ya_existen() {
    MarcaJpaEntity existente =
        marcas.save(new MarcaJpaEntity(UUID.randomUUID(), "TECNOSPORT", Instant.now()));
    AtributoJpaEntity garantia =
        atributos.save(
            new AtributoJpaEntity(
                UUID.randomUUID(), "Garantía", "NUMERO", List.of(), Instant.now(), "meses"));

    sembrador().run(null);

    assertThat(marcas.findAll())
        .filteredOn(marca -> marca.getNombre().equalsIgnoreCase("tecnosport"))
        .extracting(MarcaJpaEntity::getId)
        .containsExactly(existente.getId());
    assertThat(productos.findBySlug("camiseta-running-dry-fit").orElseThrow().getMarcaId())
        .isEqualTo(existente.getId());
    assertThat(atributos.findAll())
        .filteredOn(atributo -> atributo.getNombre().equalsIgnoreCase("garantía"))
        .extracting(AtributoJpaEntity::getId)
        .containsExactly(garantia.getId());
  }

  private SembradorCatalogo sembrador() {
    return new SembradorCatalogo(
        marcas,
        categorias,
        atributos,
        productos,
        variantes,
        valoresAtributo,
        imagenes,
        variantesDeImagen,
        setsRotacion,
        inventarios);
  }
}
