package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductoExtraidoTest {

  private static ProductoExtraido bolso(BigDecimal confianza) {
    return new ProductoExtraido(
        true,
        false,
        " Bolso de dama mediano ",
        LineaCatalogo.BOLSOS,
        TipoProductoProveedor.BOLSO,
        Dinero.deCop(53000),
        Tallas.desconocida(),
        4,
        null,
        "importado",
        List.of("incluye llavero"),
        confianza,
        "  ");
  }

  @Test
  void loQueElMensajeNoDiceQuedaVacioYNadaSeRellena() {
    ProductoExtraido extraido = bolso(new BigDecimal("0.92"));

    assertEquals(Optional.of("Bolso de dama mediano"), extraido.tituloOpcional());
    assertEquals(List.of(), extraido.tonosNombrados());
    assertEquals(null, extraido.notas());
    assertEquals(Optional.of(LineaCatalogo.BOLSOS), extraido.lineaOpcional());
    assertEquals(Optional.of(Dinero.deCop(53000)), extraido.precioProveedorOpcional());
  }

  @Test
  void sinTipoNiTallasCaeEnOtroYDesconocida() {
    ProductoExtraido extraido =
        new ProductoExtraido(
            true,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            BigDecimal.ONE,
            null);

    assertEquals(TipoProductoProveedor.OTRO, extraido.tipo());
    assertEquals(TipoDeTalla.DESCONOCIDA, extraido.tallas().tipo());
    assertEquals(Optional.empty(), extraido.tituloOpcional());
    assertEquals(Optional.empty(), extraido.lineaOpcional());
  }

  @Test
  void laConfianzaVaDeCeroAUno() {
    assertThrows(ExcepcionDeDominio.class, () -> bolso(new BigDecimal("1.01")));
    assertThrows(ExcepcionDeDominio.class, () -> bolso(new BigDecimal("-0.1")));
    assertThrows(ExcepcionDeDominio.class, () -> bolso(null));
  }

  @Test
  void lasTallasSeSostienenSolas() {
    assertEquals(Optional.of("L"), Tallas.unica("L").sirveHastaOpcional());
    assertEquals(List.of("M", "L"), Tallas.lista(List.of("M", "L")).valores());
    assertThrows(ExcepcionDeDominio.class, () -> Tallas.lista(List.of()));
    assertThrows(ExcepcionDeDominio.class, () -> new Tallas(TipoDeTalla.UNICA, "L", List.of("M")));
    assertEquals(TipoDeTalla.DESCONOCIDA, new Tallas(null, null, null).tipo());
  }
}
