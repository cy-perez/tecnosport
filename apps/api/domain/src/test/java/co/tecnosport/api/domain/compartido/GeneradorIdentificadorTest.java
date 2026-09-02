package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeneradorIdentificadorTest {

  @Test
  void generaUuidVersion7ConVarianteIetf() {
    UUID id = GeneradorIdentificador.nuevo();

    assertEquals(7, id.version());
    assertEquals(2, id.variant());
  }

  @Test
  void noRepiteIdentificadoresEnMilesDeGeneraciones() {
    Set<UUID> generados = new HashSet<>();
    for (int i = 0; i < 5000; i++) {
      generados.add(GeneradorIdentificador.nuevo());
    }

    assertEquals(5000, generados.size());
  }
}
