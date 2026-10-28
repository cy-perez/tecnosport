package co.tecnosport.api.infrastructure.correo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

/**
 * La envoltura de marca de todos los correos del sistema (adr/0098): cabecera con el logo, el
 * título, el contenido, el bloque de ayuda y el pie con los datos del vendedor y los legales.
 *
 * <p><b>Ningún caso de uso sabe que existe.</b> Siguen entregando el mismo fragmento —{@code
 * <p>…</p>} resuelto desde {@code correos_*.properties}— y {@link EnviadorDeCorreoBandejaDeSalida}
 * lo envuelve al encolarlo. Al encolar y no al mandar a propósito: la fila de la bandeja guarda así
 * el correo tal como salió, que es lo que sirve de constancia del comprobante de compra.
 *
 * <p>Del fragmento hace tres cosas, todas sobre las seis etiquetas que los textos usan:
 *
 * <ul>
 *   <li>Le pone estilos en línea, porque Gmail descarta buena parte del {@code <style>}.
 *   <li>Pinta como botón todo párrafo que solo contiene un enlace —«Ver el estado de mi pedido»,
 *       «Verificar mi correo»—, que es la acción del correo. Por forma y no por una marca en el
 *       texto: los ocho que hay ya la tienen, y uno nuevo escrito igual sale igual.
 *   <li>Saca el título del asunto, sin el « - Tecno Sport» final, y la vista previa del primer
 *       párrafo. Así no hicieron falta treinta llaves nuevas que repitieran el asunto.
 * </ul>
 *
 * <p>El esqueleto se resuelve <b>una vez</b>, al construirse: los textos del pie y las URL no
 * cambian entre un correo y otro. Si falta una llave en cualquiera de los dos idiomas, o queda un
 * marcador sin rellenar, el servicio no arranca — mismo criterio que {@link
 * TextosDeCorreoMessageSource}.
 */
public class PlantillaDeCorreo {

  /** Donde lo deja {@code tools/copiar-marca.mjs}, relativo a la raíz del sitio. */
  static final String RUTA_LOGO = "/imagenes/correo/logo-tecnosport.png";

  private static final String RECURSO = "correo/plantilla.html";

  private static final List<String> TEXTOS =
      List.of(
          "plantilla.idioma",
          "plantilla.logo_alt",
          "plantilla.dudas.titulo",
          "plantilla.dudas.texto",
          "plantilla.dudas.whatsapp_etiqueta",
          "plantilla.dudas.whatsapp",
          "plantilla.dudas.whatsapp_url",
          "plantilla.dudas.correo_etiqueta",
          "plantilla.dudas.correo",
          "plantilla.redes.titulo",
          "plantilla.redes.facebook",
          "plantilla.redes.facebook_url",
          "plantilla.redes.instagram",
          "plantilla.redes.instagram_url",
          "plantilla.enlace.terminos",
          "plantilla.enlace.privacidad",
          "plantilla.enlace.contacto",
          "plantilla.enlace.estado_pedido",
          "plantilla.ruta.terminos",
          "plantilla.ruta.privacidad",
          "plantilla.ruta.contacto",
          "plantilla.ruta.estado_pedido",
          "plantilla.vendedor",
          "plantilla.aviso");

  /** El sufijo de marca de todos los asuntos: en el título sobra, la cabecera ya lo dice. */
  private static final Pattern SUFIJO_DEL_ASUNTO = Pattern.compile("\\s+-\\s+Tecno Sport\\s*$");

  /** Los comentarios del archivo son para quien lo edita, no para quien lo recibe. */
  private static final Pattern COMENTARIO = Pattern.compile("<!--(?!/?previa-->)[\\s\\S]*?-->");

  private static final Pattern MARCADOR = Pattern.compile("\\{\\{[^}]*}}");

  private static final Pattern PARRAFO_DE_ACCION =
      Pattern.compile("<p>\\s*<a href=\"([^\"]*)\">([^<]*)</a>\\s*</p>");

  private static final int LARGO_DE_LA_PREVIA = 140;

  private static final String ESTILO_PARRAFO = "margin:0 0 16px;";
  private static final String ESTILO_SUBTITULO =
      "margin:24px 0 12px;font-family:'Archivo','Arial Narrow','Helvetica Neue',Arial,sans-serif;"
          + "font-size:20px;line-height:1.2;font-weight:700;color:#14171C;";
  private static final String ESTILO_LISTA = "margin:0 0 16px;padding:0 0 0 20px;";
  private static final String ESTILO_ITEM = "margin:0 0 8px;";
  private static final String ESTILO_ENLACE = "color:#14171C;text-decoration:underline;";
  private static final String ESTILO_PARRAFO_DE_ACCION = "margin:24px 0;";

  /**
   * El botón principal del sitio: grafito con texto blanco. El ámbar ya está en la franja de la
   * cabecera, y el manual de marca lo reserva a un solo elemento por pantalla.
   */
  private static final String ESTILO_BOTON =
      "display:inline-block;background-color:#1B1F26;color:#FFFFFF;font-weight:700;"
          + "font-size:16px;line-height:1.2;text-decoration:none;padding:14px 28px;"
          + "border:1px solid #1B1F26;border-radius:4px;";

  private final String esqueleto;

