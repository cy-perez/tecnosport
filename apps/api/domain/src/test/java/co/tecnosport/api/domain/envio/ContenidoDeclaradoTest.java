package co.tecnosport.api.domain.envio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContenidoDeclaradoTest {

  /**
   * El texto esperado se escribe aquí y no se calcula, que es lo que separa esta prueba de una que
   * le pregunte al código lo mismo que el código responde.
   */
  private static final Map<LineaCatalogo, String> ESPERADOS = new EnumMap<>(LineaCatalogo.class);

  static {
    ESPERADOS.put(LineaCatalogo.ROPA, "Ropa deportiva");
    ESPERADOS.put(LineaCatalogo.CALZADO, "Calzado deportivo");
    ESPERADOS.put(LineaCatalogo.BOLSOS, "Bolsos y morrales");
    ESPERADOS.put(LineaCatalogo.TECNOLOGIA, "Electrónica y accesorios");
  }

  @Test
  void cadaLineaDeclaraLoSuyo() {
    ESPERADOS.forEach((linea, esperado) -> assertEquals(esperado, ContenidoDeclarado.de(linea)));
  }

  /**
   * El compilador ya obliga a ampliar el {@code switch} cuando entra una línea nueva, porque no
   * tiene {@code default}. Esta prueba cubre el atajo que lo desactiva: quien agregue un {@code
   * default} para que compile deja la línea nueva declarada con la etiqueta de otra cosa, y eso
   * compila perfectamente. Aquí falla, y el mensaje dice qué hay que decidir.
   */
  @Test
  void ningunaLineaSeQuedaSinEtiquetaDecidida() {
    for (LineaCatalogo linea : LineaCatalogo.values()) {
      assertTrue(
          ESPERADOS.containsKey(linea),
          "La línea "
              + linea
              + " no tiene contenido declarado decidido. No basta con que compile: hay que elegir"
              + " qué dice la etiqueta de la caja, que tiene que ser verdad para todas sus"
              + " categorías y no anunciar qué hay dentro.");
    }
  }

  /** Lo que se le declara a la transportadora nunca puede ser una cadena vacía. */
  @Test
  void ningunContenidoVaVacio() {
    for (LineaCatalogo linea : LineaCatalogo.values()) {
      String contenido = ContenidoDeclarado.de(linea);
      assertNotNull(contenido);
      assertTrue(!contenido.isBlank(), "El contenido declarado de " + linea + " está en blanco.");
    }
  }

  /**
   * Sin línea no hay etiqueta que valga. Falla ruidoso antes de emitir la guía es mejor que un
   * bulto declarado como {@code null} en el cuerpo que va al proveedor.
   */
  @Test
  void rechazaLaLineaNula() {
    assertThrows(NullPointerException.class, () -> ContenidoDeclarado.de(null));
  }
}
