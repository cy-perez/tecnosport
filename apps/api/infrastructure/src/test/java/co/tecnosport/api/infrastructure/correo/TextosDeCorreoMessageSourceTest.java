package co.tecnosport.api.infrastructure.correo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
   * Todos los textos, en los dos idiomas. Sin decir cuántos: el número estuvo escrito y llevaba
   * desde la Fase 6 diciendo veintiséis cuando ya eran cincuenta y dos. Es la prueba que hace que
   * {@code correos_en.properties} no sea un archivo decorativo mientras nadie pueda pedir inglés.
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
   * <b>El mismo correo, cuando el pedido va en varios paquetes — y aquí estaba roto.</b> A la
   * primera línea de {@code pedido.despacho.cuerpo_varias} le faltaba la barra de continuación, y
   * en un {@code .properties} de Java eso cierra el valor ahí: las tres líneas siguientes se
   * perdían. Quien recibía un pedido en tres paquetes leía "va en 3 paquetes" y nada más, sin los
   * números de guía y <b>sin el enlace para seguir el pedido</b>.
   *
   * <p><b>La clave existía con valor truncado</b>, así que el guardiãn de arranque no la veía
   * faltar: comprueba que ninguna falte, no que ninguna esté a medias. Y la prueba de al lado
   * cubría el correo de <i>un</i> paquete, que está escrito de otra forma —con {@code } en una sola
   * línea— y por eso nunca se rompió. Dos redacciones para lo mismo, una probada y la otra no.
   *
   * <p>Encontrado el 28 de septiembre de 2026 pasando por aquí a corregir el nombre comercial.
   */
  @Test
  void elCorreoDeVariosPaquetesLlegaEnteroYNoSoloSuPrimeraLinea() {
    String cuerpo =
        textos()
            .texto(
                TextoDeCorreo.PEDIDO_DESPACHO_CUERPO_VARIAS,
                "TS-2026-000001",
                "3",
                "SE1, SE2, SE3",
                "https://tecnosport.co/es/checkout/estado?pedidoId=1&correo=a%40b.co");

    assertTrue(cuerpo.contains("3 paquetes"), cuerpo);
    assertTrue(cuerpo.contains("por separado"), cuerpo);
    assertTrue(cuerpo.contains("SE1, SE2, SE3"), cuerpo);
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

  /**
   * El importe, agrupado según el idioma que rige. Vive aquí y no en el caso de uso porque el
   * agrupamiento de miles es parte del idioma: el paquete inglés dice "COP {0}" y con el separador
   * fijo del castellano habría recibido 179.800, que en inglés se lee ciento setenta y nueve con
   * ocho.
   */
  @Test
  void elImporteSeAgrupaComoSeEscribeEnElIdiomaQueRige() {
    assertEquals("179.800", textos().dinero(Dinero.deCop(179_800)));
    assertEquals("0", textos().dinero(Dinero.deCop(0)));
  }

  /**
   * <b>Ninguna línea suelta en los archivos de texto</b>, en ningún idioma. Esta prueba no mira lo
   * que dicen los correos sino cómo están escritos, y existe porque los dos defectos que
   * aparecieron el 28 de septiembre de 2026 eran el mismo error con dos caras y <b>ninguna
   * herramienta los veía</b>:
   *
   * <ul>
   *   <li>A {@code pedido.despacho.cuerpo_varias} le faltaba la barra de continuación, así que sus
   *       tres líneas siguientes se caían del valor y el correo salía a medias.
   *   <li>Cinco líneas de {@code pedido.plazo_vencido} se habían quedado sueltas al reformatear,
   *       con su texto duplicado más arriba.
   * </ul>
   *
   * <p><b>Java no se queja de ninguno de los dos.</b> Una línea sin {@code =} es, para {@code
   * Properties}, una clave válida con valor vacío; y una clave con valor truncado sigue existiendo.
   * El guardián de arranque comprueba que ninguna <i>falte</i>, que es otra cosa.
   *
   * <p>Por eso se lee el archivo crudo y no el {@code MessageSource}: para cuando el {@code
   * MessageSource} lo cargó, el daño ya es invisible.
   */
  @Test
  void ningunaLineaDeLosArchivosDeTextoQuedaSuelta() throws IOException {
    for (String idioma : List.of("es", "en")) {
      List<String> sueltas = lineasSinClave("correos_" + idioma + ".properties");

      assertTrue(
          sueltas.isEmpty(),
          "correos_"
              + idioma
              + ".properties tiene líneas que no son ni comentario, ni continuación, ni"
              + " clave=valor. O le falta una barra al final de la línea anterior, o son restos de"
              + " una edición: "
              + sueltas);
    }
  }

  /** Las líneas que no son comentario, ni continuación de la anterior, ni {@code clave=valor}. */
  private static List<String> lineasSinClave(String recurso) throws IOException {
    List<String> sueltas = new ArrayList<>();
    try (InputStream entrada =
        TextosDeCorreoMessageSourceTest.class.getClassLoader().getResourceAsStream(recurso)) {
      assertNotNull(entrada, recurso + " no está en el classpath");
      String[] lineas = new String(entrada.readAllBytes(), StandardCharsets.UTF_8).split("\\n");
      boolean continuacion = false;
      for (String cruda : lineas) {
        String linea = cruda.strip();
        boolean sigue = cruda.stripTrailing().endsWith("\\");
        if (continuacion) {
          continuacion = sigue;
          continue;
        }
        if (!linea.isEmpty() && !linea.startsWith("#") && !linea.contains("=")) {
          sueltas.add(linea.length() > 60 ? linea.substring(0, 60) + "…" : linea);
        }
        continuacion = sigue;
      }
    }
    return sueltas;
  }
}
