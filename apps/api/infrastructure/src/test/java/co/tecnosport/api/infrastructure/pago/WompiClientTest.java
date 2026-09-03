package co.tecnosport.api.infrastructure.pago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Sin vector de prueba de Wompi fijado aquí a propósito (regla dura #9): un intento anterior de
 * traer un checksum "de ejemplo" de la documentación pública resultó estar fabricado por la
 * herramienta que resumió la página — no coincidía con el SHA-256 real de la cadena que la misma
 * documentación decía usar, verificado calculándolo aparte. Mejor sin ese vector que con uno falso.
 *
 * <p>{@code verificarFirmaEvento} sí se prueba contra un checksum calculado de forma independiente
 * aquí mismo, con {@link MessageDigest} directo — confirma que la implementación de {@code
 * WompiClient} aplica el algoritmo documentado (concatenar los valores en orden, el timestamp y el
 * secreto, y hashear con SHA-256) de forma consistente, aunque no verifica que ese algoritmo sea
 * exactamente el que usa Wompi en producción — eso queda pendiente de confirmar contra su sandbox
 * real.
 */
class WompiClientTest {

  private static final ReferenciaPago REFERENCIA = new ReferenciaPago("TS-2026-000001-1");
  private static final Dinero MONTO = Dinero.deCop(100_000);

  private WompiClient cliente(String secretoEventos) {
    return new WompiClient("secreto-integridad-de-prueba", secretoEventos);
  }

  @Test
  void laFirmaDeIntegridadEsDeterministaParaLosMismosDatos() {
    WompiClient cliente = cliente("secreto-eventos-de-prueba");

    String primera = cliente.generarFirmaIntegridad(REFERENCIA, MONTO);
    String segunda = cliente.generarFirmaIntegridad(REFERENCIA, MONTO);

    assertEquals(primera, segunda);
  }

  @Test
  void laFirmaDeIntegridadEsUnHexadecimalDeSesentaYCuatroCaracteres() {
    WompiClient cliente = cliente("secreto-eventos-de-prueba");

    String firma = cliente.generarFirmaIntegridad(REFERENCIA, MONTO);

    assertEquals(64, firma.length());
    assertTrue(firma.matches("^[0-9a-f]{64}$"));
  }

  @Test
  void laFirmaDeIntegridadCambiaSiCambiaElMonto() {
    WompiClient cliente = cliente("secreto-eventos-de-prueba");

    String firmaOriginal = cliente.generarFirmaIntegridad(REFERENCIA, MONTO);
    String firmaConOtroMonto = cliente.generarFirmaIntegridad(REFERENCIA, Dinero.deCop(200_000));

    assertNotEquals(firmaOriginal, firmaConOtroMonto);
  }

  @Test
  void laFirmaDeIntegridadCambiaSiCambiaLaReferencia() {
    WompiClient cliente = cliente("secreto-eventos-de-prueba");

    String firmaOriginal = cliente.generarFirmaIntegridad(REFERENCIA, MONTO);
    String firmaConOtraReferencia =
        cliente.generarFirmaIntegridad(new ReferenciaPago("TS-2026-000001-2"), MONTO);

    assertNotEquals(firmaOriginal, firmaConOtraReferencia);
  }

  @Test
  void secretoDeIntegridadVacioSeRechazaAlConstruir() {
    assertThrows(
        IllegalArgumentException.class, () -> new WompiClient("  ", "secreto-eventos-de-prueba"));
  }

  @Test
  void secretoDeEventosVacioSeRechazaAlConstruir() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new WompiClient("secreto-integridad-de-prueba", "  "));
  }

  private static final List<String> VALORES = List.of("wompi-tx-1", "APPROVED", "100000");
  private static final long TIMESTAMP = 1_700_000_000L;
  private static final String SECRETO_EVENTOS = "secreto-eventos-de-prueba";

  @Test
  void verificaUnChecksumCalculadoDeFormaIndependienteConElMismoAlgoritmo() {
    WompiClient cliente = cliente(SECRETO_EVENTOS);
    String checksumEsperado = sha256HexIndependiente(VALORES, TIMESTAMP, SECRETO_EVENTOS);

    boolean valida = cliente.verificarFirmaEvento(VALORES, TIMESTAMP, checksumEsperado);

    assertTrue(valida);
  }

  @Test
  void laComparacionEsInsensibleAMayusculas() {
    WompiClient cliente = cliente(SECRETO_EVENTOS);
    String checksumEnMayusculas =
        sha256HexIndependiente(VALORES, TIMESTAMP, SECRETO_EVENTOS).toUpperCase(Locale.ROOT);

    boolean valida = cliente.verificarFirmaEvento(VALORES, TIMESTAMP, checksumEnMayusculas);

    assertTrue(valida);
  }

  @Test
  void unChecksumIncorrectoSeRechaza() {
    WompiClient cliente = cliente(SECRETO_EVENTOS);

    boolean valida = cliente.verificarFirmaEvento(VALORES, TIMESTAMP, "0".repeat(64));

    assertFalse(valida);
  }

  @Test
  void unSecretoDeEventosDistintoRechazaElChecksumEsperado() {
    WompiClient cliente = cliente("otro-secreto-distinto");
    String checksumConElSecretoOriginal =
        sha256HexIndependiente(VALORES, TIMESTAMP, SECRETO_EVENTOS);

    boolean valida = cliente.verificarFirmaEvento(VALORES, TIMESTAMP, checksumConElSecretoOriginal);

    assertFalse(valida);
  }

  @Test
  void unTimestampDistintoRechazaElChecksumEsperado() {
    WompiClient cliente = cliente(SECRETO_EVENTOS);
    String checksumConElTimestampOriginal =
        sha256HexIndependiente(VALORES, TIMESTAMP, SECRETO_EVENTOS);

    boolean valida =
        cliente.verificarFirmaEvento(VALORES, TIMESTAMP + 1, checksumConElTimestampOriginal);

    assertFalse(valida);
  }

  private static String sha256HexIndependiente(
      List<String> valores, long timestamp, String secreto) {
    try {
      String cadena = String.join("", valores) + timestamp + secreto;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(cadena.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
