package co.tecnosport.api.application.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.application.inventario.DisponibilidadDeVariantes;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.Inventario;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class BuscarProductosTest {

  private static final Instant AHORA = Instant.parse("2026-09-20T15:00:00Z");

  private final RepositorioProductosFalso repositorio = new RepositorioProductosFalso();
  private final RepositorioInventarioFalso inventarios = new RepositorioInventarioFalso();
  private final BuscarProductos buscarProductos =
      new BuscarProductos(
          repositorio, new DisponibilidadDeVariantes(inventarios, new RelojFalso(AHORA)));

  @Test
  void delegaElComandoAlPuertoYDevuelveLaPaginaTalCual() {
    ResultadoPaginado<Producto> esperado =
        new ResultadoPaginado<>(List.of(productoDePrueba("TS-MOR-1")), "cursor-2");
    repositorio.devolverEnBusqueda(esperado);

    FiltroProductos filtro =
        new FiltroProductos(null, null, LineaCatalogo.BOLSOS, 50_000L, 200_000L, "morral");
    BuscarProductosComando comando =
        new BuscarProductosComando(filtro, OrdenProductos.PRECIO_ASC, "cursor-1", 20);

    CatalogoPaginado resultado = buscarProductos.ejecutar(comando);

    assertSame(esperado, resultado.pagina());
    assertEquals(filtro, repositorio.ultimoFiltro);
    assertEquals(OrdenProductos.PRECIO_ASC, repositorio.ultimoOrden);
    assertEquals("cursor-1", repositorio.ultimoCursor);
    assertEquals(20, repositorio.ultimoTamanoPagina);
  }

  /**
   * Lo que esta prueba protege es el defecto entero de adr/0050: antes, la rejilla pintaba
   * "disponible" leyendo una columna que solo movía el alta de la variante. Ahora lo dice el libro.
   */
  @Test
  void laDisponibilidadSaleDelLibroYNoDelCatalogo() {
    Producto conSaldo = productoDePrueba("TS-MOR-CON");
    Producto agotado = productoDePrueba("TS-MOR-SIN");
    repositorio.devolverEnBusqueda(new ResultadoPaginado<>(List.of(conSaldo, agotado), null));
    Variante varianteConSaldo = conSaldo.variantes().get(0);
    Variante varianteAgotada = agotado.variantes().get(0);
    Inventario libro = Inventario.crear(varianteConSaldo.id());
    libro.registrarEntrada(2, "siembra de prueba", AHORA.minus(Duration.ofDays(1)));
    inventarios.con(libro);

    CatalogoPaginado resultado =
        buscarProductos.ejecutar(
            new BuscarProductosComando(
                new FiltroProductos(null, null, null, null, null, null),
                OrdenProductos.RELEVANCIA,
                null,
                20));

    assertTrue(resultado.disponibles().hay(varianteConSaldo.id()));
    assertFalse(resultado.disponibles().hay(varianteAgotada.id()));
  }

  private static Producto productoDePrueba(String sku) {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    Producto producto =
        Producto.crear(
            "Morral urbano",
            new Slug("morral-urbano-" + sku.toLowerCase(Locale.ROOT)),
            "",
            marca,
            categoria);
    producto.agregarVariante(
        Variante.crear(
            new Sku(sku), Dinero.deCop(120_000), new BigDecimal("0.00"), null, null, List.of()));
    return producto;
  }
}