  public PlantillaDeCorreo(String urlPublica) {
    this(urlPublica, TextosDeCorreoMessageSource.paquetePropio());
  }

  PlantillaDeCorreo(String urlPublica, MessageSource mensajes) {
    Objects.requireNonNull(urlPublica, "La URL pública no puede ser nula.");
    Objects.requireNonNull(mensajes);
    String raiz = urlPublica.strip().replaceAll("/+$", "");
    String sitio = URI.create(raiz).getAuthority();
    if (sitio == null || sitio.isBlank()) {
      throw new IllegalStateException(
          "La URL pública de los correos no tiene dominio: " + urlPublica);
    }
    for (Locale idioma : TextosDeCorreoMessageSource.IDIOMAS_QUE_DEBEN_EXISTIR) {
      for (String clave : TEXTOS) {
        texto(mensajes, clave, idioma);
      }
    }

    String plantilla = COMENTARIO.matcher(leerRecurso()).replaceAll("");
    for (String clave : TEXTOS) {
      plantilla =
          plantilla.replace(
              "{{" + clave + "}}",
              texto(mensajes, clave, TextosDeCorreoMessageSource.IDIOMA_QUE_RIGE));
    }
    plantilla =
        plantilla
            .replace("{{url_sitio}}", raiz)
            .replace("{{sitio}}", sitio)
            .replace("{{logo}}", raiz + RUTA_LOGO);

    Matcher sobrante =
        MARCADOR.matcher(
            plantilla
                .replace("{{titulo}}", "")
                .replace("{{previa}}", "")
                .replace("{{contenido}}", ""));
    if (sobrante.find()) {
      throw new IllegalStateException(
          "La plantilla de correo dejó un marcador sin rellenar: " + sobrante.group());
    }
    this.esqueleto = plantilla;
  }

  /**
   * El correo completo. El asunto y el cuerpo llegan como los arma {@code TextosDeCorreo}: con los
   * datos ya escapados para HTML. Del asunto se escapan además los ángulos, porque va dentro de una
   * etiqueta y nadie lo escribió pensando en eso.
   */
  public String envolver(String asunto, String cuerpoHtml) {
    Objects.requireNonNull(asunto, "El asunto no puede ser nulo.");
    Objects.requireNonNull(cuerpoHtml, "El cuerpo no puede ser nulo.");
    String titulo =
        SUFIJO_DEL_ASUNTO
            .matcher(asunto)
            .replaceAll("")
            .strip()
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    // Un solo reemplazo por marcador y el contenido al final: si el cuerpo trajera "{{titulo}}"
    // escrito por alguien en el panel, ya no queda nada que lo interprete.
    return esqueleto
        .replace("{{titulo}}", titulo)
        .replace("{{previa}}", previa(cuerpoHtml))
        .replace("{{contenido}}", estilizar(cuerpoHtml));
  }

  static String estilizar(String fragmento) {
    String conBotones =
        PARRAFO_DE_ACCION
            .matcher(fragmento)
            .replaceAll(
                resultado ->
                    Matcher.quoteReplacement(
                        "<p style=\""
                            + ESTILO_PARRAFO_DE_ACCION
                            // El estilo antes del href, y no después: así el reemplazo de
                            // "<a href=" de más abajo no le pone un segundo style, que el
                            // navegador ignoraría y dejaría el botón como un enlace.
                            + "\"><a style=\""
                            + ESTILO_BOTON
                            + "\" href=\""
                            + resultado.group(1)
                            + "\">"
                            + resultado.group(2)
                            + "</a></p>"));
    return conBotones
        .replace("<p>", "<p style=\"" + ESTILO_PARRAFO + "\">")
        .replace("<h3>", "<h3 style=\"" + ESTILO_SUBTITULO + "\">")
        .replace("<ul>", "<ul style=\"" + ESTILO_LISTA + "\">")
        .replace("<li>", "<li style=\"" + ESTILO_ITEM + "\">")
        .replace("<a href=", "<a style=\"" + ESTILO_ENLACE + "\" href=");
  }

  /** Lo que el buzón enseña junto al asunto antes de abrir el correo: el comienzo del texto. */
  private static String previa(String cuerpoHtml) {
    String texto = TextoPlanoDeCorreo.desde(cuerpoHtml).replaceAll("\\s+", " ").strip();
    if (texto.length() > LARGO_DE_LA_PREVIA) {
      texto = texto.substring(0, LARGO_DE_LA_PREVIA).strip() + "…";
    }
    return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String texto(MessageSource mensajes, String clave, Locale idioma) {
    try {
      // Sin argumentos: así MessageFormat no interviene y el texto llega tal como está escrito.
      return mensajes.getMessage(clave, null, idioma);
    } catch (NoSuchMessageException ausente) {
      throw new IllegalStateException(
          "Falta el texto de la plantilla de correo "
              + clave
              + " en "
              + idioma.getLanguage()
              + ": sin él no sale ningún correo.",
          ausente);
    }
  }

  private static String leerRecurso() {
    try (InputStream entrada =
        PlantillaDeCorreo.class.getClassLoader().getResourceAsStream(RECURSO)) {
      if (entrada == null) {
        throw new IllegalStateException("No está la plantilla de correo en " + RECURSO + ".");
      }
      return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ilegible) {
      throw new UncheckedIOException(ilegible);
    }
  }
}
