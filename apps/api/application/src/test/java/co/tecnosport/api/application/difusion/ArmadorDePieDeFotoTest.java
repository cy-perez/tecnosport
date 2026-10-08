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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArmadorDePieDeFotoTest {

  private static final ArmadorDePieDeFoto ARMADOR =
      new ArmadorDePieDeFoto(
          "https://www.tecnosport.co", List.of(new Hashtag("TecnoSport"), new Hashtag("Medellín")));

  @Test
  void elPieLlevaNombrePrecioPrimerParrafoEnlaceYEtiquetas() {
    Producto producto = jblGrip(List.of(variante("JBL-GRIP", 299900)));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertEquals(
        """
        JBL Grip - $299.900

        El JBL Grip es un parlante portátil de 385 gramos con certificación IP68.

        https://www.tecnosport.co/es/productos/jbl-grip

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

    assertTrue(pie.contains("Enlace en la bio"), pie);
    assertFalse(pie.contains("https://"), pie);
  }

  @Test
  void unaSolaVarianteLlevaElPrecioFijo() {
    String pie = ARMADOR.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("JBL Grip - $299.900"), pie);
  }

  /**
   * Varias variantes al mismo precio no tienen un "desde" que signifique nada: todas cuestan igual.
   */
  @Test
  void variasVariantesAlMismoPrecioTampocoLlevanDesde() {
    Producto producto = jblGrip(List.of(variante("A", 89900), variante("B", 89900)));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("JBL Grip - $89.900"), pie);
    assertFalse(pie.contains("desde"), pie);
  }

  @Test
  void cuandoLosPreciosDifierenApareceElDesdeConElMenor() {
    Producto producto = jblGrip(List.of(variante("A", 129900), variante("B", 79900)));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("JBL Grip - desde $79.900"), pie);
  }

  /**
   * Una variante inactiva no se puede comprar. Si fuera la más barata, el pie anunciaría un precio
   * que la ficha no confirma — y en Colombia el precio anunciado obliga.
   */
  @Test
  void unaVarianteInactivaNoBajaElPrecioAnunciado() {
    Producto producto = jblGrip(List.of(variante("A", 129900), inactiva(variante("B", 49900))));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.startsWith("JBL Grip - $129.900"), pie);
  }

  @Test
  void sinVariantesActivasNoHayPrecioQueAnunciar() {
    Producto producto = jblGrip(List.of(inactiva(variante("A", 49900))));

    assertThrows(
        ProductoNoDifundibleException.class, () -> ARMADOR.armar(producto, RedSocial.FACEBOOK));
  }

  /** Las viñetas caben en los 2.200 caracteres, pero empujan los hashtags fuera de lo visible. */
  @Test
  void deLaDescripcionSoloEntraElPrimerParrafo() {
    Producto producto =
        producto(
            "El JBL Grip es un parlante portátil.\n\n• Certificación IP68.\n• Bluetooth 5.4.",
            List.of(variante("A", 299900)),
            List.of(new Hashtag("Parlantes")));

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.contains("El JBL Grip es un parlante portátil."), pie);
    assertFalse(pie.contains("IP68"), pie);
  }

  /** Sin línea en blanco de por medio, el primer renglón con viñeta marca igual el final. */
  @Test
  void tambienCortaCuandoLasVinetasVienenPegadas() {
    Producto producto =
        producto(
            "Un parlante portátil.\n• Certificación IP68.",
            List.of(variante("A", 299900)),
            List.of());

    String pie = ARMADOR.armar(producto, RedSocial.FACEBOOK);

    assertTrue(pie.contains("Un parlante portátil."), pie);
    assertFalse(pie.contains("IP68"), pie);
  }

  /**
   * El modelo no tiene tallas: tiene atributos con nombre libre. La línea se escribe con el nombre
   * que les pusieron, sea "Talla", "Color" o "Capacidad".
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

    assertTrue(pie.contains("Talla: S, M"), pie);
    assertTrue(pie.contains("Color: Negro"), pie);
  }

  /**
   * Todo lo publicado hoy es de tecnología y no tiene un solo atributo: el bloque no debe aparecer.
   *
   * <p>Se cuentan los bloques en vez de buscar dos puntos en el texto, porque el enlace lleva los
   * suyos en {@code https:} — la primera versión de esta prueba caía por eso y no por el código.
   */
  @Test
  void sinAtributosNoSePintaElBloque() {
    String pie = ARMADOR.armar(jblGrip(List.of(variante("A", 299900))), RedSocial.FACEBOOK);

    assertEquals(4, pie.split("\n\n").length, pie);
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
    ArmadorDePieDeFoto conBarra = new ArmadorDePieDeFoto("https://www.tecnosport.co/", List.of());

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
        null,
        variantes);
  }

  private static Variante variante(String sku, long precio) {
    return Variante.crear(
        new Sku(sku),
        new Dinero(BigDecimal.valueOf(precio)),
        BigDecimal.ZERO,
        null,
        null,
        List.of());
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
        variante.atributos(),
        variante.setRotacionPropio().orElse(null));
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
        atributos,
        variante.setRotacionPropio().orElse(null));
  }
}
