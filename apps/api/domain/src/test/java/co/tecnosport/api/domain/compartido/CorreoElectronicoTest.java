package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CorreoElectronicoTest {

  @Test
  void normalizaAMinusculasYRecortaEspacios() {
    CorreoElectronico correo = new CorreoElectronico("  Cliente@TecnoSport.co  ");

    assertEquals("cliente@tecnosport.co", correo.valor());
  }

  @Test
  void rechazaVacio() {
    assertThrows(CorreoElectronicoInvalidoException.class, () -> new CorreoElectronico("   "));
  }

  @Test
  void rechazaNulo() {
    assertThrows(CorreoElectronicoInvalidoException.class, () -> new CorreoElectronico(null));
  }

  @Test
  void rechazaSinArroba() {
    assertThrows(
        CorreoElectronicoInvalidoException.class,
        () -> new CorreoElectronico("cliente-tecnosport.co"));
  }

  @Test
  void rechazaSinDominio() {
    assertThrows(CorreoElectronicoInvalidoException.class, () -> new CorreoElectronico("cliente@"));
  }
}
