package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.util.ArrayList;
import java.util.List;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class EnviadorDeCorreoFalso implements EnviadorDeCorreo {

  record CorreoEnviado(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {}

  private final List<CorreoEnviado> enviados = new ArrayList<>();

  @Override
  public void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {
    enviados.add(new CorreoEnviado(destinatario, asunto, cuerpoHtml));
  }

  List<CorreoEnviado> enviados() {
    return List.copyOf(enviados);
  }
}
