package co.tecnosport.api.infrastructure.correo;

import java.util.regex.Pattern;

/**
 * Tapa las direcciones de correo de un texto que va a un registro.
 *
 * <p>Existe por un caso concreto: un rechazo de SMTP <b>repite la dirección</b> en su mensaje
 * —{@code 550 5.1.1 <cliente@ejemplo.com>: Recipient address rejected}— y {@code
 * TransporteDeCorreoSpringMail} volcaba la excepción entera con {@code log.error(msg, excepcion)},
 * mientras su propio javadoc prometía que "ni el mensaje ni el registro llevan el correo del
 * destinatario". La garantía escrita era más fuerte que el código, y por ahí pasan los siete
 * correos transaccionales.
 *
 * <p>Se tapa y no se borra el mensaje entero porque lo que sirve para diagnosticar es el código
 * —{@code 550}, {@code 5.1.1}, "Recipient address rejected"— y eso no es un dato personal.
 * docs/08-seguridad-legal.md: registros sin datos personales ni tokens.
 */
final class RedaccionDeCorreos {

  /**
   * Deliberadamente amplio y no una validación de RFC: aquí no se trata de decidir si algo es un
   * correo válido sino de que no se escape nada con forma de dirección.
   */
  private static final Pattern CORREO = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

  private RedaccionDeCorreos() {}

  static String sinCorreos(String texto) {
    return texto == null ? "" : CORREO.matcher(texto).replaceAll("***@***");
  }
}
