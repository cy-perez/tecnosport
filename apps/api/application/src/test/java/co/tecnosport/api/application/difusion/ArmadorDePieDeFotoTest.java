package co.tecnosport.api.application.difusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Hashtag;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.difusion.ProductoNoDifundibleException;
import co.tecnosport.api.domain.difusion.RedSocial;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArmadorDePieDeFotoTest {

  private static final ArmadorDePieDeFoto ARMADOR =
      ApoyoDeDifusion.armador(
          "https://www.tecnosport.co", List.of(new Hashtag("TecnoSport"), new Hashtag("Medellín")));

  /**
   * La plantilla entera, bloque por bloque. Con el doble de textos cada pieza sale por su llave: lo
   * que esta prueba fija es el orden y los datos; la redacción la fija {@code
   * TextosDeDifusionMessageSourceTest}.
   */
  @Test
  void elPieSigueLaPlantillaEnSuOrden() {
    Producto producto =
        producto(
            "El JBL Grip es un parlante portátil.\n\n• Certificación IP68.\n• Bluetooth 5.4.",
            List.of(variante("JBL-GRIP", 299900)),
            List.of(new Hashtag("Parlantes")));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertEquals(
        """
        [entrada|[emoji.por_omision]|JBL Grip|[precio.unico|299900]]

        El JBL Grip es un parlante portátil.
        [vineta|Certificación IP68.]
        [vineta|Bluetooth 5.4.]

        [pago.titulo]
        [pago.tarjeta]
        [pago.contraentrega]
        [pago.transferencia]
        [pago.recogida]
        [envio]

        [llamado.facebook|https://www.tecnosport.co/es/productos/jbl-grip]

        [whatsapp.facebook|[url.whatsapp]]

        [redes.instagram|[url.instagram]]

        [sitio|https://www.tecnosport.co/]

        #Parlantes #JBL #TecnoSport #Medellín""",
        pie);
  }

  /**
   * En Instagram un enlace del pie no se puede tocar: el llamado remite a la bio y el WhatsApp va
   * como número. La otra red y el sitio sí van con su URL, por decisión del negocio.
   */
  @Test
  void enInstagramNiElProductoNiElWhatsappVanComoEnlace() {
    Producto producto = jblGrip(List.of(variante("JBL-GRIP", 299900)));

    String pie = ARMADOR.armar(producto, RedSocial.INSTAGRAM);

    assertTrue(pie.contains("[llamado.instagram]"), pie);
    assertTrue(pie.contains("[whatsapp.instagram]"), pie);
    assertFalse(pie.contains("/es/productos/"), pie);
    assertFalse(pie.contains("[url.whatsapp]"), pie);
  }

  /** Cada red remite a la otra: el enlace a la misma red en la que se lee no lleva a nada nuevo. */
  @Test
  void cadaRedRemiteALaOtra() {
    Producto producto = jblGrip(List.of(variante("A", 299900)));

    String enFacebook = ARMADOR.armar(producto, RedSocial.FACEBOOK);
    String enInstagram = ARMADOR.armar(producto, RedSocial.INSTAGRAM);

    assertTrue(enFacebook.contains("[redes.instagram|[url.instagram]]"), enFacebook);
    assertFalse(enFacebook.contains("redes.facebook"), enFacebook);
    assertTrue(enInstagram.contains("[redes.facebook|[url.facebook]]"), enInstagram);
    assertFalse(enInstagram.contains("redes.instagram"), enInstagram);
    assertTrue(enInstagram.contains("[sitio|https://www.tecnosport.co/]"), enInstagram);
  }

  @Test
  void unaSolaVarianteLlevaElPrecioFijo() {
    String pie = ARMADOR.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|[emoji.por_omision]|JBL Grip|[precio.unico|299900]]"), pie);
  }

  /**
   * Varias variantes al mismo precio no tienen un "desde" que signifique nada: todas cuestan igual.
   */
  @Test
  void variasVariantesAlMismoPrecioTampocoLlevanDesde() {
    Producto producto = jblGrip(List.of(variante("A", 89900), variante("B", 89900)));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|[emoji.por_omision]|JBL Grip|[precio.unico|89900]]"), pie);
  }

  @Test
  void cuandoLosPreciosDifierenApareceElDesdeConElMenor() {
    Producto producto = jblGrip(List.of(variante("A", 129900), variante("B", 79900)));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|[emoji.por_omision]|JBL Grip|[precio.desde|79900]]"), pie);
  }

  /**
   * Una variante inactiva no se puede comprar. Si fuera la más barata, el pie anunciaría un precio
   * que la ficha no confirma — y en Colombia el precio anunciado obliga.
   */
  @Test
  void unaVarianteInactivaNoBajaElPrecioAnunciado() {
    Producto producto = jblGrip(List.of(variante("A", 129900), inactiva(variante("B", 49900))));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|[emoji.por_omision]|JBL Grip|[precio.unico|129900]]"), pie);
  }

  @Test
  void sinVariantesActivasNoHayPrecioQueAnunciar() {
    Producto producto = jblGrip(List.of(inactiva(variante("A", 49900))));

    assertThrows(
        ProductoNoDifundibleException.class, () -> ARMADOR.armar(producto, RedSocial.FACEBOOK));
  }

  /** El párrafo va como texto corrido y las viñetas, aparte y con la marca de la plantilla. */
  @Test
  void lasVinetasDeLaDescripcionNoSeMezclanConElParrafo() {
    Producto producto =
        producto(
            "Un parlante portátil.\n• Certificación IP68.",
            List.of(variante("A", 299900)),
            List.of());

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.contains("\n\nUn parlante portátil.\n[vineta|Certificación IP68.]"), pie);
    assertFalse(pie.contains("• "), pie);
  }

  /** Más de cuatro empujan el llamado y los hashtags al fondo de un texto que nadie termina. */
  @Test
  void noPasaDeCuatroVinetas() {
    Producto producto =
        producto(
            "Un celular.\n\n• Uno.\n• Dos.\n- Tres.\n* Cuatro.\n• Cinco.",
            List.of(variante("A", 299900)),
            List.of());

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.contains("[vineta|Cuatro.]"), pie);
    assertFalse(pie.contains("Cinco"), pie);
  }

  /** Una descripción que es solo viñetas no inventa un párrafo con la primera. */
  @Test
  void unaDescripcionSinParrafoSoloDejaLasVinetas() {
    Producto producto =
        producto("• Certificación IP68.\n• Bluetooth 5.4.", List.of(variante("A", 1)), List.of());

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertEquals(1, pie.split("Certificación").length - 1, pie);
  }

  /**
   * Instagram no crea el contenedor si el pie pasa de 2.200 caracteres. Lo primero que se sacrifica
   * son las viñetas: el precio, los pagos y el llamado se quedan.
   */
  @Test
  void unPieDemasiadoLargoPierdeVinetasHastaCaber() {
    String larga = "x".repeat(600);
    Producto producto =
        producto(
            "Un celular.\n\n• " + larga + "\n• " + larga + "\n• " + larga + "\n• " + larga,
            List.of(variante("A", 299900)),
            List.of());

    String pie = ARMADOR.armar(producto, RedSocial.INSTAGRAM);

    assertTrue(pie.length() <= ArmadorDePieDeFoto.TOPE_DEL_PIE, "Mide " + pie.length());
    assertTrue(pie.contains("[vineta|"), "Las que caben se quedan: " + pie.length());
    assertTrue(pie.contains("[llamado.instagram]"), pie);
  }

  /**
   * El modelo no tiene tallas: tiene atributos con nombre libre. La línea se escribe con el nombre
   * que les pusieron; el color se reconoce por su tipo y la talla por su nombre, y van en ese orden
   * —la talla primero— aunque las variantes los traigan al revés.
   */
  @Test
  void losAtributosSeEnsenanConSuNombreReal() {
    Atributo talla = new Atributo(UUID.randomUUID(), "Talla", TipoAtributo.TEXTO, List.of());
    Atributo color = new Atributo(UUID.randomUUID(), "Color", TipoAtributo.COLOR, List.of());
    Producto producto =
        producto(
            "Una camiseta.",
            List.of(
                conAtributos(
                    variante("S-NEG", 79900),
                    List.of(
                        new ValorAtributo(color, "Negro", "#000000"),
                        new ValorAtributo(talla, "S", null))),
                conAtributos(
                    variante("M-NEG", 79900),
                    List.of(
                        new ValorAtributo(color, "Negro", "#000000"),
                        new ValorAtributo(talla, "M", null)))),
            List.of());

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.contains("[atributo.talla|Talla|S, M]\n[atributo.color|Color|Negro]"), pie);
  }

  @Test
  void sinAtributosNoSePintaElBloque() {
    String pie = ARMADOR.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertFalse(pie.contains("[atributo."), pie);
  }

  /** Agotada por el proveedor no se vende ({@code seVende}): no baja el «desde». */
  @Test
  void unaVarianteAgotadaPorElProveedorNoCuenta() {
    Producto producto =
        jblGrip(List.of(variante("A", 129900), agotadaPorProveedor(variante("B", 49900))));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|[emoji.por_omision]|JBL Grip|[precio.unico|129900]]"), pie);
  }

  /**
   * Si con las viñetas fuera sigue sin caber, se va el párrafo y después las etiquetas. El precio,
   * los pagos y el llamado no se tocan nunca.
   */
  @Test
  void unParrafoEnormeTambienSeSacrificaAntesQueElLlamado() {
    Producto producto =
        producto(
            "x".repeat(3_000), List.of(variante("A", 299900)), List.of(new Hashtag("Parlantes")));

    String pie = ARMADOR.armar(producto, RedSocial.INSTAGRAM);

    assertTrue(pie.length() <= ArmadorDePieDeFoto.TOPE_DEL_PIE, "Mide " + pie.length());
    assertTrue(pie.startsWith("[entrada|"), pie);
    assertTrue(pie.contains("[pago.contraentrega]"), pie);
    assertTrue(pie.contains("[llamado.instagram]"), pie);
    assertTrue(pie.contains("#Parlantes"), "Las etiquetas caben sin el párrafo: " + pie);
  }

  @Test
  void laEtiquetaDeLaMarcaSaleDelNombreYNoHayQueEscribirla() {
    String pie = ARMADOR.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertTrue(pie.contains("#JBL"), pie);
  }

  @Test
  void elEnlaceLlevaElPrefijoDeIdiomaPorqueSinElRedirigeALaPortada() {
    assertEquals(
        "https://www.tecnosport.co/es/productos/jbl-grip",
        ARMADOR.enlaceA(jblGrip(List.of(variante("A", 299900)))));
  }

  @Test
  void laBarraFinalDeLaUrlBaseNoDuplicaLaDelEnlace() {
    ArmadorDePieDeFoto conBarra = ApoyoDeDifusion.armador("https://www.tecnosport.co/", List.of());

    assertEquals(
        "https://www.tecnosport.co/es/productos/jbl-grip",
        conBarra.enlaceA(jblGrip(List.of(variante("A", 299900)))));
  }

  // --- el punto al final de cada renglón (ADR-0107) ---

  /**
   * Cada renglón acaba en punto, lo traiga o no quien escribió la descripción; el que ya cierra con
   * un signo no se dobla.
   */
  @Test
  void elParrafoYLasVinetasAcabanEnPuntoSinDoblarlo() {
    Producto producto =
        producto(
            "Un parlante portátil\n\n• Certificación IP68\n• Bluetooth 5.4.\n• ¿Resiste el agua?",
            List.of(variante("A", 299900)),
            List.of());

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.contains("\nUn parlante portátil.\n"), pie);
    assertTrue(pie.contains("[vineta|Certificación IP68.]"), pie);
    assertTrue(pie.contains("[vineta|Bluetooth 5.4.]"), pie);
    assertTrue(pie.contains("[vineta|¿Resiste el agua?]"), pie);
  }

  @Test
  void conPuntoNoTocaLoQueYaCierra() {
    assertEquals("Hola.", ArmadorDePieDeFoto.conPunto(" Hola "));
    assertEquals("Hola.", ArmadorDePieDeFoto.conPunto("Hola."));
    assertEquals("¡Hola!", ArmadorDePieDeFoto.conPunto("¡Hola!"));
    assertEquals("Incluye:", ArmadorDePieDeFoto.conPunto("Incluye:"));
  }

  /** Sin viñetas en la descripción no se inventa ninguna especificación. */
  @Test
  void sinVinetasNoHayEspecificaciones() {
    String pie = ARMADOR.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertFalse(pie.contains("[vineta|"), pie);
  }

  // --- los medios de pago ---

  /**
   * La contraentrega, la recogida y el envío van en todos los productos, también en los que el
   * checkout no puede despachar: lo que no pase por el checkout se coordina por WhatsApp (decisión
   * del 5 de noviembre de 2026). Un producto sin medir y de un millón y medio los lleva igual.
   */
  @Test
  void laContraentregaLaRecogidaYElEnvioVanSiempre() {
    String pie = ARMADOR.armar(jblGrip(List.of(sinMedir("A", 1_500_000))), RedSocial.INSTAGRAM);

    assertTrue(
        pie.contains(
            "[pago.titulo]\n[pago.tarjeta]\n[pago.contraentrega]\n[pago.transferencia]\n"
                + "[pago.recogida]\n[envio]"),
        pie);
  }

  /** Mientras Sistecrédito esté apagado y Addi sin integrar, ningún crédito se anuncia. */
  @Test
  void sinCreditosHabilitadosNoSeAnunciaNinguno() {
    String pie = ARMADOR.armar(jblGrip(List.of(variante("A", 5_000_000))), RedSocial.FACEBOOK);

    assertFalse(pie.contains("[pago.credito"), pie);
  }

  @Test
  void conLosDosHabilitadosYElMinimoAlcanzadoVanEnUnaLinea() {
    ArmadorDePieDeFoto armador = conCreditos(Dinero.deCop(200_000), Dinero.deCop(100_000));

    String pie = armador.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertTrue(pie.contains("[pago.transferencia]\n[pago.credito.addi_y_sistecredito]\n"), pie);
  }

  /**
   * Cada crédito con su mínimo: un producto que alcanza el de Addi y no el de Sistecrédito solo
   * anuncia Addi, y al revés.
   */
  @Test
  void cadaCreditoSoloSiElPrecioAlcanzaSuMinimo() {
    Producto producto = jblGrip(List.of(variante("A", 150_000)));

    String soloAddi =
        conCreditos(Dinero.deCop(200_000), Dinero.deCop(100_000))
            .armar(producto, RedSocial.FACEBOOK);
    String soloSistecredito =
        conCreditos(Dinero.deCop(100_000), Dinero.deCop(200_000))
            .armar(producto, RedSocial.FACEBOOK);
    String ninguno =
        conCreditos(Dinero.deCop(150_001), Dinero.deCop(150_001))
            .armar(producto, RedSocial.FACEBOOK);

    assertTrue(soloAddi.contains("[pago.credito.addi]"), soloAddi);
    assertFalse(soloAddi.contains("sistecredito"), soloAddi);
    assertTrue(soloSistecredito.contains("[pago.credito.sistecredito]"), soloSistecredito);
    assertFalse(soloSistecredito.contains("addi"), soloSistecredito);
    assertFalse(ninguno.contains("[pago.credito"), ninguno);
  }

  /** El mínimo exacto alcanza: el crédito lo admite desde ese valor, no desde el peso siguiente. */
  @Test
  void elMinimoExactoAlcanza() {
    String pie =
        conCreditos(Dinero.deCop(150_000), null)
            .armar(jblGrip(List.of(variante("A", 150_000))), RedSocial.FACEBOOK);

    assertTrue(pie.contains("[pago.credito.sistecredito]"), pie);
  }

  /** Basta con que una variante se pueda financiar: se mide contra la más cara. */
  @Test
  void elMinimoSeMideContraLaVarianteMasCara() {
    String pie =
        conCreditos(Dinero.deCop(200_000), null)
            .armar(
                jblGrip(List.of(variante("A", 120_000), variante("B", 250_000))),
                RedSocial.FACEBOOK);

    assertTrue(pie.contains("[pago.credito.sistecredito]"), pie);
  }

  /** Una agotada por el proveedor no se puede comprar, y tampoco cuenta para alcanzar el mínimo. */
  @Test
  void unaVarianteQueNoSeVendeNoAlcanzaElMinimo() {
    String pie =
        conCreditos(Dinero.deCop(200_000), null)
            .armar(
                jblGrip(
                    List.of(variante("A", 120_000), agotadaPorProveedor(variante("B", 250_000)))),
                RedSocial.FACEBOOK);

    assertFalse(pie.contains("[pago.credito"), pie);
  }

  // --- el emoji de la categoría ---

  @Test
  void laCategoriaConEmojiAbreElPie() {
    Categoria tenis = categoria("Morrales", "bolsos-dama-morrales", null).conEmoji("👟");

    String pie =
        ARMADOR.armar(
            productoEn(tenis, "Tenis urbanos.", List.of(variante("A", 289900))),
            RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|👟|"), pie);
  }

  /**
   * Sin emoji propio usa el de la categoría padre, que es donde se pone una vez para toda la rama.
   */
  @Test
  void sinEmojiPropioUsaElDeLaRama() {
    Categoria calzado = categoria("Dama", "bolsos-dama", null).conEmoji("👟");
    Categoria tenis = categoria("Morrales", "bolsos-dama-morrales", calzado.id());
    ArmadorDePieDeFoto armador =
        ApoyoDeDifusion.armador(
            "https://www.tecnosport.co",
            List.of(),
            MinimosDeCredito.ninguno(),
            id -> id.equals(calzado.id()) ? Optional.of(calzado) : Optional.empty());

    String pie =
        armador.armar(
            productoEn(tenis, "Tenis urbanos.", List.of(variante("A", 289900))),
            RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|👟|"), pie);
  }

  @Test
  void sinEmojiEnLaRamaUsaElGenerico() {
    Categoria calzado = categoria("Dama", "bolsos-dama", null);
    Categoria tenis = categoria("Morrales", "bolsos-dama-morrales", calzado.id());
    ArmadorDePieDeFoto armador =
        ApoyoDeDifusion.armador(
            "https://www.tecnosport.co",
            List.of(),
            MinimosDeCredito.ninguno(),
            id -> Optional.of(calzado));

    String pie =
        armador.armar(
            productoEn(tenis, "Tenis urbanos.", List.of(variante("A", 289900))),
            RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|[emoji.por_omision]|"), pie);
  }

  // --- los hashtags ---

  /**
   * La de la marca solo en tecnología: en ropa, calzado y bolsos no se publica (decisión del
   * negocio).
   */
  @Test
  void fueraDeTecnologiaNoVaLaEtiquetaDeLaMarca() {
    Categoria tenis =
        new Categoria(
            UUID.randomUUID(),
            "Morrales",
            new Slug("bolsos-dama-morrales"),
            LineaCatalogo.BOLSOS,
            null,
            List.of(new Hashtag("Tenis")));

    String pie =
        ARMADOR.armar(
            productoEn(tenis, "Tenis urbanos.", List.of(variante("A", 289900))),
            RedSocial.FACEBOOK);

    assertTrue(pie.endsWith("#Tenis #TecnoSport #Medellín"), pie);
    assertFalse(pie.contains("#Nike"), pie);
  }

  // --- armado de escenarios ---

  private static ArmadorDePieDeFoto conCreditos(Dinero minimoSistecredito, Dinero minimoAddi) {
    return ApoyoDeDifusion.armador(
        "https://www.tecnosport.co",
        List.of(),
        new MinimosDeCredito(
            Optional.ofNullable(minimoSistecredito), Optional.ofNullable(minimoAddi)),
        id -> Optional.empty());
  }

  private static Categoria categoria(String nombre, String slug, UUID padreId) {
    return new Categoria(
        UUID.randomUUID(), nombre, new Slug(slug), LineaCatalogo.BOLSOS, padreId, List.of());
  }

  private static Producto productoEn(
      Categoria categoria, String descripcion, List<Variante> variantes) {
    return new Producto(
        UUID.randomUUID(),
        "Tenis Urbanos",
        new Slug("tenis-urbanos"),
        descripcion,
        new Marca(UUID.randomUUID(), "Nike"),
        categoria,
        EstadoProducto.PUBLICADO,
        null,
        List.of(),
        variantes);
  }

  private static Producto jblGrip(List<Variante> variantes) {
    return producto(
        "El JBL Grip es un parlante portátil de 385 gramos con certificación IP68.",
        variantes,
        List.of(new Hashtag("Parlantes")));
  }

  private static Producto producto(
      String descripcion, List<Variante> variantes, List<Hashtag> hashtagsDeCategoria) {
    Categoria categoria =
        new Categoria(
            UUID.randomUUID(),
            "Parlantes",
            new Slug("parlantes"),
            LineaCatalogo.TECNOLOGIA,
            null,
            hashtagsDeCategoria);
    return new Producto(
        UUID.randomUUID(),
        "JBL Grip",
        new Slug("jbl-grip"),
        descripcion,
        new Marca(UUID.randomUUID(), "JBL"),
        categoria,
        EstadoProducto.PUBLICADO,
        null,
        List.of(),
        variantes);
  }

  /** Medida: es lo normal en lo publicado, y sin medidas el pie no anuncia envío. */
  private static Variante variante(String sku, long precio) {
    return Variante.crear(
        new Sku(sku),
        new Dinero(BigDecimal.valueOf(precio)),
        BigDecimal.ZERO,
        null,
        new Paquete(500, 20, 10, 10),
        List.of());
  }

  private static Variante sinMedir(String sku, long precio) {
    return Variante.crear(
        new Sku(sku),
        new Dinero(BigDecimal.valueOf(precio)),
        BigDecimal.ZERO,
        null,
        null,
        List.of());
  }

  private static Variante agotadaPorProveedor(Variante variante) {
    return new Variante(
        variante.id(),
        variante.sku(),
        variante.precio(),
        variante.tasaIva(),
        variante.codigoBarras().orElse(null),
        variante.paquete().orElse(null),
        EstadoVariante.ACTIVA,
        variante.atributos(),
        true);
  }

  private static Variante inactiva(Variante variante) {
    return new Variante(
        variante.id(),
        variante.sku(),
        variante.precio(),
        variante.tasaIva(),
        variante.codigoBarras().orElse(null),
        variante.paquete().orElse(null),
        EstadoVariante.INACTIVA,
        variante.atributos());
  }

  private static Variante conAtributos(Variante variante, List<ValorAtributo> atributos) {
    return new Variante(
        variante.id(),
        variante.sku(),
        variante.precio(),
        variante.tasaIva(),
        variante.codigoBarras().orElse(null),
        variante.paquete().orElse(null),
        variante.estado(),
        atributos);
  }
}
