package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.util.ArrayList;
import java.util.List;

/** Anota a quién se le escribió: el seguimiento por número ya no responde nada más que eso. */
final class EnviadorDeCorreoDobleDePrueba implements EnviadorDeCorreo {

  private final List<String> destinatarios = new ArrayList<>();

  @Override
  public void enviar(
      CorreoElectronico destinatario,
      String nombreDelDestinatario,
      String asunto,
      String cuerpoHtml) {
    destinatarios.add(destinatario.valor());
  }

  List<String> destinatarios() {
    return List.copyOf(destinatarios);
  }

  void limpiar() {
    destinatarios.clear();
  }
}
