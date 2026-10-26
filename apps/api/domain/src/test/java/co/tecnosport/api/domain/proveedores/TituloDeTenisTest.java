package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/** El título de todo el calzado (ADR-0089, 25 de octubre de 2026). */
class TituloDeTenisTest {

  @Test
  void laMarcaYElModeloQueElLectorDaPorSeguro() {
    assertEquals(
        "Tenis importados estilo Nike Air Force 1", TituloDeTenis.componer("Nike", "Air Force 1"));
    assertEquals("Tenis importados estilo Nike", TituloDeTenis.componer("Nike", null));
    assertEquals("Tenis importados", TituloDeTenis.componer(null, "Air Force 1"));
    assertEquals("Tenis importados", TituloDeTenis.componer("  ", null));
  }

  /** Si el lector escribe el modelo con la marca delante, no se repite. */
  @Test
  void elModeloQueEmpiezaPorLaMarcaNoLaRepite() {
    assertEquals(
        "Tenis importados estilo Nike Air Max 90",
        TituloDeTenis.componer("Nike", "Nike Air Max 90"));
  }

  /** Los títulos que el extractor escribía, con la forma nueva y sin el género. */
  @Test
  void losTitulosDeAntesTomanLaFormaNueva() {
    assertEquals(
        "Tenis importados estilo Nike",
        TituloDeTenis.desde("Tenis estilo Nike para dama", List.of()));
    assertEquals(
        "Tenis importados estilo Superstar",
        TituloDeTenis.desde("Tenis estilo Superstar", List.of()));
    assertEquals(
        "Tenis importados estilo Adidas Campus",
        TituloDeTenis.desde("Tenis Adidas Campus para caballero importado", List.of("Adidas")));
    assertEquals(
        "Tenis importados estilo New Balance",
        TituloDeTenis.desde("Tenis deportivo para dama", List.of("New Balance")));
    assertEquals(
        "Tenis importados", TituloDeTenis.desde("Tenis para caballero tipo media", List.of()));
    assertNull(TituloDeTenis.desde(null, List.of("Nike")));
  }

  @Test
  void enInglesParaElTextoAlternativo() {
    assertEquals(
        "Imported Nike Air Force 1-style sneakers", TituloDeTenis.enIngles("Nike", "Air Force 1"));
    assertEquals("Imported sneakers", TituloDeTenis.enIngles(null, null));
  }
}
