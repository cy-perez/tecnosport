package co.tecnosport.api.application.compartido;

import co.tecnosport.api.domain.compartido.CorreoElectronico;

/**
 * Manda un correo <b>de verdad</b>, contra el servidor SMTP, aquí y ahora.
 *
 * <p>Tiene la misma firma que {@link EnviadorDeCorreo} y eso no es una duplicación: es la
 * distinción entera. {@code EnviadorDeCorreo} es lo que llaman los casos de uso y hoy significa
 * <b>encolar</b>; esto es lo que hace el envío, y lo llama un solo sitio — {@link
 * DrenarBandejaDeSalida}. Que sean dos tipos y no un parámetro es lo que impide que un caso de uso
 * nuevo se salte la bandeja sin querer: para mandar un correo saltándose la cola hay que pedir este
 * puerto por su nombre, y eso se ve en una revisión.
 *
 * <p>Lo implementa {@code TransporteDeCorreoSpringMail}, que es el adaptador que hasta {@code
 * adr/0045} era el único de correo del sistema.
 */
public interface TransporteDeCorreo {

  /**
   * @throws CorreoNoEnviadoException si el correo no salió. Aquí sí es un fallo técnico de verdad —
   *     el servidor de correo no aceptó el mensaje— y no la clase de cosa que un caso de uso pueda
   *     decidir: la decide la bandeja, reintentando.
   */
  void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml);
}
