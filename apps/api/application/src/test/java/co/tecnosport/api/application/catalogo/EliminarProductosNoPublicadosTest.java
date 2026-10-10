package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.Inventario;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** El borrado en bloque: lo no publicado se va, lo publicado y lo vendido se quedan, por tandas. */
class EliminarProductosNoPublicadosTest {

  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();
  private final RepositorioPedidosParaBorradoFalso pedidos =
      new RepositorioPedidosParaBorradoFalso();
  private final AlmacenDeImagenesFalso almacen = new AlmacenDeImagenesFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final List<Producto> todos = new ArrayList<>();
  private final EliminarProductosNoPublicados eliminar =
      new EliminarProductosNoPublicados(
          new NoPublicadosEnMemoria(),
          productos,
          inventarios,
          new EliminarProducto(productos, pedidos, almacen));

  @Test
  void borraLosBorradoresConSusFotosYDejaLosPublicadosYLosVendidos() {
    Producto borrador = producto("SKU-1");
    Producto vendido = producto("SKU-2");
    pedidos.conVarianteVendida(vendido.variantes().getFirst().id());
    Producto publicado = producto("SKU-3");
    publicado.asignarImagenPrincipal(imagenPrincipal());
    publicado.publicar();
    conProductos(borrador, vendido, publicado);
    almacen.conObjeto("productos/" + borrador.id() + "/principal-a.jpg", 100);

    assertEquals(2, eliminar.contar(), "cuenta también el vendido: está en borrador");
    ProductosEliminados tanda = eliminar.ejecutar(null, null, 10);

    assertEquals(1, tanda.eliminados());
    assertEquals(1, tanda.conservadosPorVentas());
    assertEquals(1, tanda.objetosBorrados());
    assertNull(tanda.siguiente(), "la tanda no se llenó: no hay más");
    assertEquals(List.of(borrador.id()), productos.productosEliminados);
  }

  /**
   * El cursor deja atrás a los conservados. Con páginas numeradas, el vendido volvería a salir
   * primero en cada tanda y el panel pediría para siempre.
   */
  @Test
  void elCursorAvanzaPorEncimaDeLosConservados() {
    List<Producto> cinco = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      cinco.add(producto("SKU-" + i));
    }
    conProductos(cinco.toArray(Producto[]::new));
    Producto primero = todos.stream().min(Comparator.comparing(Producto::id)).orElseThrow();
    pedidos.conVarianteVendida(primero.variantes().getFirst().id());

    ProductosEliminados una = eliminar.ejecutar(null, null, 2);
    ProductosEliminados dos = eliminar.ejecutar(una.siguiente(), una.hasta(), 2);
    ProductosEliminados tres = eliminar.ejecutar(dos.siguiente(), dos.hasta(), 2);

    assertEquals(1, una.eliminados());
    assertEquals(1, una.conservadosPorVentas());
    assertEquals(2, dos.eliminados());
    assertEquals(1, tres.eliminados());
    assertNull(tres.siguiente());
    assertEquals(4, productos.productosEliminados.size());
    assertEquals(1, eliminar.contar());
  }

  /**
   * Un producto retirado para rehacer sus fotos puede tener unidades en bodega: en bloque no se
   * lleva su libro de inventario.
   */
  @Test
  void unProductoConExistenciasSeConserva() {
    Producto conUnidades = producto("SKU-1");
    Producto sinUnidades = producto("SKU-2");
    conProductos(conUnidades, sinUnidades);
    Inventario inventario = Inventario.crear(conUnidades.variantes().getFirst().id());
    inventario.registrarEntrada(3, "bodega", Instant.parse("2026-10-01T00:00:00Z"));
    inventarios.con(inventario);

    ProductosEliminados tanda = eliminar.ejecutar(null, null, 10);

    assertEquals(1, tanda.eliminados());
    assertEquals(1, tanda.conservadosPorExistencias());
    assertEquals(List.of(sinUnidades.id()), productos.productosEliminados);
  }

  /**
   * El tope se fija en la primera tanda y las siguientes lo respetan: lo que tenga un id por encima
   * —con UUID v7, lo creado en otra pestaña mientras corre el borrado— no entra.
   */
  @Test
  void loQueQuedaPorEncimaDelTopeNoSeBorra() {
    conProductos(producto("SKU-1"), producto("SKU-2"), producto("SKU-3"));
    UUID tope = todos.stream().map(Producto::id).min(Comparator.naturalOrder()).orElseThrow();

    ProductosEliminados tanda = eliminar.ejecutar(null, tope, 10);

    assertEquals(1, tanda.eliminados());
    assertEquals(tope, tanda.hasta());
    assertEquals(List.of(tope), productos.productosEliminados);
  }

  @Test
  void sinNadaEnBorradorNoHayTope() {
    ProductosEliminados tanda = eliminar.ejecutar(null, null, 10);

    assertEquals(0, tanda.eliminados());
    assertNull(tanda.hasta());
  }

  @Test
  void unaTandaVaciaNoSePide() {
    assertThrows(IllegalArgumentException.class, () -> eliminar.ejecutar(null, null, 0));
  }

  private void conProductos(Producto... lista) {
    todos.addAll(List.of(lista));
    productos.conProductos(lista);
  }

  /** Lo que el adaptador saca con SQL: borradores que siguen ahí, por id, después del cursor. */
  private final class NoPublicadosEnMemoria implements ProductosNoPublicados {

    private List<Producto> vigentes() {
      return todos.stream()
          .filter(p -> p.estado() == EstadoProducto.BORRADOR)
          .filter(p -> !productos.productosEliminados.contains(p.id()))
          .sorted(Comparator.comparing(Producto::id))
          .toList();
    }

    @Override
    public long contar() {
      return vigentes().size();
    }

    @Override
    public Optional<UUID> ultimo() {
      return vigentes().stream().map(Producto::id).reduce((a, b) -> b);
    }

    @Override
    public List<UUID> ids(UUID despuesDe, UUID hasta, int limite) {
      return vigentes().stream()
          .map(Producto::id)
          .filter(id -> despuesDe == null || id.compareTo(despuesDe) > 0)
          .filter(id -> id.compareTo(hasta) <= 0)
          .limit(limite)
          .toList();
    }
  }

  private static Producto producto(String sku) {
    Marca marca = Marca.crear("JBL");
    Categoria categoria =
        Categoria.crear("Parlantes", new Slug("parlantes"), LineaCatalogo.TECNOLOGIA);
    Producto producto =
        Producto.crear("JBL " + sku, new Slug("jbl-" + sku.toLowerCase()), "", marca, categoria);
    producto.agregarVariante(
        Variante.crear(
            new Sku(sku), Dinero.deCop(890_000), new BigDecimal("0.00"), null, null, List.of()));
    return producto;
  }

  private static ImagenProducto imagenPrincipal() {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        List.of(new VarianteDeImagen(2000, "https://x/0.jpg", 1000)),
        null,
        2000,
        new HashContenido("%064x".formatted(0)),
        "alt es",
        "alt en");
  }
}
