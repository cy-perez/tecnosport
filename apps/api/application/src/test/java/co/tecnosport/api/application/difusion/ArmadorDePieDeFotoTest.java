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
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
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
        [entrada|JBL Grip|[precio.unico|299900]]

        El JBL Grip es un parlante portátil.

        [vineta|Certificación IP68.]
        [vineta|Bluetooth 5.4.]

        [confianza.envio]
        [confianza.pago_con_contraentrega]
        [confianza.whatsapp]

        [llamado.facebook|https://www.tecnosport.co/es/productos/jbl-grip]

        #Parlantes #JBL #TecnoSport #Medellín""",
        pie);
  }

  /**
   * En Instagram el pie no admite enlaces: lo que se escriba ahí es texto que nadie puede tocar.
   */
  @Test
  void enInstagramRemiteALaBioEnVezDePintarUnEnlaceMuerto() {
    Producto producto = jblGrip(List.of(variante("JBL-GRIP", 299900)));

    String pie = ARMADOR.armar(producto, RedSocial.INSTAGRAM);

    assertTrue(pie.contains("[llamado.instagram]"), pie);
    assertFalse(pie.contains("https://"), pie);
  }

  @Test
  void unaSolaVarianteLlevaElPrecioFijo() {
    String pie = ARMADOR.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|JBL Grip|[precio.unico|299900]]"), pie);
  }

  /**
   * Varias variantes al mismo precio no tienen un "desde" que signifique nada: todas cuestan igual.
   */
  @Test
  void variasVariantesAlMismoPrecioTampocoLlevanDesde() {
    Producto producto = jblGrip(List.of(variante("A", 89900), variante("B", 89900)));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|JBL Grip|[precio.unico|89900]]"), pie);
  }

  @Test
  void cuandoLosPreciosDifierenApareceElDesdeConElMenor() {
    Producto producto = jblGrip(List.of(variante("A", 129900), variante("B", 79900)));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|JBL Grip|[precio.desde|79900]]"), pie);
  }

  /**
   * Una variante inactiva no se puede comprar. Si fuera la más barata, el pie anunciaría un precio
   * que la ficha no confirma — y en Colombia el precio anunciado obliga.
   */
  @Test
  void unaVarianteInactivaNoBajaElPrecioAnunciado() {
    Producto producto = jblGrip(List.of(variante("A", 129900), inactiva(variante("B", 49900))));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|JBL Grip|[precio.unico|129900]]"), pie);
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

    assertTrue(pie.contains("\n\nUn parlante portátil.\n\n"), pie);
    assertTrue(pie.contains("[vineta|Certificación IP68.]"), pie);
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
   * son las viñetas: el precio, la confianza y el llamado se quedan.
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
   * que les pusieron, y el color lleva su propia marca.
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
                        new ValorAtributo(talla, "S", null),
                        new ValorAtributo(color, "Negro", "#000000"))),
                conAtributos(
                    variante("M-NEG", 79900),
                    List.of(
                        new ValorAtributo(talla, "M", null),
                        new ValorAtributo(color, "Negro", "#000000")))),
            List.of());

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.contains("[atributo.otro|Talla|S, M]"), pie);
    assertTrue(pie.contains("[atributo.color|Color|Negro]"), pie);
  }

  @Test
  void sinAtributosNoSePintaElBloque() {
    String pie = ARMADOR.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertFalse(pie.contains("[atributo."), pie);
  }

  // --- la contraentrega solo se anuncia donde el checkout la ofrece ---

  @Test
  void conContraentregaApagadaSoloSeAnunciaElPagoEnLinea() {
    ArmadorDePieDeFoto armador =
        ApoyoDeDifusion.armador(
            "https://www.tecnosport.co",
            List.of(),
            new CriteriosContraentrega(
                false, Dinero.deCop(20_000), Dinero.deCop(5_000_000), Set.of()));

    String pie = armador.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertTrue(pie.contains("[confianza.pago_en_linea]"), pie);
    assertFalse(pie.contains("contraentrega"), pie);
  }

  @Test
  void unaLineaExcluidaNoAnunciaContraentrega() {
    ArmadorDePieDeFoto armador =
        ApoyoDeDifusion.armador(
            "https://www.tecnosport.co",
            List.of(),
            new CriteriosContraentrega(
                true,
                Dinero.deCop(20_000),
                Dinero.deCop(5_000_000),
                Set.of(LineaCatalogo.TECNOLOGIA)));

    String pie = armador.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertTrue(pie.contains("[confianza.pago_en_linea]"), pie);
  }

  /**
   * Se mira la variante más cara y no la más barata: si una pasa del tope, «paga contra entrega»
   * sería falso justo para esa.
   */
  @Test
  void siAlgunaVariantePasaDelTopeNoSeAnunciaContraentrega() {
    ArmadorDePieDeFoto armador =
        ApoyoDeDifusion.armador(
            "https://www.tecnosport.co",
            List.of(),
            new CriteriosContraentrega(
                true, Dinero.deCop(20_000), Dinero.deCop(1_000_000), Set.of()));

    String pie =
        armador.armar(
            jblGrip(List.of(variante("A", 899_900), variante("B", 1_299_900))), RedSocial.FACEBOOK);

    assertTrue(pie.contains("[confianza.pago_en_linea]"), pie);
  }

  /** Sin medidas no hay cotización y el checkout no ofrece envío a domicilio: no se promete. */
  @Test
  void unaVarianteSinMedirQuitaElEnvioDelPie() {
    String pie =
        ARMADOR.armar(
            jblGrip(List.of(variante("A", 299900), sinMedir("B", 299900))), RedSocial.FACEBOOK);

    assertFalse(pie.contains("[confianza.envio]"), pie);
    assertTrue(pie.contains("[confianza.whatsapp]"), pie);
  }

  /**
   * Agotada por el proveedor no se vende ({@code seVende}): ni baja el «desde» ni cuenta para el
   * tope de la contraentrega.
   */
  @Test
  void unaVarianteAgotadaPorElProveedorNoCuenta() {
    Producto producto =
        jblGrip(List.of(variante("A", 129900), agotadaPorProveedor(variante("B", 49900))));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("[entrada|JBL Grip|[precio.unico|129900]]"), pie);
  }

  /**
   * Si con las viñetas fuera sigue sin caber, se va el párrafo y después las etiquetas. El precio,
   * la confianza y el llamado no se tocan nunca.
   */
  @Test
  void unParrafoEnormeTambienSeSacrificaAntesQueElLlamado() {
    Producto producto =
        producto(
            "x".repeat(3_000), List.of(variante("A", 299900)), List.of(new Hashtag("Parlantes")));

    String pie = ARMADOR.armar(producto, RedSocial.INSTAGRAM);

    assertTrue(pie.length() <= ArmadorDePieDeFoto.TOPE_DEL_PIE, "Mide " + pie.length());
    assertTrue(pie.startsWith("[entrada|"), pie);
    assertTrue(pie.contains("[confianza.whatsapp]"), pie);
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

  // --- armado de escenarios ---

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
