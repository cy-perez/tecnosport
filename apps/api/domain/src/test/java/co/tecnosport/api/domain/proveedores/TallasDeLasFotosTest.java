package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * «Tallas disponibles en la foto!»: las del rótulo impreso en cada foto (13 de octubre de 2026).
 */
class TallasDeLasFotosTest {

  private static ProductoExtraido tenis(Tallas tallas) {
    return new ProductoExtraido(
        true,
        false,
        "Tenis estilo Nike unisex",
        LineaCatalogo.CALZADO,
        TipoProductoProveedor.TENIS,
        Dinero.deCop(85000),
        tallas,
        null,
        List.of(),
        null,
        "Tenis ultralivianos.",
        null,
        false,
        new BigDecimal("0.9"),
        null,
        null);
  }

  /** WA0149, WA0150 y WA0151: 41-42-43, 35-36-38-41-42 y 38-39-43. */
  @Test
  void sinTallasEnElTextoTomaLasDeSusFotosDeMenorAMayor() {
    ProductoExtraido nike =
        tenis(Tallas.desconocida())
            .conTallasDeLasFotos(
                List.of("41", "42", "43", "35", "36", "38", "41", "42", "38", "39", "43"));

    assertEquals(Tallas.lista(List.of("35", "36", "38", "39", "41", "42", "43")), nike.tallas());
  }

  @Test
  void lasLetrasVanPorLaEscalera() {
    assertEquals(
        Tallas.lista(List.of("S", "M", "XL")),
        tenis(Tallas.desconocida()).conTallasDeLasFotos(List.of("XL", "s", "M")).tallas());
  }

  @Test
  void siElTextoDiceSusTallasMandanEsas() {
    Tallas delTexto = Tallas.lista(List.of("38", "39"));

    assertEquals(delTexto, tenis(delTexto).conTallasDeLasFotos(List.of("40")).tallas());
    assertEquals(
        Tallas.desconocida(), tenis(Tallas.desconocida()).conTallasDeLasFotos(List.of()).tallas());
  }
}
