package co.tecnosport.api.infrastructure.correo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** La versión en texto plano que viaja junto al HTML (adr/0098). */
class TextoPlanoDeCorreoTest {

  @Test
  void losParrafosQuedanSeparadosPorUnaLineaEnBlanco() {
    assertThat(TextoPlanoDeCorreo.desde("<p>Uno.</p><p>Dos.</p>")).isEqualTo("Uno.\n\nDos.");
  }

  @Test
  void unEnlaceConservaSuDireccion() {
    assertThat(
            TextoPlanoDeCorreo.desde(
                "<p><a style=\"x\" href=\"https://tecnosport.co/es/checkout/estado\">"
                    + "Ver el estado de mi pedido</a></p>"))
        .isEqualTo("Ver el estado de mi pedido: https://tecnosport.co/es/checkout/estado");
  }

  @Test
  void unCorreoEnlazadoNoSeRepite() {
    assertThat(
            TextoPlanoDeCorreo.desde(
                "<a href=\"mailto:contacto@tecnosport.co\">contacto@tecnosport.co</a>"))
        .isEqualTo("contacto@tecnosport.co");
  }

  @Test
  void elDominioEnlazadoASuRaizNoSeRepite() {
    assertThat(TextoPlanoDeCorreo.desde("<a href=\"https://tecnosport.co\">tecnosport.co</a>"))
        .isEqualTo("https://tecnosport.co");
  }

  @Test
  void lasListasYLosSaltosSeConservan() {
    assertThat(TextoPlanoDeCorreo.desde("<p>A<br>B</p><ul><li>Uno</li><li>Dos</li></ul>"))
        .isEqualTo("A\nB\n\n- Uno\n\n- Dos");
  }

  @Test
  void lasEntidadesSeDecodificanSinDecodificarDosVeces() {
    assertThat(TextoPlanoDeCorreo.desde("<p>A &amp; B &middot; &lt;C&gt; &amp;lt; &#8364;</p>"))
        .isEqualTo("A & B · <C> &lt; €");
  }

  @Test
  void elCorreoCompletoNoArrastraNiEstilosNiVistaPreviaNiElLogo() {
    String correo =
        new PlantillaDeCorreo("https://tecnosport.co")
            .envolver(
                "Despachamos tu pedido TS-1 - Tecno Sport",
                "<p>Tu pedido ya salió.</p><p><a href=\"https://tecnosport.co/x\">Ver</a></p>");

    String texto = TextoPlanoDeCorreo.desde(correo);

    assertThat(texto)
        .startsWith(
            "Despachamos tu pedido TS-1\n\nTu pedido ya salió.\n\nVer: https://tecnosport.co/x")
        .contains("NIT 1054994043-9")
        .contains("Términos y condiciones: https://tecnosport.co/es/legales/terminos")
        .doesNotContain("<")
        .doesNotContain("@media")
        .doesNotContain("logo-tecnosport.png");
    assertThat(texto.indexOf("Tu pedido ya salió."))
        .as("la vista previa oculta no se repite al comienzo del texto")
        .isEqualTo(texto.lastIndexOf("Tu pedido ya salió."));
  }
}
