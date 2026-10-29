package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Con los borradores de Meraki del 28 de octubre de 2026. */
class ArticuloQueNoSeVendeTest {

  private static Optional<String> dato(String titulo) {
    return ArticuloQueNoSeVende.porElTitulo(titulo)
        .map(
            c -> {
              assertEquals(MotivoDePublicacion.ARTICULO_QUE_NO_SE_VENDE, c.motivo());
              return c.dato();
            });
  }

  @Test
  void laPijamaSeDescartaLaNombreComoLaNombre() {
    assertEquals(Optional.of("pijama"), dato("Pijama short"));
    assertEquals(Optional.of("pijama"), dato("Conjunto pijama de dama"));
    assertEquals(Optional.of("pijama"), dato("Set de PIJAMAS"));
  }

  @Test
  void laCorreaYElCinturonSoloSiElTituloEmpiezaPorAhi() {
    assertEquals(Optional.of("correa"), dato("Correa importada"));
    assertEquals(Optional.of("cinturón"), dato("Cinturón de cuero"));
    assertEquals(Optional.empty(), dato("Bolso con correa ancha"));
    assertEquals(Optional.empty(), dato("Morral correa ajustable"));
  }

  @Test
  void loDemasSeVende() {
    assertEquals(Optional.empty(), dato("Polo bordado estilo Tommy"));
    assertEquals(Optional.empty(), dato(null));
  }
}
