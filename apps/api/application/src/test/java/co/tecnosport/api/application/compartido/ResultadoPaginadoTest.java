package co.tecnosport.api.application.compartido;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultadoPaginadoTest {

  @Test
  void sinCursorSiguienteNoHayMasPaginas() {
    ResultadoPaginado<String> resultado = new ResultadoPaginado<>(List.of("a"), null);

    assertFalse(resultado.tieneSiguiente());
  }

  @Test
  void conCursorSiguienteHayMasPaginas() {
    ResultadoPaginado<String> resultado = new ResultadoPaginado<>(List.of("a"), "cursor-2");

    assertTrue(resultado.tieneSiguiente());
  }

  @Test
  void losItemsSonInmutables() {
    ResultadoPaginado<String> resultado =
        new ResultadoPaginado<>(new ArrayList<>(List.of("a")), null);

    assertThrows(UnsupportedOperationException.class, () -> resultado.items().add("b"));
  }
}
