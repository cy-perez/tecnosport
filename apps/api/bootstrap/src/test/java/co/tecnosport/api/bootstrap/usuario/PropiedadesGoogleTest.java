package co.tecnosport.api.bootstrap.usuario;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Las llaves y el script solo pueden salir de Google, por https (ADR-0074). */
class PropiedadesGoogleTest {

  private static final String LLAVES = "https://www.googleapis.com/oauth2/v3/certs";
  private static final String SCRIPT = "https://accounts.google.com/gsi/client";

  @Test
  void lasDeGoogleSeAceptan() {
    assertDoesNotThrow(() -> new PropiedadesGoogle("", LLAVES, SCRIPT));
  }

  @Test
  void unasLlavesQueNoSonDeGoogleNoArrancan() {
    assertThrows(
        IllegalStateException.class,
        () -> new PropiedadesGoogle("", "https://atacante.example/certs", SCRIPT));
  }

  @Test
  void unScriptPorHttpNoArranca() {
    assertThrows(
        IllegalStateException.class,
        () -> new PropiedadesGoogle("", LLAVES, "http://accounts.google.com/gsi/client"));
  }
}
