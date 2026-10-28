package co.tecnosport.api.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

/**
 * La envoltura de marca (adr/0098). Sin Spring ni contenedores: es una función de texto. Lo que no
 * se ve aquí —cómo la pinta Gmail, Outlook o un teléfono— se revisó en Mailpit, y no hay forma
 * honesta de afirmarlo en una prueba.
 */
class PlantillaDeCorreoTest {

  private static final PlantillaDeCorreo PLANTILLA =
      new PlantillaDeCorreo("https://tecnosport.co/");

  private static final String RECUPERACION =
      "<p>Pediste restablecer tu clave.</p><p><a href=\"https://tecnosport.co/es/cuenta/clave?t=abc\">"
          + "Restablecer mi clave</a></p><p>Si no fuiste tú, ignora este correo.</p>";

  @Test
  void envuelveElContenidoEnUnDocumentoCompleto() {
    String correo = PLANTILLA.envolver("Restablece tu clave - Tecno Sport", RECUPERACION);

    assertThat(correo)
        .startsWith("<!doctype html>")
        .contains("Pediste restablecer tu clave.")
        .contains("Si no fuiste tú, ignora este correo.")
        .endsWith("</html>\n");
  }

  @Test
  void elTituloEsElAsuntoSinElSufijoDeMarca() {
    String correo = PLANTILLA.envolver("Despachamos tu pedido TS-123 - Tecno Sport", "<p>x</p>");

    assertThat(correo)
        .contains("<title>Despachamos tu pedido TS-123</title>")
        .contains(">Despachamos tu pedido TS-123</td>")
        .doesNotContain("TS-123 - Tecno Sport");
  }

  @Test
  void elTituloNoPuedeAbrirEtiquetas() {
    String correo = PLANTILLA.envolver("<script>x</script> - Tecno Sport", "<p>x</p>");

    assertThat(correo).doesNotContain("<script>").contains("&lt;script&gt;x&lt;/script&gt;");
  }

  @Test
  void noQuedaNingunMarcadorNiComentarioDeQuienEdita() {
    String correo = PLANTILLA.envolver("Asunto - Tecno Sport", RECUPERACION);

    assertThat(correo).doesNotContain("{{").doesNotContain("}}").doesNotContain("[[");
    assertThat(correo)
        .as("los comentarios del archivo son para quien lo edita")
        .doesNotContain("Por qué está escrita así");
  }

  @Test
  void elPieIdentificaAlVendedorYEnlazaLosLegalesSobreLaUrlPublica() {
    String correo = PLANTILLA.envolver("Asunto - Tecno Sport", "<p>x</p>");

    assertThat(correo)
        .contains("NIT 1054994043-9")
        .contains("Cra. 26C # 38B-31")
        .contains("href=\"https://wa.me/573138816711\"")
        .contains("href=\"mailto:contacto@tecnosport.co\"")
        .contains("href=\"https://www.facebook.com/tecnosport.co\"")
        .contains("href=\"https://www.instagram.com/tecnosport_co/\"")
        // La barra final de la URL pública no se duplica.
        .contains("href=\"https://tecnosport.co/es/legales/terminos\"")
        .contains("href=\"https://tecnosport.co/es/legales/privacidad\"")
        .contains("href=\"https://tecnosport.co/es/ayuda/contacto\"")
        .contains("href=\"https://tecnosport.co/es/checkout/estado\"")
        .contains("src=\"https://tecnosport.co/imagenes/correo/logo-tecnosport.png\"")
        .contains(">tecnosport.co</a>");
  }

  @Test
  void enOtroAmbienteLosEnlacesSonLosDeEseAmbiente() {
    String correo =
        new PlantillaDeCorreo("http://localhost:4200").envolver("Asunto - Tecno Sport", "<p>x</p>");

    assertThat(correo)
        .contains("href=\"http://localhost:4200/es/legales/terminos\"")
        .contains("src=\"http://localhost:4200/imagenes/correo/logo-tecnosport.png\"")
        .contains(">localhost:4200</a>")
        .doesNotContain("https://tecnosport.co/es/");
  }

  @Test
  void unParrafoQueSoloTieneUnEnlaceSePintaComoBoton() {
    String html = PlantillaDeCorreo.estilizar(RECUPERACION);

    assertThat(html)
        .contains("<a style=\"display:inline-block;background-color:#1B1F26;color:#FFFFFF;")
        .contains("href=\"https://tecnosport.co/es/cuenta/clave?t=abc\">Restablecer mi clave</a>");
    assertThat(html)
        .as("un solo style por enlace: el segundo lo ignora el navegador y el botón se pierde")
        .doesNotContainPattern("<a [^>]*style=[^>]*style=");
  }

  @Test
  void unEnlaceDentroDeUnaFraseSigueSiendoEnlace() {
    String html =
        PlantillaDeCorreo.estilizar("<p>Mira <a href=\"https://x.co\">aquí</a> el estado.</p>");

    assertThat(html)
        .contains("<a style=\"color:#14171C;text-decoration:underline;\" href=\"https://x.co\">")
        .doesNotContain("display:inline-block");
  }

  @Test
  void lasEtiquetasDelContenidoLlevanEstilosEnLinea() {
    String html =
        PlantillaDeCorreo.estilizar("<h3>Lo que compraste</h3><ul><li>Uno</li></ul><p>Total</p>");

    assertThat(html)
        .doesNotContain("<h3>")
        .doesNotContain("<ul>")
        .doesNotContain("<li>")
        .doesNotContain("<p>")
        .contains("<h3 style=\"")
        .contains("<li style=\"");
  }

  @Test
  void laVistaPreviaEsElComienzoDelTextoSinEtiquetas() {
    String largo = "<p>" + "palabra ".repeat(40) + "</p>";

    String correo = PLANTILLA.envolver("Asunto - Tecno Sport", largo);

    assertThat(correo).containsPattern("mso-hide:all;\">(palabra ){10}[^<]*…</div>");
  }

  @Test
  void sinUnTextoDeLaPlantillaNoArranca() {
    StaticMessageSource incompleto = new StaticMessageSource();
    incompleto.addMessage("plantilla.idioma", Locale.of("es"), "es");

    assertThatThrownBy(() -> new PlantillaDeCorreo("https://tecnosport.co", incompleto))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("plantilla.");
  }

  @Test
  void unaUrlPublicaSinDominioNoArranca() {
    assertThatThrownBy(() -> new PlantillaDeCorreo("/solo/una/ruta"))
        .isInstanceOf(IllegalStateException.class);
  }
}
