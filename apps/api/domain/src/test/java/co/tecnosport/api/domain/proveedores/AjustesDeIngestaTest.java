package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AjustesDeIngestaTest {

  /**
   * Las de Tenis Al Pie, 23 de octubre de 2026: EUR, que en dama es la nacional más una y en
   * caballero más tres (ADR-0103: el EUR 42 de caballero mide 26,5 cm, la 39 colombiana).
   */
  private static final TallasPorGenero AL_PIE =
      new TallasPorGenero(
          List.of("36", "37", "38", "39"),
          List.of("40", "41", "42", "43", "44"),
          new AjusteDeTalla(1),
          new AjusteDeTalla(3));

  @Test
  void laTiendaVendeLaTallaNacionalDeCadaGenero() {
    assertEquals(List.of("35", "36", "37", "38"), AL_PIE.queSeVenden(GeneroDeCalzado.DAMA));
    assertEquals(
        List.of("37", "38", "39", "40", "41"), AL_PIE.queSeVenden(GeneroDeCalzado.CABALLERO));
    assertEquals(Optional.of(new AjusteDeTalla(3)), AL_PIE.ajusteDe(GeneroDeCalzado.CABALLERO));
  }

  /**
   * Con ajustes distintos el unisex no tiene una sola talla: la EUR 40 sería la 39 de dama y la 37
   * de caballero. Se parte, y pedirle sus tallas vendidas es un error.
   */
  @Test
  void unUnisexDeAjustesDistintosSeParte() {
    assertTrue(AL_PIE.parteElUnisex());
    assertEquals(Optional.empty(), AL_PIE.ajusteDe(GeneroDeCalzado.UNISEX));
    assertThrows(ExcepcionDeDominio.class, () -> AL_PIE.queSeVenden(GeneroDeCalzado.UNISEX));
  }

  /** Con el mismo ajuste en los dos, el unisex lleva las dos escalas, sin repetir y en orden. */
  @Test
  void unUnisexDeUnSoloAjusteLlevaLasDosDeMenorAMayor() {
    TallasPorGenero parejas =
        new TallasPorGenero(
            List.of("36", "37", "38", "39"),
            List.of("40", "41", "42", "43", "44"),
            new AjusteDeTalla(1),
            new AjusteDeTalla(1));

    assertFalse(parejas.parteElUnisex());
    assertEquals(
        List.of("36", "37", "38", "39", "40", "41", "42", "43", "44"),
        parejas.delProveedor(GeneroDeCalzado.UNISEX));
    assertEquals(
        List.of("35", "36", "37", "38", "39", "40", "41", "42", "43"),
        parejas.queSeVenden(GeneroDeCalzado.UNISEX));
  }

  /** Si el proveedor no maneja de un género, ese tenis se ofrece en las del otro. */
  @Test
  void sinLasDeUnGeneroValenLasDelOtro() {
    TallasPorGenero soloCaballero =
        new TallasPorGenero(
            List.of(), List.of("40", "41"), new AjusteDeTalla(1), AjusteDeTalla.NINGUNO);

    assertEquals(List.of("40", "41"), soloCaballero.queSeVenden(GeneroDeCalzado.DAMA));
    assertFalse(soloCaballero.parteElUnisex(), "con un solo género no hay nada que partir");
    assertEquals(List.of("40", "41"), soloCaballero.queSeVenden(GeneroDeCalzado.UNISEX));
  }

  @Test
  void lasTallasDeCalzadoSonNumerosYAlMenosUnaLista() {
    assertThrows(
        ExcepcionDeDominio.class, () -> new TallasPorGenero(List.of("M"), List.of(), null, null));
    assertThrows(
        ExcepcionDeDominio.class, () -> new TallasPorGenero(List.of(), List.of(), null, null));
  }

  @Test
  void losDiasDeVigenciaVanDeUnoATreinta() {
    assertThrows(ExcepcionDeDominio.class, () -> new AjustesDeIngesta(false, false, 0, null));
    assertThrows(ExcepcionDeDominio.class, () -> new AjustesDeIngesta(false, false, 31, null));
    assertEquals(5, new AjustesDeIngesta(false, false, 5, null).diasDeVigencia());
  }

  @Test
  void soloElCalzadoTieneTallasPorGeneroYSoloElChatTieneAjustes() {
    Proveedor ropa =
        Proveedor.crear(
            "Violeta",
            LineaCatalogo.ROPA,
            "3000000000",
            "Violeta",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    Proveedor tecnologia =
        Proveedor.crear(
            "Celulares",
            LineaCatalogo.TECNOLOGIA,
            "3000000001",
            "Celulares",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);
    Proveedor calzado =
        Proveedor.crear(
            "Tenis Al Pie",
            LineaCatalogo.CALZADO,
            "3000000002",
            "tenis.alpie",
            null,
            OrdenDePublicacion.FOTOS_PRIMERO);

    assertThrows(
        ExcepcionDeDominio.class,
        () -> ropa.definirAjustesDeIngesta(new AjustesDeIngesta(true, true, 5, AL_PIE)));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> tecnologia.definirAjustesDeIngesta(new AjustesDeIngesta(false, false, 5, null)));

    calzado.definirAjustesDeIngesta(new AjustesDeIngesta(true, true, 5, AL_PIE));
    assertTrue(calzado.ajustesDeIngesta().unaFotoUnProducto());
    calzado.definirAjustesDeIngesta(null);
    assertTrue(calzado.ajustesDeIngesta().porOmision());
  }
}
