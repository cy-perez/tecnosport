package co.tecnosport.api.infrastructure.catalogo.siembra;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.ComisionDePasarela;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.infrastructure.catalogo.AtributoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.CategoriaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.ImagenProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.MarcaJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.ProductoJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteAtributoValorJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteImagenJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.VarianteJpaRepository;
import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.inventario.RepositorioInventarioJpa;
import java.math.BigDecimal;
import java.util.List;
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
 * El sembrador de desarrollo pone las fotos con las que se mira la ficha en cada arranque local:
 * una principal y dos de galería por producto. Una foto mal armada —un hash que no pasa el {@code
 * check} de {@code V19}, una fila que declara un tamaño distinto del de su URL— no revienta al
 * sembrar, revienta después, mirando la ficha y sin saber por qué.
 *
 * <p>Hasta el retiro del visor 360 (ADR-0097) esta prueba vigilaba además el set de rotación
 * sembrado; las comprobaciones de las fotos son las mismas que se le hacían a sus fotogramas.
 *
 * <p>El sembrador se instancia a mano en vez de activar el perfil {@code local}: lo que se prueba
 * es lo que construye, no cuándo Spring decide correrlo.
 */
@SpringBootTest
@Testcontainers
@Transactional
class SembradorCatalogoTest {

  private static final List<String> SLUGS =
      List.of(
          "camiseta-running-dry-fit",
          "tenis-trail-runner",
          "morral-urbano-25l",
          "celular-tecnosport-aurora");

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
  @Autowired private RepositorioInventarioJpa inventarios;

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
            variantesDeImagen,
            inventarios,
            new ComisionDePasarela(
                new BigDecimal("0.0265"), Dinero.deCop(700), new BigDecimal("0.19")));
    sembrador.run(null);
  }

  @Test
  void siembra_una_principal_y_dos_de_galeria_por_producto() {
    for (String slug : SLUGS) {
      List<ImagenProductoJpaEntity> fotos = imagenesDe(slug);

      assertThat(fotos).as("fotos de %s", slug).hasSize(3);
      assertThat(fotos.stream().filter(imagen -> "PRINCIPAL".equals(imagen.getTipo()))).hasSize(1);
      assertThat(fotos.stream().filter(imagen -> "GALERIA".equals(imagen.getTipo()))).hasSize(2);
    }
  }

  /**
   * {@code HashContenido} exige un SHA-256 bien formado y {@code V19} lo repite como {@code check}
   * del esquema, así que un hash mal armado en la siembra tumba el arranque local entero. Y tienen
   * que ser distintos entre sí: el hash existe para detectar la misma imagen cargada dos veces.
   */
  @Test
  void cada_foto_lleva_un_sha256_bien_formado_y_propio() {
    List<ImagenProductoJpaEntity> fotos = todas();

    assertThat(fotos).allMatch(imagen -> imagen.getHash().matches("[0-9a-f]{64}"));
    assertThat(fotos.stream().map(ImagenProductoJpaEntity::getHash).distinct())
        .hasSize(fotos.size());
  }

  /**
   * Cada foto tiene que ser una imagen distinta, y sus columnas {@code ancho}/{@code alto}
   * coincidir con lo que pide la URL: una fila que declara un tamaño sobre una imagen de otro hace
   * que `NgOptimizedImage` avise con razón (NG02952) en la ficha.
   */
  @Test
  void cada_foto_apunta_a_su_propia_imagen_del_tamano_que_declara() {
    List<ImagenProductoJpaEntity> fotos = todas();

    assertThat(fotos.stream().map(ImagenProductoJpaEntity::getUrl).distinct())
        .hasSize(fotos.size());
    assertThat(fotos)
        .allMatch(
            imagen -> imagen.getUrl().endsWith("/" + imagen.getAncho() + "/" + imagen.getAlto()),
            "la URL termina en el ancho y el alto que declara la fila");
  }

  /**
   * El sembrador corre en cada {@code bootRun}, así que su guardia de "ya hay productos" es lo que
   * separa un catálogo de desarrollo de tres fotos por producto de uno con seis repetidas.
   */
  @Test
  void sembrar_dos_veces_no_duplica_las_fotos() {
    sembrador.run(null);

    assertThat(imagenesDe("tenis-trail-runner")).hasSize(3);
  }

  private List<ImagenProductoJpaEntity> todas() {
    return SLUGS.stream().flatMap(slug -> imagenesDe(slug).stream()).toList();
  }

  private List<ImagenProductoJpaEntity> imagenesDe(String slug) {
    ProductoJpaEntity producto =
        productos.findBySlug(slug).orElseThrow(() -> new AssertionError("sin producto " + slug));
    return imagenes.findByProductoIdIn(List.of(producto.getId()));
  }
}
