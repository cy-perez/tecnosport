package co.tecnosport.api.infrastructure.correo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.TextoDeCorreo;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;

/**
 * Aquí se prueba <b>lo que dicen</b> los correos, porque aquí es donde viven los textos. Las
 * pruebas de aplicación solo comprueban que se manda la llave correcta con los datos correctos: si
 * afirmaran sobre la prosa, volverían a atar el caso de uso al idioma del que se lo acabó de soltar
 * — y de hecho una lo hacía, contra una versión sin tildes de "articulo 47".
 */
class TextosDeCorreoMessageSourceTest {

  /** El camino de producción: el adaptador se trae su propio paquete de mensajes. */
  private static TextosDeCorreoMessageSource textos() {
    return new TextosDeCorreoMessageSource();
  }

  /**
   * Los veinticuatro textos, en los dos idiomas. Es la prueba que hace que {@code
   * correos_en.properties} no sea un archivo decorativo mientras nadie pueda pedir inglés.
   */
  @Test
  void todosLosTextosExistenEnLosDosIdiomas() {
    assertDoesNotThrow(() -> textos().afterPropertiesSet());
  }

  @Test
  void ningunTextoQuedaVacio() {
    TextosDeCorreoMessageSource textos = textos();
    for (TextoDeCorreo texto : TextoDeCorreo.values()) {
      assertFalse(textos.texto(texto).isBlank(), texto.clave() + " está vacío");
    }
  }

  /**
   * Los dos datos que el acuse de retracto tiene que llevar, y con sus tildes: sin ellos el
   * comprador no sabe qué hacer con el producto, y son promesas legales — el artículo 47 de la Ley
   * 1480 de 2011 pone el flete de vuelta a cargo del comprador, y la Ley 2439 de 2024 dejó el
   * reintegro en quince días calendario.
   */
  @Test
  void elAcuseDeRetractoCitaLaNormaConSusTildes() {
    String cuerpo = textos().texto(TextoDeCorreo.RETRACTO_ACUSE_CUERPO, "TS-2026-000001");

    assertTrue(cuerpo.contains("artículo 47"), cuerpo);
    assertTrue(cuerpo.contains("quince (15) días calendario"), cuerpo);
    assertTrue(cuerpo.contains("TS-2026-000001"), cuerpo);
  }

  /**
   * El aviso del plazo de entrega vencido nombra la norma que le da la salida a quien compró. Sin
   * ella el correo diría "se nos pasó el plazo" y no que puede terminar el contrato, que es lo que
   * los términos publicados prometen.
   */
  @Test
  void elAvisoDePlazoVencidoCitaLaNormaYElTermino() {
    String cuerpo = textos().texto(TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_CUERPO);

    assertTrue(cuerpo.contains("artículo 18"), cuerpo);
    assertTrue(cuerpo.contains("treinta (30) días calendario"), cuerpo);
    assertTrue(cuerpo.contains("terminar el contrato"), cuerpo);
  }

  /**
   * Un contraentrega sin entregar no ha cobrado nada, así que su mitad del correo no puede prometer
   * una devolución. Las dos mitades existen por lo mismo que en la cancelación.
   */
  @Test
  void laMitadSinCobroNoPrometeNingunaDevolucion() {
    String sinCobro = textos().texto(TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_SIN_COBRO);

    assertTrue(sinCobro.contains("no hay dinero que devolverte"), sinCobro);
    assertTrue(
        textos().texto(TextoDeCorreo.PEDIDO_PLAZO_VENCIDO_CON_DINERO).contains("te devolvemos"));
  }

  /** Las tildes de verdad, en el disco y a través del codificado. Es el defecto que había. */
  @Test
  void losTextosLlevanTildes() {
    assertTrue(
        textos()
            .texto(TextoDeCorreo.ATENCION_ACUSE_CUERPO, "TS-PQR-2026-000001", "algo")
            .contains("número"));
  }

  /**
   * El asunto de una PQR y el motivo de una prórroga los escribe una persona en el panel, y el
   * cuerpo es HTML. Con la concatenación anterior, un asunto con {@code <} rompía el correo del
   * comprador; escapando en el puerto, no hay ningún sitio donde olvidarlo.
   */
  @Test
  void losArgumentosSeEscapanParaNoRomperElHtml() {
    String cuerpo =
        textos()
            .texto(
                TextoDeCorreo.ATENCION_ACUSE_CUERPO,
                "TS-PQR-2026-000001",
                "<script>alert(1)</script>");

    assertFalse(cuerpo.contains("<script>"), cuerpo);
    assertTrue(cuerpo.contains("&lt;script&gt;"), cuerpo);
    // Y las etiquetas de la propia plantilla siguen siendo etiquetas.
    assertTrue(cuerpo.contains("<strong>"), cuerpo);
  }

  /**
   * Un texto que falte impide arrancar. Es a propósito: varios de estos correos son la constancia
   * de que se devolvió un dinero, y descubrir que falta la frase en el momento de mandarla
   * significa un 500 con alguien esperándolo.
   */
  @Test
  void sinUnTextoElServicioNoLevanta() {
    TextosDeCorreoMessageSource incompleto =
        new TextosDeCorreoMessageSource(new StaticMessageSource());

    IllegalStateException error =
        assertThrows(IllegalStateException.class, incompleto::afterPropertiesSet);

    assertTrue(error.getMessage().contains("Falta el texto de correo"), error.getMessage());
  }

  /** Y el inglés existe de verdad, no es el castellano repetido. */
  @Test
  void elPaqueteInglesEstaTraducido() {
    ResourceBundleMessageSource fuente = new ResourceBundleMessageSource();
    fuente.setBasename("correos");
    fuente.setDefaultEncoding("UTF-8");
    fuente.setFallbackToSystemLocale(false);

    String es =
        fuente.getMessage(TextoDeCorreo.USUARIO_VERIFICACION_ASUNTO.clave(), null, Locale.of("es"));
    String en =
        fuente.getMessage(TextoDeCorreo.USUARIO_VERIFICACION_ASUNTO.clave(), null, Locale.of("en"));

    assertTrue(es.contains("Verifica tu correo"), es);
    assertTrue(en.contains("Verify your email"), en);
  }
}
