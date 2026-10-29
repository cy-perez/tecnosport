package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * El título no promete lo que el anuncio no escribe, y el de una réplica nombra la marca que el
 * texto escribe: los jeans de La Riverah del 9 de octubre de 2026.
 */
class CalificativosDelTituloTest {

  private static final String JEANS_LEVIS =
      """
      *Jeans AA para caballero*
      👖👖👖
      ⭕️Marca Levis
      ✅Tela Licrada
      ✅Talla  30 32 34 36

      🤑🤑*55.000*
      """;

  private static final String JEANS_PLUS =
      """
      *Jeans AA plus para caballero*
      👖👖👖
      ✅Tela Licrada
      ✅Talla  Plus
      Talla de la imagen

      🤑🤑$55.000
      """;

  private static ProductoExtraido jean(String titulo, List<String> marcas) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.ROPA,
        TipoProductoProveedor.PANTALON,
        Dinero.deCop(55000),
        Tallas.lista(List.of("30", "32", "34", "36")),
        null,
        List.of(),
        "Licrada",
        "Jean para caballero con tela licrada.",
        "Jeans for men",
        true,
        new BigDecimal("0.95"),
        null,
        null,
        null,
        List.of(),
        marcas,
        null);
  }

  @Test
  void elPlusQueElAnuncioNoEscribeSeQuita() {
    assertEquals(
        "Jean para caballero importado",
        CalificativosDelTitulo.sinLosQueElTextoNoEscribe(
            "Jean plus para caballero importado", JEANS_LEVIS));
  }

  @Test
  void elPlusQueElAnuncioEscribeSeQueda() {
    assertEquals(
        "Jean plus para caballero importado",
        CalificativosDelTitulo.sinLosQueElTextoNoEscribe(
            "Jean plus para caballero importado", JEANS_PLUS));
  }

  @Test
  void loQueNoEsUnCalificativoDeLaListaNoSeToca() {
    assertEquals(
        "Buzo manga larga",
        CalificativosDelTitulo.sinLosQueElTextoNoEscribe("Buzo manga larga", "*Busito*"));
  }

  @Test
  void sinTituloNoHayNadaQueQuitar() {
    assertNull(CalificativosDelTitulo.sinLosQueElTextoNoEscribe(null, JEANS_LEVIS));
  }

  @Test
  void elJeanLevisQueElModeloTituloPlusEsEstiloLevis() {
    // Lo que devolvió el modelo el 12 de octubre de 2026.
    ProductoExtraido contrastado =
        jean("Jean plus para caballero importado", List.of("Levi's")).contrastadoCon(JEANS_LEVIS);

    assertEquals("Jean estilo Levi's para caballero", contrastado.titulo());
  }

  @Test
  void elJeanPlusSinMarcaSigueImportado() {
    ProductoExtraido contrastado =
        jean("Jean plus para caballero importado", List.of()).contrastadoCon(JEANS_PLUS);

    assertEquals("Jean plus para caballero importado", contrastado.titulo());
  }

  @Test
  void unaMarcaQueElTextoNoEscribeNoEntraAlTitulo() {
    ProductoExtraido contrastado =
        jean("Jean plus para caballero importado", List.of("Diesel")).contrastadoCon(JEANS_PLUS);

    assertEquals("Jean plus para caballero importado", contrastado.titulo());
  }

  @Test
  void elTituloQueYaNombraLaMarcaConEstiloNoCambia() {
    ProductoExtraido contrastado =
        jean("Jean estilo Levi's para caballero", List.of("Levi's")).contrastadoCon(JEANS_LEVIS);

    assertEquals("Jean estilo Levi's para caballero", contrastado.titulo());
  }

  /** «Tela Licrada» dice lo mismo que «licrado»: la raíz, no la palabra. */
  @Test
  void unCalificativoEnOtroGeneroQueElAnuncioEscribeSeQueda() {
    assertEquals(
        "Jean licrado para caballero",
        CalificativosDelTitulo.sinLosQueElTextoNoEscribe(
            "Jean licrado para caballero", JEANS_LEVIS));
    assertEquals(
        "Camiseta oversize",
        CalificativosDelTitulo.sinLosQueElTextoNoEscribe("Camiseta oversize", "OVERSIZED $55.000"));
  }

  /**
   * El título que ya nombra la marca sin «estilo» no la repite, y desde el 29 de octubre de 2026 le
   * pone el «estilo» delante (ADR-0101).
   */
  @Test
  void unaMarcaQueElTituloYaNombraNoSeRepite() {
    ProductoExtraido contrastado =
        jean("Jean Levi's para caballero importado", List.of("Levi's")).contrastadoCon(JEANS_LEVIS);

    assertEquals("Jean estilo Levi's para caballero importado", contrastado.titulo());
  }

  /** Una marca corta se busca como palabra entera: «DG» no está en «adgj». */
  @Test
  void unaMarcaCortaSoloCuentaComoPalabraEntera() {
    ProductoExtraido contrastado =
        jean("Jean para caballero importado", List.of("DG"))
            .contrastadoCon("*Jeans AA para caballero* adgj 55.000");

    assertEquals("Jean para caballero importado", contrastado.titulo());
  }
}
