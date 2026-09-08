package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.legal.RepositorioAutorizacionesFalso;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.ExistenciaInsuficienteException;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.legal.AutorizacionRequeridaException;
import co.tecnosport.api.domain.legal.OrigenAutorizacion;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrearPedidoTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");
  private static final Duration RESERVA_PAGO_EN_LINEA = Duration.ofMinutes(30);
  private static final Duration RESERVA_TRANSFERENCIA = Duration.ofHours(24);

  private static final Direccion DIRECCION_MEDELLIN =
      new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private static final CriteriosContraentrega CRITERIOS_CONTRAENTREGA_PERMISIVOS =
      new CriteriosContraentrega(true, Dinero.deCop(10_000_000), Set.of());

  private static final int MAXIMO_INTENTOS_POR_CUENTA = 5;
  private static final Duration VENTANA_INTENTOS_POR_CUENTA = Duration.ofMinutes(60);

  private RepositorioProductosFalso productos;
  private RepositorioInventarioFalso inventarios;
  private RepositorioPedidosFalso pedidos;
  private RepositorioCoberturaContraentregaFalso cobertura;
  private LimitadorDeIntentosFalso limitadorDeIntentos;
  private RepositorioAutorizacionesFalso autorizaciones;
  private static final String VERSION_POLITICA = "2026-09-07";
  private static final String IP = "190.24.10.5";

  private Variante variante;

  private CrearPedido crear() {
    return crear(CRITERIOS_CONTRAENTREGA_PERMISIVOS, true);
  }

  private CrearPedido crear(CriteriosContraentrega criterios, boolean medellinCubierta) {
    productos = new RepositorioProductosFalso();
    inventarios = new RepositorioInventarioFalso();
    pedidos = new RepositorioPedidosFalso();
    cobertura = new RepositorioCoberturaContraentregaFalso();
    limitadorDeIntentos = new LimitadorDeIntentosFalso();
    autorizaciones = new RepositorioAutorizacionesFalso();
    if (medellinCubierta) {
      cobertura.conCiudadCubierta(DIRECCION_MEDELLIN.codigoDaneCiudad());
    }
    MetodosDePagoDisponibles metodosDePagoDisponibles =
        new MetodosDePagoDisponibles(productos, cobertura, pedidos, criterios);
    return new CrearPedido(
        productos,
        inventarios,
        pedidos,
        metodosDePagoDisponibles,
        new RelojFalso(AHORA),
        RESERVA_PAGO_EN_LINEA,
        RESERVA_TRANSFERENCIA,
        limitadorDeIntentos,
        MAXIMO_INTENTOS_POR_CUENTA,
        VENTANA_INTENTOS_POR_CUENTA,
        autorizaciones,
        VERSION_POLITICA);
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
            new HashContenido("%064x".formatted(1)),
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
        metodoPago,
        true,
        IP);
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
  void contraentregaSeRechazaSiLaCiudadNoEstaCubierta() {
    CrearPedido caso = crear(CRITERIOS_CONTRAENTREGA_PERMISIVOS, false);
    publicarProductoConVarianteYExistencia(5);

    assertThrows(
        ContraentregaNoDisponibleException.class,
        () -> caso.ejecutar(comando(MetodoPago.CONTRAENTREGA, 1)));
  }

  @Test
  void contraentregaSeRechazaSiEstaDeshabilitadaGlobalmente() {
    CriteriosContraentrega deshabilitada =
        new CriteriosContraentrega(false, Dinero.deCop(10_000_000), Set.of());
    CrearPedido caso = crear(deshabilitada, true);
    publicarProductoConVarianteYExistencia(5);

    assertThrows(
        ContraentregaNoDisponibleException.class,
        () -> caso.ejecutar(comando(MetodoPago.CONTRAENTREGA, 1)));
  }

  @Test
  void contraentregaSeRechazaSiElTotalSuperaElMontoMaximo() {
    CriteriosContraentrega montoBajo =
        new CriteriosContraentrega(true, Dinero.deCop(10_000), Set.of());
    CrearPedido caso = crear(montoBajo, true);
    publicarProductoConVarianteYExistencia(5);

    assertThrows(
        ContraentregaNoDisponibleException.class,
        () -> caso.ejecutar(comando(MetodoPago.CONTRAENTREGA, 1)));
  }

  @Test
  void contraentregaSeRechazaSiLaCategoriaEstaExcluida() {
    CriteriosContraentrega sinRopaYCalzado =
        new CriteriosContraentrega(
            true, Dinero.deCop(10_000_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO));
    CrearPedido caso = crear(sinRopaYCalzado, true);
    publicarProductoConVarianteYExistencia(5);

    assertThrows(
        ContraentregaNoDisponibleException.class,
        () -> caso.ejecutar(comando(MetodoPago.CONTRAENTREGA, 1)));
  }

  @Test
  void contraentregaSeRechazaSiElCompradorTieneUnRechazoPrevio() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);
    Pedido pedidoRechazadoAntes =
        Pedido.crear(
            co.tecnosport.api.domain.pedido.NumeroPedido.de(2026, 1),
            null,
            new co.tecnosport.api.domain.compartido.CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new co.tecnosport.api.domain.pedido.LineaPedido(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new Sku("TS-OTRO"),
                    "Otro producto",
                    1,
                    Dinero.deCop(10_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/otro.webp",
                    UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            AHORA);
    pedidoRechazadoAntes.transicionar(EstadoPedido.EN_PREPARACION, "sistema", "preparación", AHORA);
    pedidoRechazadoAntes.transicionar(EstadoPedido.DESPACHADO, "sistema", "despacho", AHORA);
    pedidoRechazadoAntes.transicionar(
        EstadoPedido.RECHAZADO_EN_ENTREGA, "sistema", "cliente no recibió", AHORA);
    pedidos.guardar(pedidoRechazadoAntes);

    assertThrows(
        ContraentregaNoDisponibleException.class,
        () -> caso.ejecutar(comando(MetodoPago.CONTRAENTREGA, 1)));
  }

  @Test
  void contraentregaSeRechazaEnRetiroEnPunto() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);
    CrearPedidoComando comandoRetiroEnPunto =
        new CrearPedidoComando(
            null,
            "cliente@tecnosport.co",
            List.of(new CrearPedidoComando.LineaComando(variante.id(), 1)),
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.CONTRAENTREGA,
            true,
            IP);

    assertThrows(
        ContraentregaNoDisponibleException.class, () -> caso.ejecutar(comandoRetiroEnPunto));
  }

  @Test
  void metodoDePagoEnLineaQuedaEnPagoPendiente() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    Pedido pedido = caso.ejecutar(comando(MetodoPago.TARJETA, 1));

    assertEquals(EstadoPedido.PAGO_PENDIENTE, pedido.estado());
  }

  @Test
  void asignaUnNumeroLegibleParaElAnioEnColombia() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    Pedido pedido = caso.ejecutar(comando(MetodoPago.NEQUI, 1));

    assertEquals("TS-2026-000001", pedido.numeroPedido().valor());
  }

  @Test
  void pedidosConsecutivosRecibenNumerosConsecutivos() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    Pedido primero = caso.ejecutar(comando(MetodoPago.NEQUI, 1));
    Pedido segundo = caso.ejecutar(comando(MetodoPago.NEQUI, 1));

    assertEquals("TS-2026-000001", primero.numeroPedido().valor());
    assertEquals("TS-2026-000002", segundo.numeroPedido().valor());
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
            MetodoPago.NEQUI,
            true,
            IP);

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
            MetodoPago.NEQUI,
            true,
            IP);

    assertThrows(
        VarianteNoEncontradaException.class, () -> caso.ejecutar(comandoConVarianteSinPublicar));
    assertEquals(EstadoVariante.ACTIVA, varianteSinPublicar.estado());
  }

  @Test
  void excederElLimiteDeIntentosPorCuentaLanzaLimiteDeIntentosExcedido() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);
    limitadorDeIntentos.denegarSiempre();

    assertThrows(
        LimiteDeIntentosExcedidoException.class, () -> caso.ejecutar(comando(MetodoPago.NEQUI, 1)));
  }

  private MovimientoInventario ultimaReserva() {
    List<MovimientoInventario> movimientos =
        inventarios.buscarPorVarianteId(variante.id()).orElseThrow().movimientos();
    return movimientos.get(movimientos.size() - 1);
  }

  @Test
  void crearElPedidoDejaLaConstanciaDeAutorizacion() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    caso.ejecutar(comando(MetodoPago.NEQUI, 1));

    AutorizacionDatos constancia = autorizaciones.todas().getFirst();
    assertEquals(OrigenAutorizacion.CHECKOUT, constancia.origen());
    assertEquals(new CorreoElectronico("cliente@tecnosport.co"), constancia.correo());
    assertEquals(VERSION_POLITICA, constancia.versionPolitica());
    assertEquals(IP, constancia.direccionIp());
    assertEquals(AHORA, constancia.otorgadaEn());
  }

  /** Se compra sin cuenta: la constancia vale igual, sin usuario detrás. */
  @Test
  void laConstanciaDelCheckoutAnonimoNoTieneUsuario() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    caso.ejecutar(comando(MetodoPago.NEQUI, 1));

    assertTrue(autorizaciones.todas().getFirst().usuarioId().isEmpty());
  }

  @Test
  void sinAutorizarNoHayPedido() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);
    CrearPedidoComando sinAutorizar =
        new CrearPedidoComando(
            null,
            "cliente@tecnosport.co",
            List.of(new CrearPedidoComando.LineaComando(variante.id(), 1)),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.NEQUI,
            false,
            IP);

    assertThrows(AutorizacionRequeridaException.class, () -> caso.ejecutar(sinAutorizar));
  }

  /**
   * Lo que de verdad importa del caso anterior: la autorización se exige antes de reservar
   * inventario y antes de quemar un número de pedido. Si se comprobara al final, un checkout sin
   * autorizar habría dejado existencias comprometidas y un consecutivo gastado por nada.
   */
  @Test
  void sinAutorizarNoSeReservaInventarioNiSeQuemaUnNumero() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);
    CrearPedidoComando sinAutorizar =
        new CrearPedidoComando(
            null,
            "cliente@tecnosport.co",
            List.of(new CrearPedidoComando.LineaComando(variante.id(), 1)),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.NEQUI,
            false,
            IP);

    assertThrows(AutorizacionRequeridaException.class, () -> caso.ejecutar(sinAutorizar));

    assertEquals(
        5, inventarios.buscarPorVarianteId(variante.id()).orElseThrow().saldoDisponible(AHORA));
    assertTrue(pedidos.todos().isEmpty());
    assertTrue(autorizaciones.todas().isEmpty());
  }
}
