package co.tecnosport.api.infrastructure.envio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * La firma del webhook de Skydropx.
 *
 * <p><strong>El valor esperado no lo calcula esta prueba.</strong> Si lo hiciera con el mismo
 * {@code Mac.getInstance("HmacSHA512")} que usa el adaptador, las dos partes se equivocarían igual
 * y la prueba pasaría con el algoritmo cambiado — que es exactamente el fallo que se quiere
 * detectar. Sale del <strong>caso de prueba 2 del RFC 4231</strong>, que publica vectores de
 * HMAC-SHA-512: clave {@code Jefe}, datos {@code what do ya want for nothing?}. Las dos son cadenas
 * ASCII, así que encajan sin convertir nada.
 *
 * <p>Lo que estas pruebas <strong>no</strong> demuestran: que un evento real de Skydropx pase. Eso
 * necesita una guía emitida, y la cuenta de sandbox no tiene créditos
 * (docs/13-skydropx-capacidades.md, sección 6). Aquí se comprueba el algoritmo y el formato de la
 * cabecera; el secreto del panel y la forma del cuerpo siguen sin medirse.
 */
class VerificadorFirmaEnvioHmacTest {

  /** RFC 4231, caso de prueba 2. */
  private static final String SECRETO = "Jefe";

  private static final String CUERPO = "what do ya want for nothing?";

  private static final String FIRMA_ESPERADA =
      "164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea250554"
          + "9758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737";

  private final VerificadorFirmaEnvioHmac verificador = new VerificadorFirmaEnvioHmac(SECRETO);

  @Test
  void aceptaElVectorDelRfc4231() {
    assertTrue(verificador.esValida(CUERPO, "HMAC " + FIRMA_ESPERADA));
  }

  /**
   * El nombre de la cabecera se elige en el panel y el esquema lo escribe la plataforma; que llegue
   * {@code hmac} en minúsculas no es motivo para descartar un evento legítimo.
   */
  @Test
  void elEsquemaEsInsensibleAMayusculas() {
    assertTrue(verificador.esValida(CUERPO, "hmac " + FIRMA_ESPERADA));
    assertTrue(verificador.esValida(CUERPO, "  HMAC  " + FIRMA_ESPERADA + "  "));
  }

  /**
   * La plataforma manda el hexadecimal en minúsculas; aceptarlo también en mayúsculas cuesta cero y
   * evita un rechazo incomprensible. Se comparan bytes, no texto.
   */
  @Test
  void aceptaElHexadecimalEnMayusculas() {
    assertTrue(verificador.esValida(CUERPO, "HMAC " + FIRMA_ESPERADA.toUpperCase(Locale.ROOT)));
  }

  /**
   * Un cuerpo con tildes firmado y verificado como UTF-8 da el mismo resultado. Es la otra mitad de
   * la decisión del controlador de recibir {@code byte[]}: aquí se fija la codificación del lado
   * que calcula.
   */
  @Test
  void elCuerpoConAcentosSeFirmaEnUtf8() {
    String cuerpo = "{\"ciudad\":\"Medellín\",\"estado\":\"in_transit\"}";
    VerificadorFirmaEnvioHmac otro = new VerificadorFirmaEnvioHmac("secreto-de-prueba");

    // No se afirma un valor: se afirma que el mismo cuerpo verifica contra su propia firma y que
    // una variación de un solo carácter no. El valor absoluto ya lo fija el vector del RFC.
    String firma = firmaDe("secreto-de-prueba", cuerpo);

    assertTrue(otro.esValida(cuerpo, "HMAC " + firma));
    assertFalse(otro.esValida(cuerpo.replace("Medellín", "Medellin"), "HMAC " + firma));
  }

  @Test
  void rechazaLaFirmaDeOtroSecreto() {
    VerificadorFirmaEnvioHmac conOtroSecreto = new VerificadorFirmaEnvioHmac("otro-secreto");

    assertFalse(conOtroSecreto.esValida(CUERPO, "HMAC " + FIRMA_ESPERADA));
  }

  @Test
  void rechazaUnCuerpoDistinto() {
    assertFalse(verificador.esValida(CUERPO + " ", "HMAC " + FIRMA_ESPERADA));
  }

  /**
   * El otro modo que ofrece la plataforma es un token estático, que su propia documentación llama
   * el menos seguro y que no habilitamos. Si llegara, no se acepta.
   */
  @Test
  void rechazaElEsquemaBearer() {
    assertFalse(verificador.esValida(CUERPO, "Bearer " + FIRMA_ESPERADA));
  }

  /** Sin esquema no se adivina: si el formato cambiara, es mejor fallar que suponer. */
  @Test
  void rechazaLaFirmaSinEsquema() {
    assertFalse(verificador.esValida(CUERPO, FIRMA_ESPERADA));
  }

  @Test
  void rechazaLoQueNoEsHexadecimal() {
    assertFalse(verificador.esValida(CUERPO, "HMAC no-es-hexadecimal"));
    assertFalse(verificador.esValida(CUERPO, "HMAC abc"));
    assertFalse(verificador.esValida(CUERPO, "HMAC "));
    assertFalse(verificador.esValida(CUERPO, "HMAC"));
  }

  @Test
  void rechazaLoVacioYLoNulo() {
    assertFalse(verificador.esValida(CUERPO, null));
    assertFalse(verificador.esValida(CUERPO, ""));
    assertFalse(verificador.esValida(null, "HMAC " + FIRMA_ESPERADA));
  }

  /**
   * Mientras el secreto valga el marcador de desarrollo de {@code application.yml}, ningún evento
   * firmado de verdad pasa. Es el estado del sistema hoy y conviene que esté escrito: el webhook
   * está cableado y no verifica nada todavía.
   */
  @Test
  void conElMarcadorDeDesarrolloNoPasaNingunEvento() {
    VerificadorFirmaEnvioHmac sinSecretoReal =
        new VerificadorFirmaEnvioHmac("secreto_pendiente_de_configurar");

    assertFalse(sinSecretoReal.esValida(CUERPO, "HMAC " + firmaDe("el-secreto-real", CUERPO)));
  }

  /** Auxiliar de la prueba, no del adaptador: firma con la biblioteca estándar. */
  private static String firmaDe(String secreto, String cuerpo) {
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
      mac.init(
          new javax.crypto.spec.SecretKeySpec(
              secreto.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA512"));
      return java.util.HexFormat.of()
          .formatHex(mac.doFinal(cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException imposible) {
      throw new IllegalStateException(imposible);
    }
  }
}
