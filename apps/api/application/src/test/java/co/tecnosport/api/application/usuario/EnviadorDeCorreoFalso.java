package co.tecnosport.api.application.usuario;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.util.ArrayList;
import java.util.List;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md.
 *
 * <p><b>Lanza {@link CorreoNoEnviadoException} y no cualquier excepción</b>, que es la lección de
 * {@code adr/0044}: un doble que habla un idioma distinto del adaptador de producción prueba un
 * escenario que no existe. Aquí importa el tipo exacto porque los dos casos de uso de cuenta
 * atrapan ese tipo y solo ese.
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
      throw new CorreoNoEnviadoException(
          new IllegalStateException("el servidor de correo no respondió"));
    }
    enviados.add(new CorreoEnviado(destinatario, asunto, cuerpoHtml));
  }

  List<CorreoEnviado> enviados() {
    return List.copyOf(enviados);
  }
}
