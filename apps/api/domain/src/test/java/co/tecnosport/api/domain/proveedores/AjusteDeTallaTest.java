package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AjusteDeTallaTest {

  private static final List<String> CALZADO =
      List.of("34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45");
  private static final List<String> JEANS =
      List.of("26", "28", "30", "32", "34", "36", "38", "40", "42");
  private static final List<String> ROPA =
      List.of("XS", "XS-S", "S", "S-M", "M", "M-L", "L", "L-XL", "XL", "XL-XXL", "XXL", "XXXL");

  private static List<String> delProveedor(int tallas, List<String> vendidas, List<String> escala) {
    AjusteDeTalla ajuste = new AjusteDeTalla(tallas);
    return vendidas.stream().map(t -> ajuste.tallaDelProveedor(t, escala).orElse("?")).toList();
  }

  /**
   * Meraki, 13 de octubre de 2026: «Ojo Horma reducida pedir una talla más · Tallas M-L-XL-XXL».
   */
  @Test
  void laHormaReducidaDeMerakiSeVendeUnaTallaAbajo() {
    assertEquals(
        List.of("M", "L", "XL", "XXL"), delProveedor(1, List.of("S", "M", "L", "XL"), ROPA));
  }

  /** Imperio Wicho, 7 de octubre de 2026: «Tallas disponibles 40 a la 45 · Aumentar 2 tallas». */
  @Test
  void aumentarDosTallasEnCalzadoSonDosNumeros() {
    assertEquals(
        List.of("40", "41", "42", "43", "44", "45"),
        delProveedor(2, List.of("38", "39", "40", "41", "42", "43"), CALZADO));
  }

  /** La del proveedor no tiene por qué estar en la escala de la tienda: la 45 + 1 es la 46. */
  @Test
  void laDelProveedorPuedeQuedarFueraDeLaEscala() {
    assertEquals(Optional.of("46"), new AjusteDeTalla(1).tallaDelProveedor("45", CALZADO));
  }

  /**
   * «Tallas disponibles 36 a la 45 eur»: la nacional es la EUR menos una (13 de octubre de 2026).
   */
  @Test
  void unaTallaEurEsLaNacionalMasUna() {
    assertEquals(List.of("36", "45"), delProveedor(1, List.of("35", "44"), CALZADO));
  }

  /** En un jean, una talla más es la siguiente de la escala, que va de 2 en 2. */
  @Test
  void enUnJeanElPasoLoDaLaEscala() {
    assertEquals(Optional.of("32"), new AjusteDeTalla(1).tallaDelProveedor("30", JEANS));
  }

  /**
   * Un pantalón de dama en 6, 8, 10 hereda la escala de letras: sin números no se sabe el paso, y
   * contar de 1 en 1 daría una talla 9 que no existe.
   */
  @Test
  void sinNumerosEnLaEscalaUnaNumericaNoSeCuenta() {
    assertEquals(Optional.empty(), new AjusteDeTalla(1).tallaDelProveedor("8", ROPA));
    assertEquals(Optional.empty(), new AjusteDeTalla(2).tallaDelProveedor("38", List.of()));
  }

  @Test
  void unaNumericaQueNoEstaEnLaEscalaNoSeCuenta() {
    assertEquals(Optional.empty(), new AjusteDeTalla(1).tallaDelProveedor("31", JEANS));
  }

  /** Con un paso irregular manda la escala, no los dos primeros números. */
  @Test
  void conPasoIrregularCuentaSobreLaEscala() {
    List<String> irregular = List.of("26", "27", "28", "29", "30", "32", "34");
    assertEquals(Optional.of("34"), new AjusteDeTalla(1).tallaDelProveedor("32", irregular));
    assertEquals(Optional.of("36"), new AjusteDeTalla(1).tallaDelProveedor("34", irregular));
  }

  @Test
  void unaTallaDeCeroONegativaNoExiste() {
    List<String> infantil = List.of("2", "4", "6");
    assertEquals(Optional.empty(), new AjusteDeTalla(-1).tallaDelProveedor("2", infantil));
    assertEquals(Optional.of("2"), new AjusteDeTalla(-1).tallaDelProveedor("4", infantil));
  }

  @Test
  void unaAgrupadaSeCuentaParteAParte() {
    assertEquals(Optional.of("M-L"), new AjusteDeTalla(1).tallaDelProveedor("S-M", ROPA));
    assertEquals(Optional.of("M-L"), new AjusteDeTalla(1).tallaDelProveedor("s/m", ROPA));
  }

  @Test
  void seEscribeComoLaComparaLaVitrina() {
    assertEquals(Optional.of("XXXL"), new AjusteDeTalla(1).tallaDelProveedor("2XL", ROPA));
  }

  /** Lo que no se puede contar no se adivina: quien aprueba decide. */
  @Test
  void loQueNoSePuedeContarQuedaVacio() {
    AjusteDeTalla uno = new AjusteDeTalla(1);
    assertEquals(Optional.empty(), uno.tallaDelProveedor("XXXL", ROPA));
    assertEquals(Optional.empty(), uno.tallaDelProveedor("Única", ROPA));
    assertEquals(Optional.empty(), uno.tallaDelProveedor("PLUS", ROPA));
    assertEquals(Optional.empty(), new AjusteDeTalla(-1).tallaDelProveedor("XS", ROPA));
    assertEquals(Optional.empty(), uno.tallaDelProveedor(" ", ROPA));
  }

  @Test
  void sinAjusteEsLaMisma() {
    assertEquals(Optional.of("Única"), AjusteDeTalla.NINGUNO.tallaDelProveedor("Única", ROPA));
    assertEquals(Optional.of("M"), AjusteDeTalla.NINGUNO.tallaDelProveedor(" M ", ROPA));
  }

  @Test
  void unaHormaGrandeSeCuentaHaciaAbajo() {
    assertEquals(Optional.of("S"), new AjusteDeTalla(-1).tallaDelProveedor("M", ROPA));
    assertEquals(Optional.of("37"), new AjusteDeTalla(-1).tallaDelProveedor("38", CALZADO));
  }

  @Test
  void masDeCuatroTallasNoEsUnaHorma() {
    assertThrows(ExcepcionDeDominio.class, () -> new AjusteDeTalla(5));
    assertThrows(ExcepcionDeDominio.class, () -> new AjusteDeTalla(-5));
  }
}
