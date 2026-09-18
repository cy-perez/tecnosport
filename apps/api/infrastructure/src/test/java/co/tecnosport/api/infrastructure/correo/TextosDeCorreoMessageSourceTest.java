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
   * Los veintiséis textos, en los dos idiomas. Es la prueba que hace que {@code
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

  /**
   * El correo del despacho, que es lo que hace verdadero el párrafo del numeral 8 de los términos
   * ("te enviaremos la empresa de transporte y el número de guía"). Si esta prueba cae, el texto
   * publicado promete algo que el correo no dice, y la publicidad obliga.
   */
  @Test
  void elCorreoDeDespachoNombraLaTransportadoraLaGuiaYElEnlace() {
    String cuerpo =
        textos()
            .texto(
                TextoDeCorreo.PEDIDO_DESPACHO_CUERPO,
                "TS-2026-000001",
                "Servientrega",
                "SE123456",
                "https://tecnosport.co/es/checkout/estado?pedidoId=1&correo=a%40b.co");

    assertTrue(cuerpo.contains("Servientrega"), cuerpo);
    assertTrue(cuerpo.contains("SE123456"), cuerpo);
    assertTrue(cuerpo.contains("número de guía"), cuerpo);
    assertTrue(cuerpo.contains("https://tecnosport.co/es/checkout/estado"), cuerpo);
  }

  /**
   * Lo que el correo <b>no</b> puede decir. Este sistema no consume eventos de la transportadora ni
   * enlaza a su rastreo: prometerlo en el correo sería la misma promesa vacía que tuvo retenido el
   * párrafo del numeral 8 durante toda la fase, con otro signo.
   */
  @Test
  void elCorreoDeDespachoNoPrometeRastreoDeEventos() {
    String cuerpo =
        textos()
            .texto(
                TextoDeCorreo.PEDIDO_DESPACHO_CUERPO,
                "TS-2026-000001",
                "Servientrega",
                "SE123456",
                "https://tecnosport.co/es/checkout/estado");

    assertFalse(cuerpo.contains("rastre"), cuerpo);
    assertFalse(cuerpo.contains("evento"), cuerpo);
  }

  /**
   * El enlace del despacho, renderizado de verdad y con su cadena de consulta. Importa porque el
   * puerto <b>escapa los argumentos para HTML por contrato</b>, así que el {@code &} que separa
   * {@code pedidoId} de {@code correo} sale como {@code &amp;} dentro del {@code href}. Eso es
   * correcto en HTML y es lo que se quiere — pero ninguna prueba lo miraba: la de aplicación usa un
   * doble que no escapa, y la de arriba solo afirma sobre la raíz de la URL, sin parámetros.
   *
   * <p>Si esto se rompiera, el comprador no podría abrir su pedido desde el correo, que es
   * exactamente lo que el numeral 8 de los términos acaba de prometer.
   */
  @Test
  void elEnlaceDelDespachoSobreviveAlEscapadoDeHtml() {
    String enlace =
        "https://tecnosport.co/es/checkout/estado?pedidoId=01a0&correo=ana%40ejemplo.co";

    String cuerpo =
        textos()
            .texto(
                TextoDeCorreo.PEDIDO_DESPACHO_CUERPO,
                "TS-2026-000001",
                "Servientrega",
                "SE123456",
                enlace);

    // El ampersand va escapado, que es lo correcto dentro de un atributo HTML...
    assertTrue(cuerpo.contains("pedidoId=01a0&amp;correo=ana%40ejemplo.co"), cuerpo);
    // ...y no doblemente escapado, que sí rompería el enlace.
    assertFalse(cuerpo.contains("&amp;amp;"), cuerpo);
    // Y la arroba codificada sigue codificada: si se escapara el %, el correo llegaría mal.
    assertTrue(cuerpo.contains("%40"), cuerpo);
  }

  /** Y el del despacho también está traducido, no es el castellano repetido. */
  @Test
  void elCorreoDeDespachoExisteEnIngles() {
    ResourceBundleMessageSource fuente = new ResourceBundleMessageSource();
    fuente.setBasename("correos");
    fuente.setDefaultEncoding("UTF-8");
    fuente.setFallbackToSystemLocale(false);

    Object[] datos = {"TS-2026-000001", "Servientrega", "SE123456", "https://tecnosport.co/x"};
    String en =
        fuente.getMessage(TextoDeCorreo.PEDIDO_DESPACHO_CUERPO.clave(), datos, Locale.of("en"));

    assertTrue(en.contains("tracking number"), en);
    assertTrue(en.contains("Servientrega"), en);
  }

  /**
   * Lo que el comprobante de compra tiene que decir y lo que no puede decir. El negocio es un no
   * obligado a facturar (art. 1.6.1.4.3 del Decreto 1625 de 2016) y optar por facturar lo
   * convertiría en obligado —parágrafo 1 del art. 8 de la Resolución DIAN 000165 de 2023—, así que
   * un documento que se llamara factura sería exactamente lo que no se quiso construir.
   */
  @Test
  void elComprobanteDiceQueNoEsUnaFactura() {
    String cierre = textos().texto(TextoDeCorreo.PEDIDO_COMPROBANTE_CIERRE, "https://x.co");

    assertTrue(cierre.contains("no es una factura de venta"), cierre);
    assertTrue(cierre.contains("1.6.1.4.3"), cierre);
    assertTrue(cierre.contains("garantía"), cierre);
  }

  /**
   * Quién vendió, que es información obligatoria del proveedor (Ley 1480 de 2011). Si esto cambia
   * sin cambiar el pie del sitio, {@code npm run datos-negocio} lo detiene: estas tres cifras viven
   * en once copias y el teléfono ya estuvo mal en cuatro de ellas durante una fase entera.
   */
  @Test
  void elComprobanteIdentificaAlVendedor() {
    String vendedor = textos().texto(TextoDeCorreo.PEDIDO_COMPROBANTE_VENDEDOR);

    assertTrue(vendedor.contains("NIT 1054994043-9"), vendedor);
    assertTrue(vendedor.contains("contacto@tecnosport.co"), vendedor);
    assertTrue(vendedor.contains("Medellín"), vendedor);
  }

  /** Y el comprobante tampoco nombra ningún IVA, porque el negocio no es responsable de él. */
  @Test
  void elComprobanteNoCobraIva() {
    String totales =
        textos().texto(TextoDeCorreo.PEDIDO_COMPROBANTE_TOTALES, "179.800", "7.850", "187.650");

    assertFalse(totales.contains("IVA"), totales);
    assertTrue(totales.contains("179.800"), totales);
    assertTrue(totales.contains("7.850"), totales);
  }

  /** Y existe en inglés de verdad, como los demás. */
  @Test
  void elComprobanteExisteEnIngles() {
    ResourceBundleMessageSource fuente = new ResourceBundleMessageSource();
    fuente.setBasename("correos");
    fuente.setDefaultEncoding("UTF-8");
    fuente.setFallbackToSystemLocale(false);

    String en =
        fuente.getMessage(
            TextoDeCorreo.PEDIDO_COMPROBANTE_CIERRE.clave(),
            new Object[] {"https://x.co"},
            Locale.of("en"));

    assertTrue(en.contains("not a sales invoice"), en);
  }
}
