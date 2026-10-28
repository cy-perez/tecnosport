package co.tecnosport.api.infrastructure.correo;

import co.tecnosport.api.domain.compartido.CorreoElectronico;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dirección "De:" de los correos transaccionales (docs/07-infra-gcp.md: {@code CORREO_REMITENTE}).
 * Dato de negocio real, no inventado aquí — el valor por defecto en {@code application.yml} es el
 * dominio real del proyecto, no un placeholder.
 *
 * <p>{@code responderA} ({@code CORREO_RESPONDER_A}) es a dónde llega lo que el cliente conteste.
 * Entró el 27 de octubre de 2026 (adr/0098): el remitente es {@code no-responder@} y cuatro correos
 * —entre ellos el acuse de retracto, que pide el medio para el reintegro— le decían al cliente
 * «respóndenos este correo». Esas respuestas se perdían. Ahora van al buzón que atiende una
 * persona, el mismo del pie del sitio.
 */
@ConfigurationProperties(prefix = "tecnosport.correo")
public record PropiedadesCorreo(String remitente, String responderA) {

  public PropiedadesCorreo {
    if (remitente == null || remitente.isBlank()) {
      throw new IllegalStateException("tecnosport.correo.remitente no puede estar vacío.");
    }
    if (responderA == null || responderA.isBlank()) {
      throw new IllegalStateException("tecnosport.correo.responder-a no puede estar vacío.");
    }
    // Validado al arrancar, como pide la regla dura #5: un Reply-To mal escrito no falla al
    // mandar, falla en silencio cuando alguien contesta.
    new CorreoElectronico(responderA);
  }
}
