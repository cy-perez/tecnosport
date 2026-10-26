package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * El tenis de otro proveedor —Imperio Wicho, con su texto— titulado también con lo que se ve en sus
 * fotos (ADR-0090): «Tenis importados estilo &lt;Marca&gt; &lt;Modelo&gt;» como el de Tenis Al Pie.
 */
class TenisDeSusFotosTest {

  private static ProductoExtraido tenis(String titulo, List<String> marcas) {
    return producto(titulo, TipoProductoProveedor.TENIS, marcas);
  }

  private static ProductoExtraido producto(
      String titulo, TipoProductoProveedor tipo, List<String> marcas) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.CALZADO,
        tipo,
        Dinero.deCop(85000),
        null,
        null,
        List.of(),
        null,
        null,
        null,
        false,
        new BigDecimal("0.9"),
        null,
        null,
        null,
        List.of(),
        marcas,
        null);
  }

  private static LecturaDeCalzado vio(String marca, String modelo, boolean seguro) {
    return new LecturaDeCalzado(
        modelo, seguro, GeneroDeCalzado.UNISEX, TipoDeCalzado.TENIS, false, marca, 0);
  }

  /** «NIKE UNISEX ULTRALIVIANO»: el texto dice la marca, la foto dice el modelo. */
  @Test
  void elTextoDiceLaMarcaYLaFotoElModelo() {
    ProductoExtraido conFotos =
        tenis("Tenis importados estilo Nike", List.of("Nike"))
            .conElTenisDeSusFotos(vio("Nike", "Air Max Plus", false), List.of());

    assertEquals(
        Optional.of("Tenis importados estilo Nike Air Max Plus"), conFotos.tituloOpcional());
    assertEquals("Imported Nike Air Max Plus-style sneakers", conFotos.altEn());
    assertEquals(List.of("Nike"), conFotos.marcas());
  }

  /** Si el proveedor escribió el modelo, ese manda: tiene el tenis en la mano. */
  @Test
  void elModeloDelTextoMandaSobreElDeLaFoto() {
    ProductoExtraido delTexto =
        tenis("Tenis importados estilo Adidas Superstar", List.of("Adidas"));

    assertSame(
        delTexto, delTexto.conElTenisDeSusFotos(vio("Adidas", "Campus 00s", true), List.of()));
  }

  /**
   * «Tenis para caballero tipo media ultraliviano»: el texto no dice marca, y la de la foto queda
   * como réplica para revisar.
   */
  @Test
  void sinMarcaEnElTextoVaLaQueReconoceElLector() {
    ProductoExtraido conFotos =
        tenis("Tenis importados", List.of())
            .conElTenisDeSusFotos(vio("Le Coq Sportif", null, false), List.of("LCS"));

    assertEquals(Optional.of("Tenis importados estilo Le Coq Sportif"), conFotos.tituloOpcional());
    assertEquals(List.of("Le Coq Sportif"), conFotos.marcas());
    assertTrue(conFotos.esReplica());
  }

  /** Sin la marca por parecido, las que se leen escritas en la foto. */
  @Test
  void sinMarcaPorParecidoVanLasLeidas() {
    ProductoExtraido conFotos =
        tenis("Tenis importados", List.of())
            .conElTenisDeSusFotos(vio(null, "Old Skool", true), List.of("Vans"));

    assertEquals(Optional.of("Tenis importados estilo Vans Old Skool"), conFotos.tituloOpcional());
  }

  /** Sin nada que se vea ni se lea, el título se queda como estaba. */
  @Test
  void sinMarcaNiModeloQuedaComoEstaba() {
    ProductoExtraido conFotos =
        tenis("Tenis importados", List.of())
            .conElTenisDeSusFotos(vio(null, null, false), List.of());

    assertEquals(Optional.of("Tenis importados"), conFotos.tituloOpcional());
    assertFalse(conFotos.esReplica());
  }

  /** Una chancla o un bolso no se titulan como tenis. */
  @Test
  void loQueNoEsUnTenisNoCambia() {
    ProductoExtraido bolso = producto("Bolso de mano", TipoProductoProveedor.OTRO, List.of());

    assertSame(bolso, bolso.conElTenisDeSusFotos(vio("Nike", "Air Max Plus", true), List.of()));
  }
}
