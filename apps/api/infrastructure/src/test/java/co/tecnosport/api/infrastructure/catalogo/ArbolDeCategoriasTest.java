package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * El árbol que deja {@code V63} es <b>exactamente</b> el que el negocio pidió el 24 de septiembre
 * de 2026, con la forma que pidió.
 *
 * <p><b>Por qué se afirma el árbol entero y no solo que existe.</b> Es la misma lección de {@code
 * CategoriasDeTecnologiaTest}, que nació porque nadie comparaba la lista del catálogo con la de la
 * skill y "Cables de cargador" vivió diez días sin que pudiera llegarle un producto. Aquí hay dos
 * listas otra vez —la de la migración y la que el menú del sitio pinta— y una tercera que no se ve:
 * la <b>forma</b>. Una prueba que solo mire que "Faldas" existe pasa igual si "Faldas" quedó
 * colgando de la línea en vez de bajo "Dama", y entonces el menú la pinta en el sitio equivocado.
 *
 * <p>Va sobre el contenedor y no sobre un doble porque lo que se comprueba es el <b>resultado de
 * migrar</b>: el dato lo escribe Flyway, no el código. Si esta prueba se cae con Docker apagado, no
 * es una regresión.
 *
 * <p>Cuando el negocio cambie el árbol, el orden es: entra por el panel si es un cambio de
 * operación, o por migración si toda instalación lo necesita, y se ajusta aquí. Lo que no puede
 * pasar es que cambie y nadie se entere.
 */
@SpringBootTest
@Testcontainers
class ArbolDeCategoriasTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

  @Autowired private CategoriaJpaRepository categorias;

  /**
   * Las cuatro líneas y sus categorías de primer nivel. Las ocho de tecnología cuelgan directamente
   * de la línea; ropa, calzado y bolsos abren rama primero.
   */
  private static final Map<String, List<String>> PRIMER_NIVEL =
      Map.of(
          "TECNOLOGIA",
              List.of(
                  "celulares",
                  "tablets",
                  "relojes",
                  "audifonos",
                  "consolas",
                  "computadores",
                  "proyectores",
                  "parlantes"),
          "ROPA", List.of("ropa-dama", "ropa-caballero"),
          "CALZADO", List.of("calzado-dama", "calzado-caballero", "calzado-unisex"),
          "BOLSOS", List.of("bolsos-dama"));

  /** El segundo nivel, por el slug de su padre. Lo que no aparece aquí es una hoja. */
  private static final Map<String, List<String>> SEGUNDO_NIVEL =
      Map.of(
          "ropa-dama",
              List.of(
                  "ropa-dama-camisas",
                  "ropa-dama-blusas",
                  "ropa-dama-busos",
                  "ropa-dama-pantalones",
                  "ropa-dama-faldas",
                  "ropa-dama-shorts",
                  "ropa-dama-bodis",
                  "ropa-dama-licras",
                  "ropa-dama-sudaderas"),
          "ropa-caballero",
              List.of(
                  "ropa-caballero-camisetas",
                  "ropa-caballero-busos",
                  "ropa-caballero-sudaderas",
                  "ropa-caballero-pantalonetas"),
          "bolsos-dama",
              List.of(
                  "bolsos-dama-bolsos-de-mano",
                  "bolsos-dama-manos-libres",
                  "bolsos-dama-morrales"));

  @Test
  void cadaLineaTieneExactamenteSusCategoriasDePrimerNivel() {
    List<CategoriaJpaEntity> todas = categorias.findAll();

    PRIMER_NIVEL.forEach(
        (linea, slugs) ->
            assertThat(todas)
                .filteredOn(c -> linea.equals(c.getLinea()) && c.getPadreId() == null)
                .extracting(CategoriaJpaEntity::getSlug)
                .as("primer nivel de %s", linea)
                .containsExactlyInAnyOrderElementsOf(slugs));
  }

  @Test
  void cadaRamaTieneExactamenteSusHojas() {
    Map<String, UUID> idPorSlug =
        categorias.findAll().stream()
            .collect(Collectors.toMap(CategoriaJpaEntity::getSlug, CategoriaJpaEntity::getId));

    SEGUNDO_NIVEL.forEach(
        (padre, hojas) ->
            assertThat(categorias.findByPadreIdOrderByNombreAsc(idPorSlug.get(padre)))
                .extracting(CategoriaJpaEntity::getSlug)
                .as("hojas de %s", padre)
                .containsExactlyInAnyOrderElementsOf(hojas));
  }

  /**
   * El tope de dos niveles no es solo una regla de {@code CrearCategoria}: el dato que entra por
   * migración tiene que cumplirlo también, o el panel defendería una invariante que la base ya
   * rompió.
   */
  @Test
  void ningunaCategoriaCuelgaDeUnaQueYaTienePadre() {
    Map<UUID, CategoriaJpaEntity> porId =
        categorias.findAll().stream()
            .collect(Collectors.toMap(CategoriaJpaEntity::getId, Function.identity()));

    assertThat(porId.values())
        .filteredOn(c -> c.getPadreId() != null)
        .allSatisfy(
            hija ->
                assertThat(porId.get(hija.getPadreId()).getPadreId())
                    .as("la madre de %s no puede tener madre", hija.getSlug())
                    .isNull());
  }

  /** Una hija siempre está en la línea de su madre: si no, el menú no sabría dónde pintarla. */
  @Test
  void cadaHijaHeredaLaLineaDeSuMadre() {
    Map<UUID, CategoriaJpaEntity> porId =
        categorias.findAll().stream()
            .collect(Collectors.toMap(CategoriaJpaEntity::getId, Function.identity()));

    assertThat(porId.values())
        .filteredOn(c -> c.getPadreId() != null)
        .allSatisfy(
            hija ->
                assertThat(hija.getLinea())
                    .as("línea de %s", hija.getSlug())
                    .isEqualTo(porId.get(hija.getPadreId()).getLinea()));
  }

  /**
   * Las tres categorías planas que el árbol reemplaza. Explícito además del conjunto de arriba, por
   * el mismo motivo que en {@code CategoriasDeTecnologiaTest}: cuando falle, el mensaje nombra el
   * caso concreto.
   */
  @Test
  void noSobrevivenLasCategoriasPlanasQueElArbolReemplaza() {
    assertThat(categorias.findBySlug("ropa-deportiva")).isEmpty();
    assertThat(categorias.findBySlug("calzado-deportivo")).isEmpty();
    assertThat(categorias.findBySlug("bolsos")).isEmpty();
  }

  /**
   * Y la línea vieja no queda en ninguna fila: {@code LineaCatalogo.valueOf} reventaría al leer.
   */
  @Test
  void ningunaFilaConservaLaLineaQueElEnumYaNoConoce() {
    assertThat(categorias.findAll())
        .extracting(CategoriaJpaEntity::getLinea)
        .doesNotContain("ROPA_Y_CALZADO");
  }
}
