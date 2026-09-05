package co.tecnosport.api.presentation.usuario;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;

final class EnviadorDeCorreoDobleDePrueba implements EnviadorDeCorreo {

  @Override
  public void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {
    // No hace nada: estas pruebas no verifican el envío de correo, solo el HTTP.
  }
}
