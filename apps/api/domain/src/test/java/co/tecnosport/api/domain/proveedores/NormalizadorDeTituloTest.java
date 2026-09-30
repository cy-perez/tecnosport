package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NormalizadorDeTituloTest {

  @Test
  void quitaTildesEmojisSignosYMayusculas() {
    assertEquals(
        "bolso de dama mediano", NormalizadorDeTitulo.normalizar("  Bolso de DAMA  mediano 👜 "));
    assertEquals("conjunto pantalon", NormalizadorDeTitulo.normalizar("*CONJUNTO PANTALÓN*"));
    assertEquals(
        "bolso mediano ejecutivo importado",
        NormalizadorDeTitulo.normalizar("👜 *BOLSO MEDIANO EJECUTIVO IMPORTADO* 🔝"));
  }

  /** Dos escrituras del mismo producto tienen que dar lo mismo: de eso depende la huella. */
  @Test
  void dosEscriturasDelMismoProductoCoinciden() {
    assertEquals(
        NormalizadorDeTitulo.normalizar("Bolso de dama mediano 👜"),
        NormalizadorDeTitulo.normalizar("BOLSO DE DAMA MEDIANO"));
    assertEquals(
        NormalizadorDeTitulo.normalizar("Morral dúo"),
        NormalizadorDeTitulo.normalizar("morral duo"));
  }

  @Test
  void conservaLosNumeros() {
    assertEquals("polo 1 1 puma", NormalizadorDeTitulo.normalizar("POLO 1.1 PUMA"));
  }

  @Test
  void nuloYVacioDanVacio() {
    assertEquals("", NormalizadorDeTitulo.normalizar(null));
    assertEquals("", NormalizadorDeTitulo.normalizar("🌈🌸"));
  }
}
