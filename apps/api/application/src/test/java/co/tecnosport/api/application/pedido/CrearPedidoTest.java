package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.ExistenciaInsuficienteException;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrearPedidoTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");
  private static final Duration RESERVA_PAGO_EN_LINEA = Duration.ofMinutes(30);
  private static final Duration RESERVA_TRANSFERENCIA = Duration.ofHours(24);

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioProductosFalso productos;
  private RepositorioInventarioFalso inventarios;
  private RepositorioPedidosFalso pedidos;
  private Variante variante;

  private CrearPedido crear() {
    productos = new RepositorioProductosFalso();
    inventarios = new RepositorioInventarioFalso();
    pedidos = new RepositorioPedidosFalso();
    return new CrearPedido(
        productos,
        inventarios,
        pedidos,
        new RelojFalso(AHORA),
        RESERVA_PAGO_EN_LINEA,
        RESERVA_TRANSFERENCIA);
  }

  private void publicarProductoConVarianteYExistencia(int existencia) {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO);
    Producto producto =
        Producto.crear(
            "Camiseta running Dry-Fit",
            new Slug("camiseta-running-dry-fit"),
            "Descripción",
            marca,
            categoria);
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            "https://cdn.tecnosport.co/img.jpg",
            "https://cdn.tecnosport.co/img.webp",
            800,
            600,
            1000,
            "hash",
            "alt es",
            "alt en"));
    variante =
        Variante.crear(
            new Sku("TS-CAM-AZ-M"),
            Dinero.deCop(50_000),
            new BigDecimal("0.19"),
            0,
            null,
            List.of());
    producto.agregarVariante(variante);
    producto.publicar();
    productos.conProductos(producto);

    Inventario inventario = Inventario.crear(variante.id());
    inventario.registrarEntrada(existencia, "siembra de prueba", AHORA);
    inventarios.conInventario(inventario);
  }

  private CrearPedidoComando comando(MetodoPago metodoPago, int cantidad) {
    return new CrearPedidoComando(
        null,
        "cliente@tecnosport.co",
        List.of(new CrearPedidoComando.LineaComando(variante.id(), cantidad)),
        TipoEntrega.ENVIO_A_DOMICILIO,
        DIRECCION_MEDELLIN,
        metodoPago);
  }

  @Test
  void congelaPrecioSkuNombreEImagenDelCatalogoReal() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    Pedido pedido = caso.ejecutar(comando(MetodoPago.NEQUI, 2));

    assertEquals(1, pedido.lineas().size());
    var linea = pedido.lineas().get(0);
    assertEquals(variante.sku(), linea.sku());
    assertEquals("Camiseta running Dry-Fit", linea.nombre());
    assertEquals(Dinero.deCop(50_000), linea.precioUnitario());
    assertEquals(new BigDecimal("0.19"), linea.tasaIva());
    assertEquals("https://cdn.tecnosport.co/img.jpg", linea.imagenUrl());
    assertEquals(Dinero.deCop(100_000), pedido.total());
  }

  @Test
  void reservaInventarioDeCadaLinea() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    caso.ejecutar(comando(MetodoPago.NEQUI, 2));

    Inventario inventario = inventarios.buscarPorVarianteId(variante.id()).orElseThrow();
    assertEquals(3, inventario.saldoDisponible(AHORA));
  }

  @Test
  void pagoEnLineaReservaConLaDuracionConfigurada() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    caso.ejecutar(comando(MetodoPago.NEQUI, 2));

    MovimientoInventario reserva = ultimaReserva();
    assertEquals(AHORA.plus(RESERVA_PAGO_EN_LINEA), reserva.expiraEn());
  }

  @Test
  void transferenciaManualReservaPorVeinticuatroHoras() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    caso.ejecutar(comando(MetodoPago.TRANSFERENCIA_MANUAL, 1));

    MovimientoInventario reserva = ultimaReserva();
    assertEquals(AHORA.plus(RESERVA_TRANSFERENCIA), reserva.expiraEn());
  }

  @Test
  void contraentregaReservaSinVencimiento() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    Pedido pedido = caso.ejecutar(comando(MetodoPago.CONTRAENTREGA, 1));

    assertEquals(EstadoPedido.CONFIRMADO_CONTRAENTREGA, pedido.estado());
    assertNull(ultimaReserva().expiraEn());
  }

  @Test
  void metodoDePagoEnLineaQuedaEnPagoPendiente() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    Pedido pedido = caso.ejecutar(comando(MetodoPago.TARJETA, 1));

    assertEquals(EstadoPedido.PAGO_PENDIENTE, pedido.estado());
  }

  @Test
  void guardaElPedidoEnElRepositorio() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    Pedido pedido = caso.ejecutar(comando(MetodoPago.NEQUI, 1));

    assertTrue(pedidos.buscarPorId(pedido.id()).isPresent());
  }

  @Test
  void existenciaInsuficienteLanzaExistenciaInsuficiente() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(1);

    assertThrows(
        ExistenciaInsuficienteException.class, () -> caso.ejecutar(comando(MetodoPago.NEQUI, 2)));
  }

  @Test
  void varianteInexistenteLanzaVarianteNoEncontrada() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);
    CrearPedidoComando comandoConVarianteAjena =
        new CrearPedidoComando(
            null,
            "cliente@tecnosport.co",
            List.of(new CrearPedidoComando.LineaComando(UUID.randomUUID(), 1)),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.NEQUI);

    assertThrows(VarianteNoEncontradaException.class, () -> caso.ejecutar(comandoConVarianteAjena));
  }

  @Test
  void productoSinPublicarLanzaVarianteNoEncontrada() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO);
    Producto productoEnBorrador =
        Producto.crear(
            "Camiseta sin publicar", new Slug("camiseta-sin-publicar"), "", marca, categoria);
    Variante varianteSinPublicar =
        Variante.crear(
            new Sku("TS-SIN-PUB"),
            Dinero.deCop(10_000),
            new BigDecimal("0.19"),
            5,
            null,
            List.of());
    productoEnBorrador.agregarVariante(varianteSinPublicar);
    CrearPedido caso = crear();
    productos.conProductos(productoEnBorrador);
    Inventario inventario = Inventario.crear(varianteSinPublicar.id());
    inventario.registrarEntrada(5, "siembra", AHORA);
    inventarios.conInventario(inventario);
    CrearPedidoComando comandoConVarianteSinPublicar =
        new CrearPedidoComando(
            null,
            "cliente@tecnosport.co",
            List.of(new CrearPedidoComando.LineaComando(varianteSinPublicar.id(), 1)),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.NEQUI);

    assertThrows(
        VarianteNoEncontradaException.class, () -> caso.ejecutar(comandoConVarianteSinPublicar));
    assertEquals(EstadoVariante.ACTIVA, varianteSinPublicar.estado());
  }

  private MovimientoInventario ultimaReserva() {
    List<MovimientoInventario> movimientos =
        inventarios.buscarPorVarianteId(variante.id()).orElseThrow().movimientos();
    return movimientos.get(movimientos.size() - 1);
  }
}
