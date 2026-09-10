package co.tecnosport.api.infrastructure.correo;

import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Los textos de los correos, desde {@code correos_es.properties} y {@code correos_en.properties}.
 *
 * <p><b>El idioma es el castellano y no una preferencia del comprador</b>, porque hoy el sistema no
 * sabe en qué idioma navega: ni {@code Pedido} ni {@code Usuario} guardan idioma. Que sea el
 * castellano no es un descuido, es la única respuesta defendible: la <b>Ley 1480 de 2011</b> exige
 * la información mínima en castellano (art. 23) y ese idioma en los contratos (art. 37.1), y este
 * proyecto ya lo dejó escrito en la nota de cortesía de los documentos legales — la versión
 * española es la que rige. El día que el pedido guarde el idioma del comprador, este adaptador
 * recibe un parámetro y el paquete {@code en} ya está escrito.
 *
 * <p>Y no es un paquete muerto mientras tanto: {@link #afterPropertiesSet()} resuelve <b>todos</b>
 * los textos en los dos idiomas al arrancar, así que un texto que falte en cualquiera de los dos
 * impide levantar el servicio. Es a propósito: varios de estos correos son la constancia de que se
 * devolvió un dinero o de que una PQR quedó radicada, y descubrir que falta el texto en el momento
 * de mandarlo significa un 500 con un comprador esperando.
 */
@Component
public class TextosDeCorreoMessageSource implements TextosDeCorreo, InitializingBean {

  /** Ver el javadoc de la clase: la versión en castellano es la que rige. */
  private static final Locale IDIOMA_QUE_RIGE = Locale.of("es");

  private static final Locale[] IDIOMAS_QUE_DEBEN_EXISTIR = {Locale.of("es"), Locale.of("en")};

  private final MessageSource mensajes;

  public TextosDeCorreoMessageSource() {
    this(paquetePropio());
  }

  /**
   * Visible solo para las pruebas, que necesitan poder darle una fuente incompleta y comprobar que
   * entonces el servicio no levanta.
   */
  TextosDeCorreoMessageSource(MessageSource mensajes) {
    this.mensajes = Objects.requireNonNull(mensajes);
  }

  /**
   * El paquete se construye aquí y no se pide al contexto por {@code spring.messages}, y no es un
   * detalle: los contextos de prueba de esta capa no cargan el {@code application.yml} de {@code
   * bootstrap}, así que el {@code MessageSource} inyectado llegaba sin ningún paquete detrás y la
   * comprobación de arranque tumbaba <b>todas</b> las pruebas de infraestructura. Lo descubrió el
   * build, y enseña algo que vale más que el arreglo: un componente que se niega a arrancar sin su
   * configuración no puede depender de que otra capa se acuerde de configurarlo.
   *
   * <p>De paso deja de compartir {@code spring.messages} con cualquier otro uso futuro de mensajes,
   * que habría podido cambiarle el {@code basename} por debajo.
   */
  private static MessageSource paquetePropio() {
    ResourceBundleMessageSource fuente = new ResourceBundleMessageSource();
    fuente.setBasename("correos");
    // Explícito y no por omisión: los .properties se leen en ISO-8859-1 si nadie dice otra cosa, y
    // estos textos citan artículos de la Ley 1480 con tildes.
    fuente.setDefaultEncoding("UTF-8");
    // Sin caer al idioma del sistema operativo: el idioma lo decide esta clase, con una razón legal
    // escrita, y no la máquina donde corra el contenedor.
    fuente.setFallbackToSystemLocale(false);
    return fuente;
  }

  @Override
  public String texto(TextoDeCorreo texto, Object... argumentos) {
    Objects.requireNonNull(texto, "El texto no puede ser nulo.");
    return mensajes.getMessage(texto.clave(), escapados(argumentos), IDIOMA_QUE_RIGE);
  }

  @Override
  public void afterPropertiesSet() {
    for (Locale idioma : IDIOMAS_QUE_DEBEN_EXISTIR) {
      for (TextoDeCorreo texto : TextoDeCorreo.values()) {
        try {
          mensajes.getMessage(texto.clave(), null, idioma);
        } catch (NoSuchMessageException ausente) {
          throw new IllegalStateException(
              "Falta el texto de correo "
                  + texto.clave()
                  + " en "
                  + idioma.getLanguage()
                  + ": un correo sin texto es un 500 con alguien esperándolo.",
              ausente);
        }
      }
    }
  }

  private static Object[] escapados(Object... argumentos) {
    if (argumentos == null) {
      return new Object[0];
    }
    return Arrays.stream(argumentos).map(TextosDeCorreoMessageSource::escapar).toArray();
  }

  /**
   * Los cinco caracteres que cambian el significado de un HTML. A mano y no con {@code
   * HtmlUtils.htmlEscape}: eso vive en {@code spring-web}, que esta capa no tiene por qué arrastrar
   * para cinco reemplazos. El orden importa — el ampersand va primero, o se escaparían dos veces
   * los que introducen los demás.
   */
  private static String escapar(Object argumento) {
    return String.valueOf(argumento)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
