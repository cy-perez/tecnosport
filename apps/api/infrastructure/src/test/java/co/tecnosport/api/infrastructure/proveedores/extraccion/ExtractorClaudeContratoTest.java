package co.tecnosport.api.infrastructure.proveedores.extraccion;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.application.proveedores.ResultadoExtraccion;
import co.tecnosport.api.application.proveedores.TextoDePublicacion;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.ClaseDePrecioAdicional;
import co.tecnosport.api.domain.proveedores.PrendaAcompanante;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.TipoDeTalla;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Contra la API real, con el esquema y el prompt de verdad. <b>No corre en el pipeline</b>: Gradle
 * excluye la etiqueta {@code integracion-externa} salvo con {@code -PintegracionExterna=true}, y
 * además solo se activa si {@code ANTHROPIC_API_KEY} está definida. Cuesta tokens y depende de un
 * tercero; lo que compra es saber que el esquema sigue siendo uno que la API acepta.
 */
@Tag("integracion-externa")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ExtractorClaudeContratoTest {

  private static ExtractorClaude extractor() {
    return new ExtractorClaude(
        URI.create("https://api.anthropic.com"),
        System.getenv("ANTHROPIC_API_KEY"),
        "claude-haiku-4-5-20251001",
        2048,
        Duration.ofSeconds(30),
        2,
        Duration.ofSeconds(2),
        RecursosDelExtractor.promptDeSistema(),
        RecursosDelExtractor.esquema());
  }

  /** Un pie de foto de Violeta (2 de octubre de 2026): el conjunto son dos productos. */
  @Test
  void leeLosDosProductosDeUnConjuntoDeVioletaEnSuOrden() {
    ResultadoExtraccion resultado =
        extractor()
            .extraer(
                new TextoDePublicacion(
                    "*✨NEW COLLECTION ✨*\n\nChaqueta Denim corta (Q377)\n💲108\nTalla S M L\n\n"
                        + "Jean Mom Fit Licrado (Q343)\n💲119900\nTalla S M L XL",
                    List.of(),
                    LineaCatalogo.ROPA));

    assertThat(resultado.productos()).hasSize(2);
    assertThat(resultado.productos().get(0).titulo()).containsIgnoringCase("chaqueta");
    assertThat(resultado.productos().get(0).precioProveedor()).isEqualTo(Dinero.deCop(108000));
    assertThat(resultado.productos().get(1).titulo()).containsIgnoringCase("jean");
    assertThat(resultado.productos().get(1).precioProveedor()).isEqualTo(Dinero.deCop(119900));
    assertThat(resultado.productos().get(1).tallas().valores()).contains("XL");
  }

  @Test
  void laApiAceptaElEsquemaYLeeElPrimerBolsoDelAnexo() {
    ResultadoExtraccion resultado =
        extractor()
            .extraer(
                new TextoDePublicacion(
                    "*Nueva colección* 😍\nBolso de dama mediano 👜\n2 Compartimientos internos 🌸\n"
                        + "Incluye llavero 🌟\nTira para manos libres en material graduable 👌\n"
                        + "4 tonos disponibles 🌈\nMaterial de excelente calidad, importado 🔝\n"
                        + "Perfecto para estás ocasiones especiales 🤗\n💰 *53.000*",
                    List.of(),
                    LineaCatalogo.BOLSOS));

    assertThat(resultado.productos()).hasSize(1);
    assertThat(resultado.productos().getFirst().esProducto()).isTrue();
    assertThat(resultado.productos().getFirst().precioProveedor()).isEqualTo(Dinero.deCop(53000));
    assertThat(resultado.productos().getFirst().cantidadTonos()).isEqualTo(4);
    assertThat(resultado.productos().getFirst().tallas().tipo()).isEqualTo(TipoDeTalla.DESCONOCIDA);
    assertThat(resultado.productos().getFirst().titulo())
        .isNotBlank()
        .doesNotContain("Nueva colección");
    assertThat(resultado.uso().tokensDeEntrada()).isPositive();
  }

  // --- Los sets de La Riverah (12 de octubre de 2026), con los anuncios del 9 de octubre ---

  private static ProductoExtraido unoDeLaRiverah(String texto) {
    ResultadoExtraccion resultado =
        extractor().extraer(new TextoDePublicacion(texto, List.of(), LineaCatalogo.ROPA));
    assertThat(resultado.productos()).hasSize(1);
    return resultado.productos().getFirst();
  }

  @Test
  void laFaldaConSuSetAnunciadoTraeElComboConLaBlusaYLosTextosDelSet() {
    ProductoExtraido falda =
        unoDeLaRiverah(
            "NUEVA COLECCIÓN \n         ✨👸✨\n\n✅Falda de dama \n✅Tela Mallatex americana \n"
                + "✅Talla  S M L \n\nFalda 🤑$49.900\nSet, falda + básica $ 88.000\n\n"
                + "*LÍNEA EXCLUSIVA PARA PEDIDO*\n📦*+57 301 9854097*");

    assertThat(falda.tipo()).isEqualTo(TipoProductoProveedor.FALDA);
    assertThat(falda.precioProveedor()).isEqualTo(Dinero.deCop(49900));
    assertThat(falda.preciosAdicionales())
        .anySatisfy(
            p -> {
              assertThat(p.clase()).isEqualTo(ClaseDePrecioAdicional.COMBO);
              assertThat(p.prenda()).isEqualTo(PrendaAcompanante.BLUSA);
              assertThat(p.precio()).isEqualTo(Dinero.deCop(88000));
            });
    assertThat(falda.set()).isNotNull();
    assertThat(falda.set().descripcion()).isNotBlank();
  }

  @Test
  void laFaldaQueTieneShortEsUnaFaldaShort() {
    ProductoExtraido falda =
        unoDeLaRiverah(
            "NUEVA COLECCIÓN \n         ✨👸✨\n\n✅Falda de dama ( tiene short ) \n"
                + "✅Textura lisa con caída \n✅Talla  S M L \n\nFalda 🤑$59.900\n"
                + "Set básica + falda $99.000");

    assertThat(falda.tipo()).isEqualTo(TipoProductoProveedor.FALDA_SHORT);
    assertThat(falda.set()).isNotNull();
    assertThat(falda.set().descripcion()).containsIgnoringCase("short");
  }

  @Test
  void laNotaDeLasGorrasEsUnAcompananteYLaPromoUnaPromocion() {
    ProductoExtraido camiseta =
        unoDeLaRiverah(
            "Camisetas Oversize para  Caballero\n      👕👕👕\n⭕ Cuello redondo\n⭕ *PREMIUN*\n"
                + "⭕ Talla S M L XL (Horma real, amplia) \n\n  Camiseta 🎽55.000~~\n"
                + "Promo 4x200.000\nNota: Las gorras tiene un valor de 35mil-");

    assertThat(camiseta.preciosAdicionales())
        .anySatisfy(
            p -> {
              assertThat(p.clase()).isEqualTo(ClaseDePrecioAdicional.ACOMPANANTE);
              assertThat(p.prenda()).isEqualTo(PrendaAcompanante.GORRA);
              assertThat(p.precio()).isEqualTo(Dinero.deCop(35000));
            })
        .anySatisfy(p -> assertThat(p.clase()).isEqualTo(ClaseDePrecioAdicional.PROMOCION));
  }
}
