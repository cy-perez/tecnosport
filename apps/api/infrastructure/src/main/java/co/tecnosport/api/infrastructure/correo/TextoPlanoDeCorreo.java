package co.tecnosport.api.infrastructure.correo;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La versión en texto plano de un correo, sacada de su HTML (adr/0098).
 *
 * <p>Va junto al HTML en un {@code multipart/alternative}: la leen los clientes que no pintan HTML
 * y los lectores de pantalla configurados así, y un correo solo-HTML suma puntos en los filtros de
 * spam. Se saca del HTML y no se escribe aparte para que las dos versiones no puedan decir cosas
 * distintas.
 *
 * <p>Hecho a mano y no con una librería: el HTML de entrada lo escribimos nosotros, con seis
 * etiquetas y una plantilla conocida, y para eso bastan unas expresiones regulares. Si un día entra
 * HTML ajeno, esto se queda corto y habría que discutir la dependencia.
 */
final class TextoPlanoDeCorreo {

  private static final Pattern ENCABEZADO =
      Pattern.compile("<head[\\s>][\\s\\S]*?</head>", Pattern.CASE_INSENSITIVE);
  private static final Pattern PREVIA = Pattern.compile("<!--previa-->[\\s\\S]*?<!--/previa-->");
  private static final Pattern COMENTARIO = Pattern.compile("<!--[\\s\\S]*?-->");
  private static final Pattern IMAGEN = Pattern.compile("<img[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern ENLACE =
      Pattern.compile(
          "<a\\s[^>]*?href=\"([^\"]*)\"[^>]*>([\\s\\S]*?)</a>", Pattern.CASE_INSENSITIVE);
  private static final Pattern SALTO = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
  private static final Pattern ITEM = Pattern.compile("<li[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern FIN_DE_BLOQUE =
      Pattern.compile("</(p|h\\d|ul|ol|li|tr|table|div)>", Pattern.CASE_INSENSITIVE);
  private static final Pattern ETIQUETA = Pattern.compile("<[^>]+>");
  private static final Pattern NUMERICA = Pattern.compile("&#(\\d+);");

  private TextoPlanoDeCorreo() {}

  static String desde(String html) {
    String texto = ENCABEZADO.matcher(html).replaceAll("");
    texto = PREVIA.matcher(texto).replaceAll("");
    texto = COMENTARIO.matcher(texto).replaceAll("");
    texto = IMAGEN.matcher(texto).replaceAll("");
    // Sin los saltos de línea del fuente: en HTML no significan nada, y aquí sí.
    texto = texto.replaceAll("\\s+", " ");
    texto = ENLACE.matcher(texto).replaceAll(TextoPlanoDeCorreo::enlace);
    texto = SALTO.matcher(texto).replaceAll("\n");
    texto = ITEM.matcher(texto).replaceAll("\n- ");
    texto = FIN_DE_BLOQUE.matcher(texto).replaceAll("\n\n");
    texto = ETIQUETA.matcher(texto).replaceAll("");
    texto = entidades(texto);

    StringBuilder limpio = new StringBuilder();
    for (String linea : texto.split("\n", -1)) {
      limpio.append(linea.strip()).append('\n');
    }
    return limpio.toString().replaceAll("\n{3,}", "\n\n").strip();
  }

  /**
   * Un enlace sin HTML es su texto y su dirección. Cuando son lo mismo —un correo, un teléfono—
   * basta uno; y un enlace sin texto (el del logo) no deja nada.
   */
  private static String enlace(MatchResult resultado) {
    String destino = resultado.group(1);
    String texto = ETIQUETA.matcher(resultado.group(2)).replaceAll("").strip();
    String reemplazo;
    if (texto.isEmpty()) {
      reemplazo = "";
    } else if (destino.isEmpty()
        || destino.equals(texto)
        || destino.equals("mailto:" + texto)
        || destino.equals("tel:" + texto)) {
      reemplazo = texto;
    } else if (destino.replaceFirst("^https?://", "").replaceFirst("/$", "").equals(texto)) {
      // "tecnosport.co" enlazado a su propia raíz: la dirección ya lo dice todo.
      reemplazo = destino;
    } else {
      reemplazo = texto + ": " + destino;
    }
    return Matcher.quoteReplacement(reemplazo);
  }

  private static String entidades(String texto) {
    String resuelto =
        texto
            .replace("&nbsp;", " ")
            .replace("&middot;", "·")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'");
    resuelto =
        NUMERICA
            .matcher(resuelto)
            .replaceAll(
                numero ->
                    Matcher.quoteReplacement(
                        Character.toString(Integer.parseInt(numero.group(1)))));
    // El ampersand al final: si fuera primero, "&amp;lt;" acabaría como "<".
    return resuelto.replace("&amp;", "&");
  }
}
