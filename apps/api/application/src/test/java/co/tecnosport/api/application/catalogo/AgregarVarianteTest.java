package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.TipoMovimientoInventario;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgregarVarianteTest {

  private final RepositorioProductosFalso repositorioProductos = new RepositorioProductosFalso();
  private final RepositorioAtributosFalso repositorioAtributos = new RepositorioAtributosFalso();
  private final RepositorioInventarioFalso repositorioInventario = new RepositorioInventarioFalso();
  private final RelojFalso reloj = new RelojFalso(Instant.parse("2026-01-01T00:00:00Z"));
  private final AgregarVariante agregarVariante =
      new AgregarVariante(repositorioProductos, repositorioAtributos, repositorioInventario, reloj);

  private Producto productoDePrueba() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO);
    return Producto.crear(
        "Camiseta running Dry-Fit", new Slug("camiseta-running-dry-fit"), "", marca, categoria);
  }

  @Test
  void creaLaVarianteConAtributosYRegistraLaEntradaInicial() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    Atributo color = Atributo.crear("Color", TipoAtributo.COLOR, List.of());
    repositorioAtributos.conAtributos(color);

    var variante =
        agregarVariante.ejecutar(
            new AgregarVarianteComando(
                producto.id(),
                "TS-CAM-AZ-M",
                89_900,
                new BigDecimal("0.19"),
                null,
                5,
                180,
                30,
                25,
                4,
                List.of(new ValorAtributoComando(color.id(), "Azul marino", "#1E3A8A"))));

    assertEquals("TS-CAM-AZ-M", variante.sku().valor());
    assertEquals(new Paquete(180, 30, 25, 4), variante.paquete());
    assertEquals(1, variante.atributos().size());
    assertEquals("Azul marino", variante.atributos().get(0).valor());
    assertEquals(producto.id(), repositorioProductos.ultimoProductoIdConVariante);
    assertEquals(variante, repositorioProductos.ultimaVarianteAgregada);

    assertEquals(variante.id(), repositorioInventario.ultimoGuardado.varianteId());
    assertEquals(1, repositorioInventario.ultimoGuardado.movimientos().size());
    var movimiento = repositorioInventario.ultimoGuardado.movimientos().get(0);
    assertEquals(TipoMovimientoInventario.ENTRADA, movimiento.tipo());
    assertEquals(5, movimiento.cantidad());
  }

  @Test
  void conExistenciaInicialCeroNoRegistraNingunMovimiento() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);

    agregarVariante.ejecutar(
        new AgregarVarianteComando(
            producto.id(),
            "TS-CAM-AZ-M",
            89_900,
            new BigDecimal("0.19"),
            null,
            0,
            180,
            30,
            25,
            4,
            List.of()));

    assertTrue(repositorioInventario.ultimoGuardado.movimientos().isEmpty());
  }

  @Test
  void productoInexistenteLanzaProductoNoEncontradoPorId() {
    UUID productoId = UUID.randomUUID();

    assertThrows(
        ProductoNoEncontradoPorIdException.class,
        () ->
            agregarVariante.ejecutar(
                new AgregarVarianteComando(
                    productoId,
                    "TS-1",
                    1000,
                    new BigDecimal("0.19"),
                    null,
                    0,
                    180,
                    30,
                    25,
                    4,
                    List.of())));
  }

  @Test
  void skuYaEnUsoLanzaSkuYaEnUsoException() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    repositorioProductos.conSkusEnUso("TS-YA-EXISTE");

    assertThrows(
        SkuYaEnUsoException.class,
        () ->
            agregarVariante.ejecutar(
                new AgregarVarianteComando(
                    producto.id(),
                    "TS-YA-EXISTE",
                    1000,
                    new BigDecimal("0.19"),
                    null,
                    0,
                    180,
                    30,
                    25,
                    4,
                    List.of())));
  }

  @Test
  void atributoInexistenteLanzaAtributoNoEncontrado() {
    Producto producto = productoDePrueba();
    repositorioProductos.conProductos(producto);
    UUID atributoId = UUID.randomUUID();

    assertThrows(
        AtributoNoEncontradoException.class,
        () ->
            agregarVariante.ejecutar(
                new AgregarVarianteComando(
                    producto.id(),
                    "TS-1",
                    1000,
                    new BigDecimal("0.19"),
                    null,
                    0,
                    180,
                    30,
                    25,
                    4,
                    List.of(new ValorAtributoComando(atributoId, "Azul", null)))));
  }
}
