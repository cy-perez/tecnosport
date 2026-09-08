package co.tecnosport.api.infrastructure.catalogo.siembra;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.infrastructure.catalogo.AtributoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.CategoriaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.ImagenProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.MarcaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.ProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.SetRotacionJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteAtributoValorJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.SetRotacionJpaEntity;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
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
 * El sembrador de desarrollo no tenía ninguna prueba, y su set de rotación es el dato con el que se
 * ejercita el visor 360 en cada arranque local: un set mal armado —fotogramas declarados que no
 * coinciden con las imágenes, un orden con huecos, un hash que no pasa el {@code check} de {@code
 * V19}— no revienta al sembrar, revienta después, mirando la ficha y sin saber por qué.
 *
 * <p>El sembrador se instancia a mano en vez de activar el perfil {@code local}: lo que se prueba
 * es lo que construye, no cuándo Spring decide correrlo.
 */
@SpringBootTest
@Testcontainers
@Transactional
class SembradorCatalogoTest {

  /** El objetivo y el mínimo de la tabla de docs/10-captura-360.md. */
  private static final int FOTOGRAMAS_DEL_TENIS = 8;

  private static final int FOTOGRAMAS_DEL_MORRAL = 4;

  private static final String TIPO_ROTACION = "ROTACION";

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
  @Autowired private SetRotacionJpaRepository setsRotacion;

  private SembradorCatalogo sembrador;

  @BeforeEach
  void sembrar() {
    sembrador =
        new SembradorCatalogo(
            marcas,
            categorias,
            atributos,
            productos,
            variantes,
            valoresAtributo,
            imagenes,
            setsRotacion);
    sembrador.run(null);
  }

  @Test
  void siembra_un_set_publicado_para_el_tenis_y_otro_para_el_morral() {
    assertThat(setDe("tenis-trail-runner").getEstado()).isEqualTo("PUBLICADO");
    assertThat(setDe("tenis-trail-runner").getFotogramas()).isEqualTo(FOTOGRAMAS_DEL_TENIS);
    assertThat(setDe("morral-urbano-25l").getEstado()).isEqualTo("PUBLICADO");
    assertThat(setDe("morral-urbano-25l").getFotogramas()).isEqualTo(FOTOGRAMAS_DEL_MORRAL);
  }

  /**
   * Un set que declara ocho fotogramas y guarda siete deja al visor girando sobre un hueco: el
   * contador dice "de 8" y esa imagen no existe. La cuenta declarada y las imágenes reales son dos
   * verdades que tienen que coincidir.
   */
  @Test
  void cada_fotograma_declarado_tiene_su_imagen_y_los_ordenes_van_completos() {
    for (String slug : List.of("tenis-trail-runner", "morral-urbano-25l")) {
      SetRotacionJpaEntity set = setDe(slug);
      List<ImagenProductoJpaEntity> fotogramas = fotogramasDe(set.getId());

      assertThat(fotogramas).hasSize(set.getFotogramas());
      assertThat(fotogramas).allMatch(imagen -> TIPO_ROTACION.equals(imagen.getTipo()));
      assertThat(fotogramas.stream().map(ImagenProductoJpaEntity::getOrden))
          .containsExactlyElementsOf(IntStream.range(0, set.getFotogramas()).boxed().toList());
    }
  }

  /**
   * {@code HashContenido} exige un SHA-256 bien formado y {@code V19} lo repite como {@code check}
   * del esquema, así que un hash mal armado en la siembra tumba el arranque local entero. Y tienen
   * que ser distintos entre sí: el hash existe para detectar la misma imagen cargada dos veces, y
   * ocho fotogramas con el mismo hash serían ocho duplicados aparentes.
   */
  @Test
  void cada_fotograma_lleva_un_sha256_bien_formado_y_propio() {
    List<ImagenProductoJpaEntity> fotogramas = fotogramasDe(setDe("tenis-trail-runner").getId());

    assertThat(fotogramas).allMatch(imagen -> imagen.getHash().matches("[0-9a-f]{64}"));
    assertThat(fotogramas.stream().map(ImagenProductoJpaEntity::getHash).distinct())
        .hasSize(FOTOGRAMAS_DEL_TENIS);
  }

  /**
   * El sembrador corre en cada {@code bootRun}, así que su guardia de "ya hay productos" es lo que
   * separa un catálogo de desarrollo de ocho fotogramas de uno con dieciséis repetidos.
   */
  @Test
  void sembrar_dos_veces_no_duplica_el_set_ni_sus_fotogramas() {
    sembrador.run(null);

    SetRotacionJpaEntity set = setDe("tenis-trail-runner");
    assertThat(fotogramasDe(set.getId())).hasSize(FOTOGRAMAS_DEL_TENIS);
    assertThat(setsRotacion.findByProductoIdIn(List.of(set.getProductoId()))).hasSize(1);
  }

  private SetRotacionJpaEntity setDe(String slug) {
    ProductoJpaEntity producto =
        productos.findBySlug(slug).orElseThrow(() -> new AssertionError("sin producto " + slug));
    List<SetRotacionJpaEntity> sets = setsRotacion.findByProductoIdIn(List.of(producto.getId()));
    assertThat(sets).as("sets de rotación de %s", slug).hasSize(1);
    return sets.getFirst();
  }

  private List<ImagenProductoJpaEntity> fotogramasDe(UUID setId) {
    return imagenes.findBySetRotacionId(setId).stream()
        .sorted(Comparator.comparingInt(ImagenProductoJpaEntity::getOrden))
        .toList();
  }
}
