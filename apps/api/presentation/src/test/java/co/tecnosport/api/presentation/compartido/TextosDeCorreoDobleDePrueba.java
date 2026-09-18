package co.tecnosport.api.presentation.compartido;

import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.Dinero;

/**
 * Doble de los textos de correo para los contextos de prueba de esta capa: devuelve la llave, no la
 * frase. Lo que aquí se comprueba es el contrato HTTP, no la prosa — eso se prueba donde vive el
 * texto, en {@code TextosDeCorreoMessageSourceTest}.
 *
 * <p>Una clase y no ocho lambdas, que es como estaba: {@code TextosDeCorreo} dejó de ser una
 * interfaz funcional el 18 de septiembre de 2026, cuando el formato del dinero se movió al puerto
 * —el agrupamiento de miles es parte del idioma y el caso de uso no sabe en cuál se va a pintar—.
 * Con ocho lambdas idénticas, cada método nuevo del puerto costaba ocho ediciones.
 */
public final class TextosDeCorreoDobleDePrueba implements TextosDeCorreo {

  @Override
  public String texto(TextoDeCorreo texto, Object... argumentos) {
    return texto.clave();
  }

  @Override
  public String dinero(Dinero valor) {
    return valor.valor().toPlainString();
  }
}
