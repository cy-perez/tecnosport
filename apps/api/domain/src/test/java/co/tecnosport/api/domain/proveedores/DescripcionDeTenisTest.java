package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** La descripción de un tenis: título, género, tallas y cómo está hecho (ADR-0092). */
class DescripcionDeTenisTest {

  private static final List<String> DAMA = List.of("35", "36", "37", "38");
  private static final List<String> UNISEX =
      List.of("35", "36", "37", "38", "39", "40", "41", "42", "43");

  @Test
  void elTenisDeDamaDiceParaDamaYSuRango() {
    assertEquals(
        "Tenis importados estilo New Balance 9060 para dama. Disponibles desde la talla 35 hasta"
            + " la 38.",
        DescripcionDeTenis.componer(
            "Tenis importados estilo New Balance 9060", GeneroDeCalzado.DAMA, DAMA, null));
  }

  @Test
  void elUnisexNoLlevaPara() {
    assertEquals(
        "Tenis importados estilo Alexander McQueen unisex. Disponibles desde la talla 35 hasta la"
            + " 43.",
        DescripcionDeTenis.componer(
            "Tenis importados estilo Alexander McQueen", GeneroDeCalzado.UNISEX, UNISEX, " "));
  }

  @Test
  void lasCaracteristicasVanAlFinalComoFrase() {
    assertEquals(
        "Tenis importados estilo Nike para caballero. Disponibles desde la talla 38 hasta la 43."
            + " Ultralivianos, con suela EVA y cordones encerados.",
        DescripcionDeTenis.componer(
            "Tenis importados estilo Nike",
            GeneroDeCalzado.CABALLERO,
            List.of("43", "38", "40", "39", "41", "42"),
            "ultralivianos, con suela EVA y cordones encerados"));
  }

  @Test
  void unaSolaTallaNoEsUnRango() {
    assertEquals(
        "Tenis importados estilo Nike para caballero. Disponibles en la talla 42.",
        DescripcionDeTenis.componer(
            "Tenis importados estilo Nike", GeneroDeCalzado.CABALLERO, List.of("42"), null));
  }

  @Test
  void loQueNoSeSabeNoSeEscribe() {
    assertEquals(
        "Tenis importados. Suela importada.",
        DescripcionDeTenis.componer("Tenis importados", null, List.of(), "Suela importada."));
    assertEquals(
        "Tenis importados para dama.",
        DescripcionDeTenis.componer(
            "Tenis importados", GeneroDeCalzado.DAMA, List.of("S", "M"), null));
    assertNull(DescripcionDeTenis.componer(" ", GeneroDeCalzado.DAMA, DAMA, "Suela EVA."));
  }

  /** Quien revisa corrige el modelo: la descripción compuesta deja ver sus características. */
  @Test
  void lasCaracteristicasSeRecuperanDeLaDescripcionCompuesta() {
    String compuesta =
        DescripcionDeTenis.componer(
            "Tenis importados estilo Nike", GeneroDeCalzado.DAMA, DAMA, "Suela EVA.");

    assertEquals(
        Optional.of("Suela EVA."),
        DescripcionDeTenis.caracteristicasDe(
            compuesta, "Tenis importados estilo Nike", GeneroDeCalzado.DAMA, DAMA));
    assertEquals(
        Optional.of(""),
        DescripcionDeTenis.caracteristicasDe(
            DescripcionDeTenis.componer(
                "Tenis importados estilo Nike", GeneroDeCalzado.DAMA, DAMA, null),
            "Tenis importados estilo Nike",
            GeneroDeCalzado.DAMA,
            DAMA));
  }

  @Test
  void unaDescripcionReescritaNoSeRecompone() {
    assertEquals(
        Optional.empty(),
        DescripcionDeTenis.caracteristicasDe(
            "Un tenis cómodo para todos los días.",
            "Tenis importados estilo Nike",
            GeneroDeCalzado.DAMA,
            DAMA));
    assertEquals(
        Optional.empty(),
        DescripcionDeTenis.caracteristicasDe(
            "Tenis importados estilo Nike para dama. Disponibles desde la talla 35 hasta la 38.",
            "Tenis importados estilo Nike",
            GeneroDeCalzado.CABALLERO,
            DAMA));
  }
}
