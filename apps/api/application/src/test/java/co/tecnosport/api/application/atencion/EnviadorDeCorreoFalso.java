package co.tecnosport.api.application.atencion;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.util.ArrayList;
import java.util.List;

/**
 * Doble escrito a mano, sin Mockito (docs/06-testing.md). El de {@code application.usuario} no se
 * puede reutilizar desde aquí: es de visibilidad de paquete, a propósito.
 *
 * <p>{@code falla} existe para probar lo que de verdad importa del acuse — que un correo caído no
 * deje una solicitud guardada a medias.
 */
final class EnviadorDeCorreoFalso implements EnviadorDeCorreo {

  record CorreoEnviado(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {}

  private final List<CorreoEnviado> enviados = new ArrayList<>();
  private boolean falla;

  void hazQueFalle() {
    this.falla = true;
  }

  @Override
  public void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {
    if (falla) {
      throw new IllegalStateException("el servidor de correo no respondió");
    }
    enviados.add(new CorreoEnviado(destinatario, asunto, cuerpoHtml));
  }

  List<CorreoEnviado> enviados() {
    return List.copyOf(enviados);
  }
}
