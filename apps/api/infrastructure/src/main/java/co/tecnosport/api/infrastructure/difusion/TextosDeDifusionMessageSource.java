package co.tecnosport.api.infrastructure.difusion;

import co.tecnosport.api.application.difusion.TextoDeDifusion;
import co.tecnosport.api.application.difusion.TextosDeDifusion;
import co.tecnosport.api.domain.compartido.Dinero;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Los textos del pie de las publicaciones, desde {@code difusion_es.properties} y {@code
 * difusion_en.properties}.
 *
 * <p>La misma forma que {@code TextosDeCorreoMessageSource}, con dos diferencias. No escapa nada:
 * el pie es texto plano. Y el idioma es el castellano porque lo que se publica va en castellano —la
 * cuenta es de Medellín y el enlace apunta a {@code /es/}—, no por una razón legal.
 *
 * <p>El constructor resuelve <b>todos</b> los textos en los dos idiomas, así que uno que falte
 * impide levantar el servicio. Descubrirlo al pulsar «Difundir» sería un 500 en el panel.
 */
public final class TextosDeDifusionMessageSource implements TextosDeDifusion {

  static final Locale IDIOMA_QUE_SE_PUBLICA = Locale.of("es");

  static final Locale[] IDIOMAS_QUE_DEBEN_EXISTIR = {Locale.of("es"), Locale.of("en")};

  /** Dos son los que pide el texto que más lleva: el atributo, con su nombre y sus valores. */
  private static final Object[] RELLENO = {"x", "x"};

  private final MessageSource mensajes;

  public TextosDeDifusionMessageSource() {
    this(paquetePropio());
  }

  /** Visible para las pruebas, que necesitan darle una fuente incompleta. */
  TextosDeDifusionMessageSource(MessageSource mensajes) {
    this.mensajes = Objects.requireNonNull(mensajes);
    comprobarQueEstanTodos();
  }

  static MessageSource paquetePropio() {
    ResourceBundleMessageSource fuente = new ResourceBundleMessageSource();
    fuente.setBasename("difusion");
    // Los .properties se leen en ISO-8859-1 si nadie dice otra cosa, y estos llevan tildes y
    // emojis.
    fuente.setDefaultEncoding("UTF-8");
    fuente.setFallbackToSystemLocale(false);
    return fuente;
  }

  @Override
  public String texto(TextoDeDifusion texto, Object... argumentos) {
    Objects.requireNonNull(texto, "El texto no puede ser nulo.");
    return mensajes.getMessage(texto.clave(), argumentos, IDIOMA_QUE_SE_PUBLICA);
  }

  /** Agrupado como se escribe en Colombia y sin decimales: el peso no se fracciona. */
  @Override
  public String dinero(Dinero valor) {
    Objects.requireNonNull(valor, "El importe no puede ser nulo.");
    return NumberFormat.getIntegerInstance(Locale.of("es", "CO")).format(valor.valor());
  }

  /**
   * Con argumentos de relleno y no vacíos: {@code AbstractMessageSource} se salta {@code
   * MessageFormat} cuando no hay argumentos, y entonces un apóstrofe suelto o un marcador de más
   * pasarían sin verse. Formateando de verdad, los dos dejan una llave en el resultado.
   */
  private void comprobarQueEstanTodos() {
    for (Locale idioma : IDIOMAS_QUE_DEBEN_EXISTIR) {
      for (TextoDeDifusion texto : TextoDeDifusion.values()) {
        String resuelto;
        try {
          resuelto = mensajes.getMessage(texto.clave(), RELLENO, idioma);
        } catch (NoSuchMessageException ausente) {
          throw new IllegalStateException(
              "Falta el texto de difusión " + texto.clave() + " en " + idioma.getLanguage() + ".",
              ausente);
        }
        // Sin argumentos, Spring no pasa por MessageFormat y un '' se publicaría doble. Los que
        // no llevan marcadores se usan así, de modo que no pueden llevar apóstrofes dobles.
        String sinArgumentos = mensajes.getMessage(texto.clave(), null, idioma);
        if (!sinArgumentos.contains("{") && sinArgumentos.contains("''")) {
          throw new IllegalStateException(
              "El texto de difusión "
                  + texto.clave()
                  + " en "
                  + idioma.getLanguage()
                  + " no lleva marcadores y se publicaría con el apóstrofe doble: "
                  + sinArgumentos);
        }
        if (resuelto.indexOf('{') >= 0) {
          throw new IllegalStateException(
              "El texto de difusión "
                  + texto.clave()
                  + " en "
                  + idioma.getLanguage()
                  + " dejó un marcador sin rellenar: "
                  + resuelto);
        }
      }
    }
  }
}
