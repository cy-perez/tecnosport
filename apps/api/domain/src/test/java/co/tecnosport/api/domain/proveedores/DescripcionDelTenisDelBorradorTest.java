package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El borrador de un tenis nace con la descripción compuesta, y la descripción sigue al modelo y a
 * las tallas que corrige quien revisa mientras nadie la reescriba (ADR-0092).
 */
class DescripcionDelTenisDelBorradorTest {

  private static final Instant T = Instant.parse("2026-10-26T15:00:00Z");

  private static BorradorProducto borrador(
      TipoProductoProveedor tipo, String caracteristicas, GeneroDeCalzado genero) {
    ProductoExtraido extraido =
        new ProductoExtraido(
            true,
            false,
            "Tenis importados estilo Nike",
            LineaCatalogo.CALZADO,
            tipo,
            Dinero.deCop(85000),
            Tallas.lista(List.of("38", "39", "40")),
            null,
            List.of(),
            null,
            caracteristicas,
            null,
            true,
            new BigDecimal("0.9"),
            null,
            null);
    BorradorProducto borrador =
        BorradorProducto.nuevo(
            UUID.randomUUID(),
            UUID.randomUUID(),
            extraido,
            "{}",
            Dinero.deCop(85000),
            Dinero.deCop(102000),
            null,
            null,
            Set.of(),
            T);
    BorradorProducto.conGeneroDeCalzado(borrador, genero);
    borrador.describirComoTenis();
    return borrador;
  }

  private static BorradorProducto tenis() {
    return borrador(
        TipoProductoProveedor.TENIS, "Ultralivianos y con suela importada.", GeneroDeCalzado.DAMA);
  }

  @Test
  void naceConLaDescripcionCompuesta() {
    assertEquals(
        Optional.of(
            "Tenis importados estilo Nike para dama. Disponibles desde la talla 38 hasta la 40."
                + " Ultralivianos y con suela importada."),
        tenis().descripcion());
  }

  @Test
  void loQueNoEsUnTenisConservaLaDelExtractor() {
    BorradorProducto bolso =
        borrador(TipoProductoProveedor.BOLSO, "Bolso en cuero sintético.", null);

    assertEquals(Optional.of("Bolso en cuero sintético."), bolso.descripcion());
  }

  /** El panel manda la descripción tal como estaba junto con el título corregido. */
  @Test
  void corregirElModeloRecomponeLaDescripcion() {
    BorradorProducto tenis = tenis();

    tenis.editar(
        "Tenis importados estilo Nike Air Max Plus",
        null,
        null,
        Tallas.lista(List.of("37", "38")),
        null,
        null,
        null,
        tenis.descripcion().orElseThrow(),
        null);

    assertEquals(
        Optional.of(
            "Tenis importados estilo Nike Air Max Plus para dama. Disponibles desde la talla 37"
                + " hasta la 38. Ultralivianos y con suela importada."),
        tenis.descripcion());
  }

  @Test
  void unaDescripcionReescritaMandaSobreElModeloCorregido() {
    BorradorProducto tenis = tenis();

    tenis.editar(
        "Tenis importados estilo Nike Air Max Plus",
        null,
        null,
        null,
        null,
        null,
        null,
        "Un clásico para todos los días.",
        null);

    assertEquals(Optional.of("Un clásico para todos los días."), tenis.descripcion());
  }

  @Test
  void alAprobarConOtroTituloLaDescripcionLoSigue() {
    BorradorProducto tenis = tenis();

    assertEquals(
        Optional.of(
            "Tenis importados estilo Nike Shox R4 para dama. Disponibles en la talla 38."
                + " Ultralivianos y con suela importada."),
        tenis.descripcionDelTenisCon(
            "Tenis importados estilo Nike Shox R4", Tallas.lista(List.of("38"))));
  }
}
