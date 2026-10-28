package co.tecnosport.api.domain.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import org.junit.jupiter.api.Test;

class NombreDePersonaTest {

  @Test
  void recortaYAceptaNombresConTildeApostrofoGuionEInicial() {
    assertEquals("Ana Pérez", new NombreDePersona("  Ana Pérez ").valor());
    assertEquals("D'Angelo Ruiz-Mejía", new NombreDePersona("D'Angelo Ruiz-Mejía").valor());
    assertEquals("J. Gómez", new NombreDePersona("J. Gómez").valor());
  }

  @Test
  void rechazaVacioSimbolosDigitosYUnaSolaLetra() {
    assertThrows(ExcepcionDeDominio.class, () -> new NombreDePersona(null));
    assertThrows(ExcepcionDeDominio.class, () -> new NombreDePersona("   "));
    assertThrows(ExcepcionDeDominio.class, () -> new NombreDePersona("<script>"));
    assertThrows(ExcepcionDeDominio.class, () -> new NombreDePersona("Ana 3"));
    assertThrows(ExcepcionDeDominio.class, () -> new NombreDePersona("A."));
  }

  @Test
  void rechazaMasDeCientoVeinteCaracteres() {
    assertEquals(120, new NombreDePersona("a".repeat(120)).valor().length());
    assertThrows(ExcepcionDeDominio.class, () -> new NombreDePersona("a".repeat(121)));
  }
}
