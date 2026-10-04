package co.tecnosport.api.domain.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MuestraDeColorTest {

  private static final List<ColorDePaleta> PALETA =
      List.of(
          new ColorDePaleta(UUID.randomUUID(), "Negro", "Black", "#111111", 1),
          new ColorDePaleta(UUID.randomUUID(), "Rojo", "Red", "#C62828", 2),
          new ColorDePaleta(UUID.randomUUID(), "Azul petróleo", "Petrol blue", "#1F4E5F", 3),
          new ColorDePaleta(UUID.randomUUID(), "Blanco", "White", "#FFFFFF", 4),
          new ColorDePaleta(
              UUID.randomUUID(),
              "Animal print",
              "Animal print",
              "#C19A6B",
              5,
              PatronDeColor.ANIMAL_PRINT,
              List.of("#C19A6B", "#3B2A1A")));

  /** La camiseta del caso que trajo el negocio: negro y rojo, en ese orden. */
  @Test
  void negroYRojoSonDosPorcionesEnElOrdenElegido() {
    MuestraDeColor muestra = MuestraDeColor.componer("Negro / Rojo", PALETA).orElseThrow();

    assertEquals(
        List.of(ParteDeMuestra.lisa("#111111"), ParteDeMuestra.lisa("#C62828")), muestra.partes());
    assertEquals("#111111", muestra.primerHex());
    assertEquals(
        List.of(ParteDeMuestra.lisa("#C62828"), ParteDeMuestra.lisa("#111111")),
        MuestraDeColor.componer("Rojo / Negro", PALETA).orElseThrow().partes());
  }

  @Test
  void unPatronSeCombinaConUnColorLiso() {
    MuestraDeColor muestra = MuestraDeColor.componer("Animal print / Negro", PALETA).orElseThrow();

    assertEquals(PatronDeColor.ANIMAL_PRINT, muestra.partes().get(0).patron());
    assertEquals(List.of("#C19A6B", "#3B2A1A"), muestra.partes().get(0).colores());
    assertEquals("#C19A6B", muestra.primerHex());
  }

  /**
   * El nombre se compara sin tildes ni mayúsculas, y los espacios alrededor de la barra no
   * importan.
   */
  @Test
  void elNombreSeComparaSinTildesNiMayusculas() {
    assertEquals(
        "#1F4E5F",
        MuestraDeColor.componer("azul petroleo/NEGRO", PALETA).orElseThrow().primerHex());
  }

  /** Un color escrito a mano no tiene con qué pintarse: sin muestra, en vez de una a medias. */
  @Test
  void unColorQueNoEstaEnLaPaletaNoTieneMuestra() {
    assertEquals(Optional.empty(), MuestraDeColor.componer("Negro / Fucsia eléctrico", PALETA));
    assertEquals(Optional.empty(), MuestraDeColor.componer(null, PALETA));
  }

  @Test
  void seCombinanHastaTresColores() {
    assertEquals(
        3, MuestraDeColor.componer("Negro / Rojo / Blanco", PALETA).orElseThrow().partes().size());
    assertThrows(
        ExcepcionDeDominio.class,
        () -> MuestraDeColor.componer("Negro / Rojo / Blanco / Azul petróleo", PALETA));
  }

  @Test
  void unaParteLisaEsUnColorYUnPatronAlMenosDos() {
    assertThrows(
        ExcepcionDeDominio.class, () -> new ParteDeMuestra(null, List.of("#111111", "#FFFFFF")));
    assertThrows(
        ExcepcionDeDominio.class,
        () -> new ParteDeMuestra(PatronDeColor.ESTAMPADO, List.of("#111111")));
    assertThrows(ExcepcionDeDominio.class, () -> ParteDeMuestra.lisa("negro"));
  }
}
