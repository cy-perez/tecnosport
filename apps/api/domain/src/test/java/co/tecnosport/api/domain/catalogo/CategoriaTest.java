package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Hashtag;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CategoriaTest {

  @Test
  void guardaLaLineaDelCatalogo() {
    Categoria categoria =
        Categoria.crear("Morrales", new Slug("bolsos-dama-morrales"), LineaCatalogo.BOLSOS);

    assertEquals(LineaCatalogo.BOLSOS, categoria.linea());
  }

  @Test
  void rechazaNombreVacio() {
    assertThrows(
        ExcepcionDeDominio.class,
        () -> Categoria.crear(" ", new Slug("x"), LineaCatalogo.TECNOLOGIA));
  }

  @Test
  void naceSinEtiquetasYEsoNoEsUnDefecto() {
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);

    assertEquals(List.of(), categoria.hashtags());
  }

  @Test
  void lasEtiquetasSeGuardanEnElOrdenEnQueSeEscriben() {
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA)
            .conHashtags(
                List.of(new Hashtag("Parlantes"), new Hashtag("JBL"), new Hashtag("TecnoSport")));

    assertEquals(
        List.of("#Parlantes", "#JBL", "#TecnoSport"),
        categoria.hashtags().stream().map(Hashtag::valor).toList());
  }

  /**
   * Repetir una etiqueta no merece reventar -- quien la teclea puede haberse repetido sin mas --
   * pero tampoco tiene por que llegar al post: las redes la cuentan contra su tope y una duplicada
   * gasta un cupo sin aportar alcance.
   */
  @Test
  void unaEtiquetaRepetidaNoGastaDosCupos() {
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA)
            .conHashtags(List.of(new Hashtag("JBL"), new Hashtag("#JBL"), new Hashtag("Audio")));

    assertEquals(
        List.of("#JBL", "#Audio"), categoria.hashtags().stream().map(Hashtag::valor).toList());
  }

  /** Renombrar "Camisas" no tiene por que borrar lo que se publicaba con sus etiquetas. */
  @Test
  void renombrarNoSeLlevaLasEtiquetas() {
    Categoria categoria =
        Categoria.crear("Camisas", new Slug("ropa-dama-camisas"), LineaCatalogo.ROPA)
            .conHashtags(List.of(new Hashtag("Camisas")));

    Categoria renombrada = categoria.renombrada("Camisas de vestir", new Slug("camisas-de-vestir"));

    assertEquals(List.of(new Hashtag("#Camisas")), renombrada.hashtags());
  }

  @Test
  void moverDeSitioTampoco() {
    Categoria padre = Categoria.crear("Caballero", new Slug("ropa-caballero"), LineaCatalogo.ROPA);
    Categoria categoria =
        Categoria.crear("Camisas", new Slug("ropa-dama-camisas"), LineaCatalogo.TECNOLOGIA)
            .conHashtags(List.of(new Hashtag("Camisas")));

    Categoria movida = categoria.movidaBajo(Optional.of(padre), LineaCatalogo.ROPA);

    assertEquals(List.of(new Hashtag("#Camisas")), movida.hashtags());
    assertEquals(LineaCatalogo.ROPA, movida.linea());
  }
}
