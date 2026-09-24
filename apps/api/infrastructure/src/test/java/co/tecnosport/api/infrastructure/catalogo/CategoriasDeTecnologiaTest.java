package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Las categorías de {@code TECNOLOGIA} que quedan después de migrar son <b>exactamente</b> las que
 * la skill {@code listas-de-proveedor} sabe llenar, ni una más.
 *
 * <p><b>Por qué existe esta prueba.</b> {@code V38} creó "Cables de cargador" el 14 de septiembre
 * de 2026, el mismo día en que esa skill decidió que un cable nunca se publica —la lista solo trae
 * los extremos, sin longitud ni potencia ni marca—. La categoría vivió diez días en el catálogo sin
 * que existiera un solo camino por el que pudiera llegarle un producto, y nadie se enteró porque
 * nada comparaba las dos listas: una vive en una migración SQL y la otra en un {@code set} de
 * Python. {@code V62} la borró junto a "Cargadores" y "Power banks", y esta prueba es lo que impide
 * que vuelva a pasar.
 *
 * <p>Afirma el conjunto <b>completo</b> y no solo la ausencia de las tres borradas, y esa es la
 * diferencia que importa: una prueba que solo mire que no está "cables-de-cargador" no dice nada el
 * día que alguien agregue "Impresoras" por migración. La lista de proveedor descarta las impresoras
 * igual que descartaba los cables.
 *
 * <p>Va sobre el contenedor y no sobre un doble porque lo que se comprueba es el <b>resultado de
 * migrar</b>: el dato lo escribe Flyway, no el código. Si esta prueba se cae con Docker apagado, no
 * es una regresión.
 *
 * <p>Cuando el negocio decida publicar una categoría nueva, el orden es: entra en {@code
 * CATEGORIAS_INCLUIDAS} de la skill, entra por migración al catálogo, y entra aquí. Si solo se
 * hacen dos de las tres, esta prueba dice cuál falta.
 */
@SpringBootTest
@Testcontainers
class CategoriasDeTecnologiaTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private CategoriaJpaRepository categorias;

  /**
   * Los mismos ocho de {@code CATEGORIAS_INCLUIDAS} en {@code
   * .claude/skills/listas-de-proveedor/scripts/parsear_lista.py}. Cambiar uno exige cambiar el otro
   * en el mismo commit.
   */
  private static final String[] SLUGS_ESPERADOS = {
    "audifonos",
    "celulares",
    "computadores",
    "consolas",
    "parlantes",
    "proyectores",
    "relojes",
    "tablets"
  };

  @Test
  void laLineaDeTecnologiaTieneSoloLasCategoriasQueLaListaDeProveedorPuedeLlenar() {
    assertThat(categorias.findAll())
        .filteredOn(c -> "TECNOLOGIA".equals(c.getLinea()))
        .extracting(CategoriaJpaEntity::getSlug)
        .containsExactlyInAnyOrder(SLUGS_ESPERADOS);
  }

  /**
   * Las ocho cuelgan de la línea y de nadie más, y eso hay que afirmarlo desde que {@code V63}
   * convirtió el catálogo en un árbol: la lista de slugs de arriba pasaría igual si alguien colgara
   * "Tablets" bajo "Celulares", y el menú la pintaría dentro en vez de al lado. A diferencia de
   * ropa y bolsos, tecnología no abre ramas — no hay "Celulares › Gama alta" y no se quiere.
   */
  @Test
  void lasOchoCuelganDeLaLineaYNoUnaDeOtra() {
    assertThat(categorias.findAll())
        .filteredOn(c -> "TECNOLOGIA".equals(c.getLinea()))
        .allSatisfy(
            categoria ->
                assertThat(categoria.getPadreId())
                    .as("%s no debería colgar de otra categoría", categoria.getSlug())
                    .isNull());
  }

  /**
   * Explícita sobre las tres que {@code V62} borró. Es redundante con la anterior y se queda a
   * propósito: cuando falle, el mensaje nombra el caso concreto en vez de dejar una diferencia de
   * conjuntos que hay que leer dos veces.
   */
  @Test
  void loQueAlimentaAlEquipoNoEsUnaCategoriaDelCatalogo() {
    assertThat(categorias.findBySlug("cargadores")).isEmpty();
    assertThat(categorias.findBySlug("power-banks")).isEmpty();
    assertThat(categorias.findBySlug("cables-de-cargador")).isEmpty();
  }
}
