package co.tecnosport.api.infrastructure.correo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * La forma exacta en que un servidor SMTP devuelve un rechazo, que es de donde venía la fuga: el
 * mensaje repite la dirección y {@code TransporteDeCorreoSpringMail} lo volcaba entero en un
 * registro, mientras su javadoc prometía lo contrario.
 */
class RedaccionDeCorreosTest {

  @Test
  void tapaLaDireccionYConservaElCodigoDelRechazo() {
    String crudo = "550 5.1.1 <cliente@ejemplo.com>: Recipient address rejected: User unknown";

    String redactado = RedaccionDeCorreos.sinCorreos(crudo);

    assertFalse(redactado.contains("cliente@ejemplo.com"));
    // Lo que sirve para diagnosticar se queda.
    assertEquals("550 5.1.1 <***@***>: Recipient address rejected: User unknown", redactado);
  }

  @Test
  void tapaTodasLasQueHaya() {
    String redactado =
        RedaccionDeCorreos.sinCorreos("de tienda@tecnosport.co para alguien@gmail.com");

    assertFalse(redactado.contains("tecnosport.co"));
    assertFalse(redactado.contains("gmail.com"));
  }

  @Test
  void unTextoSinCorreosNoCambia() {
    assertEquals("Connection timed out", RedaccionDeCorreos.sinCorreos("Connection timed out"));
  }

  @Test
  void unMensajeNuloNoRevienta() {
    assertEquals("", RedaccionDeCorreos.sinCorreos(null));
  }
}
