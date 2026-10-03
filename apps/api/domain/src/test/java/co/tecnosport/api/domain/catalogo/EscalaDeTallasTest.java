package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** La escala de tallas de una categoría y la talla única de un producto (3 de octubre de 2026). */
class EscalaDeTallasTest {

  private static final Categoria DAMA =
      Categoria.crear("Dama", new Slug("ropa-dama"), LineaCatalogo.ROPA)
          .conEscalaDeTallas(List.of("XS", "S", "M", "L"));

  @Test
  void unaHojaSinEscalaUsaLaDeSuRama() {
    Categoria bodis = Categoria.crearBajo(DAMA, "Bodis", new Slug("ropa-dama-bodis"));

    assertEquals(List.of("XS", "S", "M", "L"), bodis.escalaEfectiva(Optional.of(DAMA)));
    assertEquals(List.of(), bodis.escalaTallas());
  }

  @Test
  void laEscalaPropiaManda() {
    Categoria jeans =
        Categoria.crearBajo(DAMA, "Jeans", new Slug("ropa-dama-jeans"))
            .conEscalaDeTallas(List.of("26", "28", "30"));

    assertEquals(List.of("26", "28", "30"), jeans.escalaEfectiva(Optional.of(DAMA)));
  }

  @Test
  void sinEscalaNiRamaNoTalla() {
    Categoria celulares =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA);

    assertEquals(List.of(), celulares.escalaEfectiva(Optional.empty()));
  }

  @Test
  void laEscalaSeLimpiaYNoRepite() {
    Categoria limpia = DAMA.conEscalaDeTallas(Arrays.asList(" S ", "M", "", null, "S", "L"));

    assertEquals(List.of("S", "M", "L"), limpia.escalaTallas());
  }

  @Test
  void renombrarOMoverConservaLaEscala() {
    Categoria renombrada = DAMA.renombrada("Mujer", new Slug("ropa-mujer"));

    assertEquals(DAMA.escalaTallas(), renombrada.escalaTallas());
    assertEquals(
        DAMA.escalaTallas(), DAMA.movidaBajo(Optional.empty(), LineaCatalogo.ROPA).escalaTallas());
  }

  @Test
  void elSirveHastaDeUnaTallaUnica() {
    Producto bodi =
        Producto.crear(
            "Bodi herraje",
            new Slug("bodi-herraje"),
            "",
            Marca.crear("Genérica"),
            Categoria.crearBajo(DAMA, "Bodis", new Slug("ropa-dama-bodis")));

    bodi.definirTallaSirveHasta(" L ");
    assertEquals(Optional.of("L"), bodi.tallaSirveHasta());

    bodi.definirTallaSirveHasta("  ");
    assertEquals(Optional.empty(), bodi.tallaSirveHasta());

    assertThrows(
        ExcepcionDeDominio.class,
        () -> bodi.definirTallaSirveHasta("sirve hasta la talla L de las grandes"));
  }
}
