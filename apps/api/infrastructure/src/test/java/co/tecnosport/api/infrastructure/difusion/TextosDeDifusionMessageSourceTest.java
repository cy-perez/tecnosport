package co.tecnosport.api.infrastructure.difusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.difusion.ArmadorDePieDeFoto;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Hashtag;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.difusion.RedSocial;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

/**
 * Los textos del pie donde viven, y el pie entero tal como sale publicado: con el armador de verdad
 * y los textos de verdad, que es lo que ninguna de las dos pruebas por separado ve.
 */
class TextosDeDifusionMessageSourceTest {

  private static final TextosDeDifusionMessageSource TEXTOS = new TextosDeDifusionMessageSource();

  private static final CriteriosContraentrega CONTRAENTREGA =
      new CriteriosContraentrega(true, Dinero.deCop(20_000), Dinero.deCop(5_000_000), Set.of());

  @Test
  void elPieDeFacebookSaleAsi() {
    ArmadorDePieDeFoto armador =
        new ArmadorDePieDeFoto(
            "https://www.tecnosport.co",
            List.of(new Hashtag("TecnoSport"), new Hashtag("Medellín")),
            TEXTOS,
            CONTRAENTREGA);

    String pie = armador.armar(jblGrip(), RedSocial.FACEBOOK);

    assertEquals(
        """
        ✨ JBL Grip · $299.900

        Un parlante portátil de 385 gramos.

        ✔️ Certificación IP68
        ✔️ Hasta 12 horas de batería

        🚚 Envío a todo Colombia
        💳 Paga en línea, o contra entrega si tu ciudad tiene cobertura
        💬 ¿Dudas? Escríbenos por WhatsApp al 313 881 6711

        👉 Míralo y cómpralo aquí: https://www.tecnosport.co/es/productos/jbl-grip

        #Parlantes #JBL #TecnoSport #Medellín""",
        pie);
  }

  @Test
  void enInstagramRemiteALaBio() {
    ArmadorDePieDeFoto armador =
        new ArmadorDePieDeFoto("https://www.tecnosport.co", List.of(), TEXTOS, CONTRAENTREGA);

    String pie = armador.armar(jblGrip(), RedSocial.INSTAGRAM);

    assertTrue(pie.contains("👉 Cómpralo desde el enlace de nuestra bio"), pie);
    assertFalse(pie.contains("https://"), pie);
  }

  /** El agrupamiento de miles es el colombiano: un punto, y sin decimales. */
  @Test
  void elPrecioSeEscribeComoEnColombia() {
    assertEquals("1.299.900", TEXTOS.dinero(Dinero.deCop(1_299_900)));
  }

  /** Un texto que falta en cualquier idioma impide arrancar, en vez de dar un 500 en el panel. */
  @Test
  void unaFuenteIncompletaNoArranca() {
    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () -> new TextosDeDifusionMessageSource(new StaticMessageSource()));

    assertTrue(error.getMessage().contains("Falta el texto de difusión"), error.getMessage());
  }

  /** Un apóstrofe suelto se come el marcador que sigue: la comprobación de arranque lo ve. */
  @Test
  void unApostrofeSueltoNoPasaElArranque() {
    StaticMessageSource fuente = new StaticMessageSource();
    for (var texto : co.tecnosport.api.application.difusion.TextoDeDifusion.values()) {
      for (var idioma : TextosDeDifusionMessageSource.IDIOMAS_QUE_DEBEN_EXISTIR) {
        fuente.addMessage(texto.clave(), idioma, "Mira l'oferta {0}");
      }
    }

    assertThrows(IllegalStateException.class, () -> new TextosDeDifusionMessageSource(fuente));
  }

  private static Producto jblGrip() {
    return new Producto(
        UUID.randomUUID(),
        "JBL Grip",
        new Slug("jbl-grip"),
        "Un parlante portátil de 385 gramos.\n\n• Certificación IP68\n• Hasta 12 horas de batería",
        new Marca(UUID.randomUUID(), "JBL"),
        new Categoria(
            UUID.randomUUID(),
            "Parlantes",
            new Slug("parlantes"),
            LineaCatalogo.TECNOLOGIA,
            null,
            List.of(new Hashtag("Parlantes"))),
        EstadoProducto.PUBLICADO,
        null,
        List.of(),
        List.of(
            Variante.crear(
                new Sku("JBL-GRIP"),
                new Dinero(BigDecimal.valueOf(299900)),
                BigDecimal.ZERO,
                null,
                new Paquete(540, 20, 10, 10),
                List.of())));
  }
}
