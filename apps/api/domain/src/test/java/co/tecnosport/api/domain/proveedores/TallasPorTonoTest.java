package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.proveedores.TallasPorTono.TallasDeUnTono;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TallasPorTonoTest {

  /** «Talla SM ML(negro) / Talla ML(cocoa) / Talla SM(verde)», Violeta, 9 de octubre de 2026. */
  private static final TallasPorTono BLUSA =
      new TallasPorTono(
          List.of(
              new TallasDeUnTono("negro", List.of("SM", "ML")),
              new TallasDeUnTono("cocoa", List.of("ML")),
              new TallasDeUnTono("verde", List.of("SM"))));

  @Test
  void elTonoSeReconoceSinMayusculasNiTildesNiElNumeroDePrenda() {
    assertEquals(Optional.of(List.of("ML")), BLUSA.tallasDe("Cocoa"));
    assertEquals(Optional.of(List.of("ML")), BLUSA.tallasDe("cocoa 2"));
    assertEquals(Optional.of(List.of("SM", "ML")), BLUSA.tallasDe(" NEGRO "));
    assertEquals(Optional.empty(), BLUSA.tallasDe("beige"));
    assertEquals(Optional.empty(), BLUSA.tallasDe(null));
  }

  /**
   * La paleta no tiene «cocoa»: quien aprueba la marca «Café», y la lectura de fotos dice que en
   * esa foto vio «cocoa». Con ese segundo nombre el tono encuentra sus tallas.
   */
  @Test
  void elTonoSeBuscaPorCadaUnoDeSusNombresYSeQuedaConLasAprobadas() {
    List<String> aprobadas = List.of("SM", "ML");

    assertEquals(List.of("ML"), BLUSA.tallasPara(List.of("Café", "cocoa"), aprobadas));
    assertEquals(List.of("SM", "ML"), BLUSA.tallasPara(List.of("Negro 2"), aprobadas));
    assertEquals(
        aprobadas, BLUSA.tallasPara(List.of("Café"), aprobadas), "sin coincidencia, todas");
    assertEquals(
        List.of("XL"),
        BLUSA.tallasPara(List.of("verde"), List.of("XL")),
        "si quien aprueba quitó las del tono, el tono va en las que quedan");
  }

  @Test
  void soloQuedanLosTonosQueElTextoNombra() {
    String texto = "Blusa Licrada (VY2945)\nTalla SM ML(negro)\nTalla ML(cocoa)";

    TallasPorTono contrastadas = BLUSA.contrastadoCon(texto);

    assertEquals(2, contrastadas.tonos().size());
    assertEquals(Optional.empty(), contrastadas.tallasDe("verde"));
    assertEquals(TallasPorTono.ninguna(), BLUSA.contrastadoCon(null));
  }

  @Test
  void unTonoNoSeRepiteNiLlegaSinTallas() {
    assertThrows(
        ExcepcionDeDominio.class,
        () ->
            new TallasPorTono(
                List.of(
                    new TallasDeUnTono("Negro", List.of("S")),
                    new TallasDeUnTono("negro", List.of("M")))));
    assertThrows(ExcepcionDeDominio.class, () -> new TallasDeUnTono("negro", List.of()));
  }
}
