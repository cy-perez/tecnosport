package co.tecnosport.api.presentation.retracto;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;

/** No manda nada: en este slice solo importa que el controlador llame al caso de uso. */
final class EnviadorDeCorreoDobleDePrueba implements EnviadorDeCorreo {

  @Override
  public void enviar(CorreoElectronico destinatario, String asunto, String cuerpoHtml) {}
}
