package co.tecnosport.api.infrastructure.difusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.difusion.ArmadorDePieDeFoto;
import co.tecnosport.api.application.difusion.MinimosDeCredito;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

/**
 * Los textos del pie donde viven, y el pie entero tal como sale publicado: con el armador de verdad
 * y los textos de verdad, que es lo que ninguna de las dos pruebas por separado ve.
 */
class TextosDeDifusionMessageSourceTest {

  private static final TextosDeDifusionMessageSource TEXTOS = new TextosDeDifusionMessageSource();

  @Test
  void elPieDeFacebookSaleAsi() {
    String pie = armador(MinimosDeCredito.ninguno()).armar(jblGrip(), RedSocial.FACEBOOK);

    assertEquals(
        """
        📦 JBL Grip · $299.900

        Un parlante portátil de 385 gramos.
        ✔️ Certificación IP68.
        ✔️ Hasta 12 horas de batería.

        💰 Diferentes opciones de pago:
        💳 Tarjeta débito o crédito y PSE.
        💵 Pago contraentrega.
        📲 Transferencia.
        📍 Opción de recogida en nuestro punto físico.
        🚚 Envíos a toda Colombia.

        👉 ¿Te interesa este producto?. Míralo y cómpralo aquí:
        https://www.tecnosport.co/es/productos/jbl-grip

        💬 ¿Dudas?. Escríbenos por WhatsApp:
        https://wa.me/573138816711

        Síguenos en Instagram:
        https://www.instagram.com/tecnosport_co/

        Visita nuestro sitio web para ver más productos:
        https://www.tecnosport.co/

        #Parlantes #JBL #tecnosport""",
        pie);
  }

  @Test
  void elPieDeInstagramSaleAsi() {
    MinimosDeCredito creditos =
        new MinimosDeCredito(
            Optional.of(Dinero.deCop(200_000)), Optional.of(Dinero.deCop(100_000)));

    String pie = armador(creditos).armar(jblGrip(), RedSocial.INSTAGRAM);

    assertEquals(
        """
        📦 JBL Grip · $299.900

        Un parlante portátil de 385 gramos.
        ✔️ Certificación IP68.
        ✔️ Hasta 12 horas de batería.

        💰 Diferentes opciones de pago:
        💳 Tarjeta débito o crédito y PSE.
        💵 Pago contraentrega.
        📲 Transferencia.
        🏦 Addi o Sistecrédito.
        📍 Opción de recogida en nuestro punto físico.
        🚚 Envíos a toda Colombia.

        👉 ¿Te interesa este producto?. Cómpralo desde el enlace de nuestra bio 🔗.

        💬 ¿Dudas?. Escríbenos por WhatsApp al 313 881 6711.

        Síguenos en Facebook:
        https://www.facebook.com/tecnosport.co

        Visita nuestro sitio web para ver más productos:
        https://www.tecnosport.co/

        #Parlantes #JBL #tecnosport""",
        pie);
  }

  /**
   * Un punto pegado a una URL puede quedar dentro del enlace en Facebook, y el enlace dejaría de
   * abrir. Todo renglón que acaba en URL va sin punto, en las dos redes y en los dos idiomas.
   */
  @Test
  void ningunaUrlLlevaPuntoFinal() {
    for (RedSocial red : RedSocial.values()) {
      String pie = armador(MinimosDeCredito.ninguno()).armar(jblGrip(), red);

      assertFalse(pie.matches("(?s).*https://\\S*\\.(\\s.*|$)"), pie);
    }
  }

  /**
   * Cada renglón de texto acaba en punto, salvo los que la plantilla exceptúa: la entrada, los que
   * abren una URL con dos puntos, las URL y los hashtags.
   */
  @Test
  void cadaRenglonDeTextoAcabaEnPunto() {
    for (RedSocial red : RedSocial.values()) {
      String pie = armador(MinimosDeCredito.ninguno()).armar(jblGrip(), red);

      pie.lines()
          .filter(l -> !l.isBlank())
          .filter(l -> !l.startsWith("📦 "))
          .filter(l -> !l.startsWith("https://"))
          .filter(l -> !l.startsWith("#"))
          .forEach(l -> assertTrue(l.endsWith(".") || l.endsWith(":"), "Sin punto: " + l));
    }
  }

  private static ArmadorDePieDeFoto armador(MinimosDeCredito creditos) {
    return new ArmadorDePieDeFoto(
        "https://www.tecnosport.co",
        List.of(new Hashtag("tecnosport")),
        TEXTOS,
        creditos,
        id -> Optional.empty());
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
