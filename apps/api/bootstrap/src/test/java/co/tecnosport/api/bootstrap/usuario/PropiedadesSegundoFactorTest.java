package co.tecnosport.api.bootstrap.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class PropiedadesSegundoFactorTest {

  private static final String LLAVE = Base64.getEncoder().encodeToString(new byte[32]);

  private static PropiedadesSegundoFactor con(String llave, int cuentaIp, int cuenta) {
    return new PropiedadesSegundoFactor(llave, true, 5, 5, cuentaIp, cuenta, 15, 15, "TecnoSport");
  }

  @Test
  void unaLlaveDe32BytesArranca() {
    assertEquals(32, con(LLAVE, 5, 10).llaveEnBytes().length);
  }

  @Test
  void sinLlaveOConElMarcadorSinResolverNoArranca() {
    IllegalStateException fallo =
        assertThrows(IllegalStateException.class, () -> con("${SEGUNDO_FACTOR_LLAVE}", 5, 10));
    assertTrue(fallo.getMessage().contains("SEGUNDO_FACTOR_LLAVE"));
    assertThrows(IllegalStateException.class, () -> con("", 5, 10));
    assertThrows(IllegalStateException.class, () -> con(null, 5, 10));
  }

  @Test
  void unaLlaveQueNoEsDe32BytesNoArranca() {
    assertThrows(
        IllegalStateException.class,
        () -> con(Base64.getEncoder().encodeToString(new byte[16]), 5, 10));
    assertThrows(
        IllegalStateException.class,
        () -> con(Base64.getEncoder().encodeToString(new byte[33]), 5, 10));
  }

  @Test
  void unaLlaveEnBase64UrlNoArranca() {
    byte[] bytes = new byte[32];
    bytes[0] = (byte) 0xfb; // en base64url sale un '-' o un '_', que el estándar no acepta
    bytes[1] = (byte) 0xff;
    String url = Base64.getUrlEncoder().encodeToString(bytes);
    assertTrue(url.contains("-") || url.contains("_"), url);
    assertThrows(IllegalStateException.class, () -> con(url, 5, 10));
  }

  @Test
  void porCuentaSolaNoPuedeSerMenorQuePorCuentaEIp() {
    assertThrows(IllegalStateException.class, () -> con(LLAVE, 10, 5));
  }

  @Test
  void losNumerosTienenQueSerPositivos() {
    assertThrows(
        IllegalStateException.class,
        () -> new PropiedadesSegundoFactor(LLAVE, true, 0, 5, 5, 10, 15, 15, "TecnoSport"));
    assertThrows(
        IllegalStateException.class,
        () -> new PropiedadesSegundoFactor(LLAVE, true, 5, 5, 5, 10, 15, 15, " "));
  }
}
