package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CorrectorDeTituloTest {

  /** El caso que pidió el negocio: «Body Herraje», con dos espacios. */
  @Test
  void bodyEsBodiYSeQuitanLosEspaciosDeMas() {
    assertEquals("Bodi Herraje", CorrectorDeTitulo.corregir("Body  Herraje"));
    assertEquals("Bodi herraje", CorrectorDeTitulo.corregir("  Body herraje "));
  }

  @Test
  void conservaLaMayusculaDeLaPalabraQueCorrige() {
    assertEquals("bodi manga larga", CorrectorDeTitulo.corregir("body manga larga"));
    assertEquals("Conjunto de Bodi", CorrectorDeTitulo.corregir("Conjunto de BODY"));
  }

  @Test
  void elPluralEsBodis() {
    assertEquals("Bodis de encaje", CorrectorDeTitulo.corregir("Bodies de encaje"));
    assertEquals("Bodis de encaje", CorrectorDeTitulo.corregir("Bodys de encaje"));
  }

  /** Solo la palabra entera: una bodega o un «embody» no son prendas. */
  @Test
  void noTocaPalabrasQueSoloEmpiezanIgual() {
    assertEquals("Bolso bodega", CorrectorDeTitulo.corregir("Bolso bodega"));
    assertEquals("Camiseta embody", CorrectorDeTitulo.corregir("Camiseta embody"));
  }

  @Test
  void nuloEsNulo() {
    assertNull(CorrectorDeTitulo.corregir(null));
  }

  /** La marca de réplica no es parte del nombre: el extractor la quita, y esto por si no. */
  @Test
  void quitaLaMarcaDeReplica() {
    assertEquals(
        "Tenis estilo Superstar", CorrectorDeTitulo.corregir("Tenis estilo Superstar AAA"));
    assertEquals(
        "Camiseta estilo Superdry", CorrectorDeTitulo.corregir("Camiseta estilo Superdry 1.1"));
  }
}
