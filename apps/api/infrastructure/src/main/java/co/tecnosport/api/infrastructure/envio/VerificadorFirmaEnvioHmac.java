package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.VerificadorFirmaEnvio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifica la firma del webhook de Skydropx. Sustituye al adaptador que rechazaba todo, y lo hace
 * con la documentación oficial delante y no con una pista de tercera mano
 * (docs/13-skydropx-capacidades.md, sección 6.1; adr/0022).
 *
 * <p><strong>Lo confirmado:</strong> la cabecera trae {@code HMAC <firma>} —el nombre de la
 * cabecera se configura en el panel, por eso es {@code SKYDROPX_CABECERA_FIRMA} y no una
 * constante—, la firma es HMAC con SHA-512 sobre los bytes crudos del cuerpo, con el secreto propio
 * del webhook, y viene en hexadecimal en minúsculas.
 *
 * <p><strong>Tres decisiones que este adaptador toma y conviene no deshacer sin pensarlas:</strong>
 *
 * <ul>
 *   <li><strong>Solo el esquema {@code HMAC}.</strong> La plataforma ofrece también {@code Bearer
 *       <token>} con un token estático, y su propia documentación lo llama el modo menos seguro: un
 *       token que viaja igual en cada petición es una contraseña, no una firma. No lo habilitamos,
 *       así que aquí no se acepta. Una firma sin esquema tampoco: si mañana el formato cambiara,
 *       prefiero que falle a que adivine.
 *   <li><strong>Comparación en tiempo constante</strong> con {@link MessageDigest#isEqual}. Un
 *       {@code equals} de cadenas corta en el primer carácter distinto, y ese tiempo se puede medir
 *       desde fuera para ir adivinando la firma byte a byte. El endpoint es público.
 *   <li><strong>Se comparan bytes, no el texto hexadecimal.</strong> Así el hex en mayúsculas —que
 *       no es lo que manda la plataforma, pero cuesta cero aceptar— no produce un rechazo
 *       incomprensible, y la comparación sigue siendo sobre los 64 bytes de verdad.
 * </ul>
 *
 * <p><strong>Lo que todavía no está probado, y hay que decirlo:</strong> el algoritmo está
 * verificado contra los vectores del RFC 4231, pero <em>ningún evento real de Skydropx ha pasado
 * por aquí</em> — la cuenta de sandbox no tiene créditos para emitir una guía. Lo que falta por
 * comprobar el día que llegue uno es que el secreto del panel sea el que creemos y que el cuerpo
 * llegue byte a byte como se firmó. Por eso el controlador recibe el cuerpo como {@code byte[]} y
 * lo decodifica en UTF-8 explícitamente: si el cuerpo se decodificara con otra codificación,
 * volverlo a codificar daría bytes distintos y la firma no cuadraría por un motivo que no se ve
 * leyendo este archivo.
 *
 * <p>Mientras el secreto valga el marcador de desarrollo, esto rechaza todos los eventos. Es lo
 * correcto: sin secreto no hay nada que verificar, y el seguimiento lo cubre entretanto la
 * conciliación programada, que no depende de la firma.
 */
public final class VerificadorFirmaEnvioHmac implements VerificadorFirmaEnvio {

  private static final String ALGORITMO = "HmacSHA512";

  /** Con el espacio: {@code HMAC <firma>}. */
  private static final String ESQUEMA = "HMAC ";

  private final byte[] secreto;

  public VerificadorFirmaEnvioHmac(String secreto) {
    Objects.requireNonNull(secreto, "El secreto del webhook de envíos no puede ser nulo.");
    this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public boolean esValida(String cuerpoCrudo, String firma) {
    if (cuerpoCrudo == null || firma == null) {
      return false;
    }
    byte[] recibida = firmaRecibida(firma);
    return recibida != null && MessageDigest.isEqual(calcular(cuerpoCrudo), recibida);
  }

  /**
   * Los bytes de la firma que trae la cabecera, o {@code null} si no es una firma que aceptemos.
   */
  private byte[] firmaRecibida(String cabecera) {
    String valor = cabecera.trim();
    if (valor.length() <= ESQUEMA.length()
        || !valor.regionMatches(true, 0, ESQUEMA, 0, ESQUEMA.length())) {
      return null;
    }
    String hex = valor.substring(ESQUEMA.length()).trim().toLowerCase(Locale.ROOT);
    try {
      return HexFormat.of().parseHex(hex);
    } catch (IllegalArgumentException hexInvalido) {
      return null;
    }
  }

  private byte[] calcular(String cuerpoCrudo) {
    try {
      Mac mac = Mac.getInstance(ALGORITMO);
      mac.init(new SecretKeySpec(secreto, ALGORITMO));
      return mac.doFinal(cuerpoCrudo.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.GeneralSecurityException imposible) {
      // HmacSHA512 lo exige la especificación de la plataforma Java, y la clave siempre tiene
      // bytes. Si esto ocurre, la instalación está rota y no es algo que este adaptador pueda
      // resolver descartando el evento en silencio.
      throw new IllegalStateException(
          "No se pudo calcular el HMAC del webhook de envíos.", imposible);
    }
  }
}
