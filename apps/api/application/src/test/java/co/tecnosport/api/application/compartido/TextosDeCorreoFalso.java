package co.tecnosport.api.application.compartido;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Doble de los textos de correo, escrito a mano y sin Mockito (docs/06-testing.md). Público y en
 * {@code compartido} por lo mismo que {@link RelojFalso}: lo necesitan seis paquetes de prueba.
 *
 * <p>Devuelve la llave y sus argumentos en vez de una frase, y eso es el punto: lo que a la capa de
 * aplicación le toca demostrar es que manda <b>el texto que corresponde con los datos que
 * corresponden</b>. Que la frase diga lo que la ley exige —"artículo 47", "quince (15) días
 * calendario"— se prueba donde vive el texto, en {@code TextosDeCorreoMessageSourceTest} de
 * infraestructura, y no aquí: una prueba de aplicación que afirme sobre la prosa vuelve a atar el
 * caso de uso al idioma del que se acaba de soltar.
 */
public final class TextosDeCorreoFalso implements TextosDeCorreo {

  @Override
  public String texto(TextoDeCorreo texto, Object... argumentos) {
    if (argumentos == null || argumentos.length == 0) {
      return "[" + texto.clave() + "]";
    }
    return "["
        + texto.clave()
        + "|"
        + Arrays.stream(argumentos).map(String::valueOf).collect(Collectors.joining("|"))
        + "]";
  }
}
