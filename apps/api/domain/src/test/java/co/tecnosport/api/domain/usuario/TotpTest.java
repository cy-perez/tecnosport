package co.tecnosport.api.domain.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class TotpTest {

  /** La clave de los vectores de prueba de la RFC 6238, Apéndice B, para SHA-1. */
  private static final ClaveTotp RFC =
      new ClaveTotp("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

  private static String codigoA(long segundos) {
    return Totp.codigoEn(RFC, Totp.pasoDe(Instant.ofEpochSecond(segundos)));
  }

  /**
   * Los vectores del Apéndice B son de ocho dígitos; los seis de la derecha son el código de seis,
   * porque los dos se sacan del mismo entero con módulo 10^8 y 10^6.
   */
  @Test
  void coincideConLosVectoresDeLaRfc6238() {
    assertEquals("287082", codigoA(59));
    assertEquals("081804", codigoA(1111111109));
    assertEquals("050471", codigoA(1111111111));
    assertEquals("005924", codigoA(1234567890));
    assertEquals("279037", codigoA(2000000000));
  }

  @Test
  void elPasoEsDeTreintaSegundos() {
    assertEquals(1, Totp.pasoDe(Instant.ofEpochSecond(59)));
    assertEquals(2, Totp.pasoDe(Instant.ofEpochSecond(60)));
  }

  @Test
  void aceptaElPasoAnteriorElActualYElSiguiente() {
    Instant ahora = Instant.ofEpochSecond(1111111111);
    long paso = Totp.pasoDe(ahora);
    for (long delta = -1; delta <= 1; delta++) {
      OptionalLong aceptado = Totp.verificar(RFC, Totp.codigoEn(RFC, paso + delta), ahora, null);
      assertEquals(OptionalLong.of(paso + delta), aceptado, "delta " + delta);
    }
  }

  @Test
  void rechazaDosPasosAtrasYDosAdelante() {
    Instant ahora = Instant.ofEpochSecond(1111111111);
    long paso = Totp.pasoDe(ahora);
    assertTrue(Totp.verificar(RFC, Totp.codigoEn(RFC, paso - 2), ahora, null).isEmpty());
    assertTrue(Totp.verificar(RFC, Totp.codigoEn(RFC, paso + 2), ahora, null).isEmpty());
  }

  @Test
  void unCodigoYaUsadoNoSeAceptaDosVecesNiUnoMasViejo() {
    Instant ahora = Instant.ofEpochSecond(1111111111);
    long paso = Totp.pasoDe(ahora);
    assertTrue(Totp.verificar(RFC, Totp.codigoEn(RFC, paso), ahora, paso).isEmpty());
    assertTrue(Totp.verificar(RFC, Totp.codigoEn(RFC, paso - 1), ahora, paso).isEmpty());
    assertEquals(
        OptionalLong.of(paso + 1), Totp.verificar(RFC, Totp.codigoEn(RFC, paso + 1), ahora, paso));
  }

  @Test
  void soloSeisDigitosAsciiTienenFormatoDeCodigo() {
    assertTrue(Totp.esFormatoDeCodigo("012345"));
    assertFalse(Totp.esFormatoDeCodigo("12345"));
    assertFalse(Totp.esFormatoDeCodigo("1234567"));
    assertFalse(Totp.esFormatoDeCodigo("12 345"));
    assertFalse(Totp.esFormatoDeCodigo("+12345"));
    // Dígitos árabe-índicos: Character.isDigit diría que sí.
    assertFalse(Totp.esFormatoDeCodigo("١٢٣٤٥٦"));
    assertFalse(Totp.esFormatoDeCodigo(null));
    assertTrue(Totp.verificar(RFC, "abcdef", Instant.ofEpochSecond(59), null).isEmpty());
  }
}
