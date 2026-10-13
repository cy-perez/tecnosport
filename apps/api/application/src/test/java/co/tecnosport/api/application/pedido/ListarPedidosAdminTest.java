package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.proveedores.AjusteDeTalla;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.TallaAlProveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListarPedidosAdminTest {

  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private static final Marca MARCA = new Marca(UUID.randomUUID(), "Genérica");
  private static final Atributo TALLA = Atributo.crear("Talla", TipoAtributo.TEXTO, List.of());
  private static final Categoria POLOS =
      Categoria.crear("Polos", new Slug("ropa-caballero-polos"), LineaCatalogo.ROPA);
  private static final Categoria CALZADO =
      Categoria.crear("Calzado caballero", new Slug("calzado-caballero"), LineaCatalogo.CALZADO)
          .conEscalaDeTallas(
              List.of("34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45"));

  private RepositorioPedidosFalso pedidos;
  private final RepositorioProductosFalso productos = new RepositorioProductosFalso();

  private ListarPedidosAdmin crear() {
    pedidos = new RepositorioPedidosFalso();
    return new ListarPedidosAdmin(
        pedidos, productos, new CategoriasEnMemoria(List.of(POLOS, CALZADO)));
  }

  private static Producto deProveedor(String titulo, Categoria categoria, int ajuste) {
    Producto producto =
        Producto.crearDeProveedor(
            titulo,
            Slug.generarDesde(titulo),
            "Importado.",
            MARCA,
            categoria,
            UUID.randomUUID(),
            Dinero.deCop(50_000),
            HuellaProveedor.calcular(UUID.randomUUID(), titulo, Dinero.deCop(50_000)),
            Instant.parse("2026-10-13T12:00:00Z"));
    producto.definirAjusteDeTalla(new AjusteDeTalla(ajuste));
    return producto;
  }

  private static Variante enTalla(Producto producto, String talla) {
    Variante variante =
        Variante.crear(
            new Sku("PRV-" + talla + "-" + producto.slug().valor()),
            Dinero.deCop(69_900),
            BigDecimal.ZERO,
            null,
            null,
            List.of(ValorAtributo.de(TALLA, talla)));
    producto.agregarVariante(variante);
    return variante;
  }

  private Pedido pedido(int secuencial, Instant creadoEn) {
    return pedido(secuencial, creadoEn, UUID.randomUUID());
  }

  private Pedido pedido(int secuencial, Instant creadoEn, UUID varianteId) {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, secuencial),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.WOMPI,
            "cliente@tecnosport.co",
            creadoEn);
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void listaPedidosOrdenadosPorFechaDescendente() {
    ListarPedidosAdmin caso = crear();
    Instant t0 = Instant.parse("2026-09-01T00:00:00Z");
    Pedido primero = pedido(1, t0);
    Pedido segundo = pedido(2, t0.plusSeconds(60));

    PedidosPaginados resultado = caso.ejecutar(new ListarPedidosAdminComando(0, 10)).pedidos();

    assertEquals(2, resultado.items().size());
    assertEquals(segundo.id(), resultado.items().get(0).id());
    assertEquals(primero.id(), resultado.items().get(1).id());
    assertEquals(1, resultado.totalPaginas());
    assertEquals(2, resultado.totalPedidos());
  }

  /**
   * El polo de Meraki se vende en S y se le pide al proveedor en M; el tenis de Imperio Wicho se
   * vende en 38 y se pide en 40 (ADR-0085). Una XXXL agregada después no se sabe cómo pedirla, y lo
   * dice. Lo que el proveedor nombra igual no sale.
   */
  @Test
  void traeQueTallaPedirleAlProveedorPorLasLineasDeLaPagina() {
    ListarPedidosAdmin caso = crear();
    Instant t0 = Instant.parse("2026-09-01T00:00:00Z");
    Producto polo = deProveedor("Polo estilo Puma - Ferrari", POLOS, 1);
    Variante poloS = enTalla(polo, "S");
    Variante poloXxxl = enTalla(polo, "XXXL");
    Producto tenis = deProveedor("Tenis para caballero", CALZADO, 2);
    Variante tenis38 = enTalla(tenis, "38");
    Producto camiseta = deProveedor("Camiseta", POLOS, 0);
    Variante camisetaM = enTalla(camiseta, "M");
    productos.conProductos(polo, tenis, camiseta);
    pedido(1, t0, poloS.id());
    pedido(2, t0.plusSeconds(60), poloXxxl.id());
    pedido(3, t0.plusSeconds(120), tenis38.id());
    pedido(4, t0.plusSeconds(180), camisetaM.id());
    pedido(5, t0.plusSeconds(240), UUID.randomUUID());

    PedidosDelPanel pagina = caso.ejecutar(new ListarPedidosAdminComando(0, 10));

    assertEquals(
        Map.of(
            poloS.id(), new TallaAlProveedor("M"),
            poloXxxl.id(), TallaAlProveedor.desconocida(),
            tenis38.id(), new TallaAlProveedor("40")),
        pagina.tallasAlProveedor());
  }

  /** La talla de hoy: cambiada en el panel después de vender, la que se pide cambia con ella. */
  @Test
  void cuentaDesdeLaTallaQueLaVarianteTieneHoy() {
    ListarPedidosAdmin caso = crear();
    Producto polo = deProveedor("Polo estilo Puma - Ferrari", POLOS, 1);
    Variante variante = enTalla(polo, "S");
    productos.conProductos(polo);
    pedido(1, Instant.parse("2026-09-01T00:00:00Z"), variante.id());
    polo.cambiarTalla(variante.id(), "L");

    PedidosDelPanel pagina = caso.ejecutar(new ListarPedidosAdminComando(0, 10));

    assertEquals(Map.of(variante.id(), new TallaAlProveedor("XL")), pagina.tallasAlProveedor());
  }

  @Test
  void sinPedidosNoHayTallasQuePedir() {
    ListarPedidosAdmin caso = crear();

    assertTrue(caso.ejecutar(new ListarPedidosAdminComando(0, 10)).tallasAlProveedor().isEmpty());
  }

  @Test
  void respetaElTamanoDePaginaYCalculaElTotalDePaginas() {
    ListarPedidosAdmin caso = crear();
    Instant t0 = Instant.parse("2026-09-01T00:00:00Z");
    for (int i = 1; i <= 5; i++) {
      pedido(i, t0.plusSeconds(i));
    }

    PedidosPaginados primeraPagina = caso.ejecutar(new ListarPedidosAdminComando(0, 2)).pedidos();
    PedidosPaginados segundaPagina = caso.ejecutar(new ListarPedidosAdminComando(1, 2)).pedidos();

    assertEquals(2, primeraPagina.items().size());
    assertEquals(2, segundaPagina.items().size());
    assertEquals(3, primeraPagina.totalPaginas());
    assertEquals(5, primeraPagina.totalPedidos());
  }

  @Test
  void unaPaginaFueraDeRangoDevuelveVacio() {
    ListarPedidosAdmin caso = crear();
    pedido(1, Instant.parse("2026-09-01T00:00:00Z"));

    PedidosPaginados resultado = caso.ejecutar(new ListarPedidosAdminComando(5, 10)).pedidos();

    assertTrue(resultado.items().isEmpty());
  }

  @Test
  void filtraPorEstadoYOrdenaPorMasAntiguoPrimero() {
    ListarPedidosAdmin caso = crear();
    Instant t0 = Instant.parse("2026-09-01T00:00:00Z");
    Pedido pendienteViejo = pedido(1, t0);
    pendienteViejo.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "pago aprobado", t0);
    pendienteViejo.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "preparación", t0);
    pendienteViejo.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despacho", t0);
    pendienteViejo.transicionar(EstadoPedido.ENTREGADO, "admin:test", "entregado", t0);
    pendienteViejo.transicionar(EstadoPedido.RECAUDO_PENDIENTE, "admin:test", "recaudo", t0);
    pedidos.guardar(pendienteViejo);

    Instant t1 = t0.plusSeconds(60);
    Pedido pendienteReciente = pedido(2, t1);
    pendienteReciente.transicionar(EstadoPedido.PAGADO, "webhook-wompi", "pago aprobado", t1);
    pendienteReciente.transicionar(EstadoPedido.EN_PREPARACION, "admin:test", "preparación", t1);
    pendienteReciente.transicionar(EstadoPedido.DESPACHADO, "admin:test", "despacho", t1);
    pendienteReciente.transicionar(EstadoPedido.ENTREGADO, "admin:test", "entregado", t1);
    pendienteReciente.transicionar(EstadoPedido.RECAUDO_PENDIENTE, "admin:test", "recaudo", t1);
    pedidos.guardar(pendienteReciente);

    pedido(3, t0.plusSeconds(120));

    PedidosPaginados resultado =
        caso.ejecutar(new ListarPedidosAdminComando(0, 10, EstadoPedido.RECAUDO_PENDIENTE))
            .pedidos();

    assertEquals(2, resultado.totalPedidos());
    assertEquals(pendienteViejo.id(), resultado.items().get(0).id());
    assertEquals(pendienteReciente.id(), resultado.items().get(1).id());
  }

  @Test
  void unTamanoDePaginaFueraDeRangoSeRechaza() {
    assertThrows(IllegalArgumentException.class, () -> new ListarPedidosAdminComando(0, 0));
    assertThrows(IllegalArgumentException.class, () -> new ListarPedidosAdminComando(0, 101));
    assertThrows(IllegalArgumentException.class, () -> new ListarPedidosAdminComando(-1, 10));
  }

  private record CategoriasEnMemoria(List<Categoria> todas) implements RepositorioCategorias {

    @Override
    public List<Categoria> listarTodas() {
      return todas;
    }

    @Override
    public Optional<Categoria> buscarPorId(UUID id) {
      return todas.stream().filter(categoria -> categoria.id().equals(id)).findFirst();
    }

    @Override
    public Optional<Categoria> buscarPorSlug(Slug slug) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Categoria> hijasDe(UUID padreId) {
      return List.of();
    }

    @Override
    public void guardar(Categoria categoria) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void eliminar(UUID id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean tieneProductos(UUID categoriaId) {
      return false;
    }
  }
}
