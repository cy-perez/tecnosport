package co.tecnosport.api.domain.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Las dos piezas de la bolsa de referencia (adr/0071): sus medidas y el peso de cada prenda. */
class ReferenciasDeEnvioTest {

  @Test
  void lasMedidasEnCeroSeRechazan() {
    assertThrows(ExcepcionDeDominio.class, () -> new MedidasDeReferencia(0, 30, 10));
    assertThrows(ExcepcionDeDominio.class, () -> new MedidasDeReferencia(40, 0, 10));
    assertThrows(ExcepcionDeDominio.class, () -> new MedidasDeReferencia(40, 30, 0));
  }

  @Test
  void lasMedidasNegativasSeRechazan() {
    assertThrows(ExcepcionDeDominio.class, () -> new MedidasDeReferencia(-40, 30, 10));
  }

  @Test
  void laBolsaCargadaLlevaLasMedidasYElPesoDeLoQueVaDentro() {
    Paquete bolsa = new MedidasDeReferencia(40, 30, 10).conPeso(1_600);

    assertEquals(new Paquete(1_600, 40, 30, 10), bolsa);
  }

  @Test
  void unPesoEnCeroSeRechaza() {
    assertThrows(ExcepcionDeDominio.class, () -> new PesoDeReferencia(UUID.randomUUID(), 0));
  }

  @Test
  void unPesoSinCategoriaSeRechaza() {
    assertThrows(NullPointerException.class, () -> new PesoDeReferencia(null, 300));
  }

  @Test
  void ropaCalzadoYBolsosSePromedian() {
    assertTrue(PesoDeReferencia.admiteLaLinea(LineaCatalogo.ROPA));
    assertTrue(PesoDeReferencia.admiteLaLinea(LineaCatalogo.CALZADO));
    assertTrue(PesoDeReferencia.admiteLaLinea(LineaCatalogo.BOLSOS));
  }

  /**
   * Un celular y un proyector no tienen nada que promediar: se miden con la ficha del fabricante.
   */
  @Test
  void laTecnologiaNoSePromedia() {
    assertFalse(PesoDeReferencia.admiteLaLinea(LineaCatalogo.TECNOLOGIA));
  }
}
