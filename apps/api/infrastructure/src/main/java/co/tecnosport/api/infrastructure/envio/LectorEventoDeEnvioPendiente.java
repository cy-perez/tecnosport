package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.AplicarEventoDeEnvioComando;
import co.tecnosport.api.application.envio.LectorEventoDeEnvio;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * No sabe leer ningún cuerpo todavía, y es la respuesta correcta mientras nadie haya visto uno.
 *
 * <p>Los doce estados que usa Skydropx sí están confirmados (adr/0022) y ya viven en {@code
 * EstadoEnvio}. Lo que no se ha visto nunca es un evento real: cómo se llaman los campos, dónde
 * viaja la guía, si el estado llega como cadena o anidado, y si hay un identificador de evento
 * propio o hay que derivarlo del hash de la firma. Emitir una guía es lo que haría falta para
 * verlo, y la cuenta de sandbox no tiene créditos.
 *
 * <p>Un lector escrito de memoria se probaría contra un JSON inventado por la misma mano que lo
 * escribió, pasaría, y fallaría en el primer despacho real. Ya pasó en este proyecto con un vector
 * de firma "de ejemplo" que resultó fabricado.
 */
@Component
final class LectorEventoDeEnvioPendiente implements LectorEventoDeEnvio {

  @Override
  public Optional<AplicarEventoDeEnvioComando> leer(String cuerpoCrudo) {
    return Optional.empty();
  }
}
