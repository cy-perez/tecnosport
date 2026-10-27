package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.ExistenciaInsuficienteException;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EnlaceDeCompra;
import co.tecnosport.api.domain.pedido.EnlaceDeCompraNoVigenteException;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReintentarPagoTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T12:00:00Z");
  private static final Duration RESERVA_PAGO_EN_LINEA = Duration.ofMinutes(30);
  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private RepositorioPedidosFalso pedidos;
  private RepositorioProductosFalso productos;
  private final java.util.Map<UUID, Producto> porVariante = new java.util.LinkedHashMap<>();
  private RepositorioInventarioFalso inventarios;
  private UUID varianteId;
  private ModalidadesDeEntrega modalidadesDeEntrega = new ModalidadesDeEntrega(true);
  private RepositorioProveedoresFalso proveedores;
  private RepositorioEnlacesDeCompraFalso enlaces;

  private ReintentarPago crear() {
    enlaces = new RepositorioEnlacesDeCompraFalso();
    pedidos = new RepositorioPedidosFalso();
    productos = new RepositorioProductosFalso();
    inventarios = new RepositorioInventarioFalso();
    proveedores = new RepositorioProveedoresFalso();
    return new ReintentarPago(
        pedidos,
        productos,
        inventarios,
        new RelojFalso(AHORA),
        RESERVA_PAGO_EN_LINEA,
        new RecogidaEnPunto(modalidadesDeEntrega, productos, proveedores),
        enlaces);
  }

  /** Un producto de proveedor publicado con esa variante; agotado por el proveedor si se pide. */
  private Producto seVende(UUID varianteId, boolean agotadoPorElProveedor) {
    return seVende(varianteId, agotadoPorElProveedor, UUID.randomUUID());
  }

  private Producto seVende(UUID varianteId, boolean agotadoPorElProveedor, UUID proveedorId) {
    return seVende(varianteId, agotadoPorElProveedor, proveedorId, false, false);
  }

  private Producto seVende(
      UUID varianteId,
      boolean agotadoPorElProveedor,
      UUID proveedorId,
      boolean varianteAgotada,
      boolean disponibilidadPorConfirmar) {
    Producto producto =
        Producto.crearDeProveedor(
            "Samsung Galaxy A17 5G",
            new Slug("a17-" + varianteId),
            "Celular 5G.",
            Marca.crear("Samsung"),
            Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA),
            proveedorId,
            Dinero.deCop(675_000),
            HuellaProveedor.deModelo(proveedorId, "samsung-galaxy-a17-5g"),
            AHORA);
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://cdn.tecnosport.co/a17.avif", 1000)),
            null,
            800,
            new HashContenido("%064x".formatted(17)),
            "alt es",
            "alt en"));
    producto.agregarVariante(
        new Variante(
            varianteId,
            new Sku("TEC-" + varianteId.toString().substring(0, 8)),
            Dinero.deCop(849_900),
            BigDecimal.ZERO,
            null,
            null,
            EstadoVariante.ACTIVA,
            List.of(),
            null,
            varianteAgotada,
            disponibilidadPorConfirmar));
    producto.publicar();
    if (agotadoPorElProveedor) {
      producto.marcarAgotadoPorProveedor(AHORA.plusSeconds(60));
    }
    porVariante.put(varianteId, producto);
    productos.conProductos(porVariante.values().toArray(Producto[]::new));
    return producto;
  }

  private Pedido pedidoFallidoConInventario(int existencia) {
    varianteId = UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    if (existencia > 0) {
      inventario.registrarEntrada(existencia, "siembra de prueba", AHORA);
    }
    inventarios.conInventario(inventario);
    seVende(varianteId, false);

    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
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
            AHORA);
    pedido.transicionar(EstadoPedido.PAGO_FALLIDO, "webhook-wompi", "pago rechazado", AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void unPedidoConPagoFallidoVuelveAPagoPendienteConUnaReservaNueva() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    UUID reservaOriginal = pedido.lineas().get(0).idReserva();

    Pedido reintentado =
        caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co"));

    assertEquals(EstadoPedido.PAGO_PENDIENTE, reintentado.estado());
    assertEquals(3, reintentado.historial().size());
    assertEquals("cliente@tecnosport.co", reintentado.historial().get(2).actor());
    assertNotEquals(reservaOriginal, reintentado.lineas().get(0).idReserva());
  }

  @Test
  void reintentarReservaDeNuevoLaExistenciaDisponible() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);

    caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co"));

    Inventario inventario = inventarios.buscarPorVarianteId(varianteId).orElseThrow();
    assertEquals(4, inventario.saldoDisponible(AHORA));
  }

  @Test
  void reintentarSinExistenciaSuficienteLanzaExistenciaInsuficiente() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(0);

    assertThrows(
        ExistenciaInsuficienteException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co")));
  }

  @Test
  void unPedidoInexistenteLanzaPedidoNoEncontrado() {
    ReintentarPago caso = crear();

    assertThrows(
        PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(UUID.randomUUID(), "cliente@tecnosport.co")));
  }

  /**
   * El correo autoriza, igual que en el seguimiento. Sin esto, cualquiera con el id del pedido —que
   * viaja en la URL de retorno de la pasarela y en el correo de confirmación— podía volver a
   * reservar inventario y leerse el pedido entero: correo, teléfono, dirección e historial.
   *
   * <p>Y la respuesta es 404 y no 403 a propósito: un 403 confirmaría que ese id corresponde a una
   * compra real, que es justo lo que no hay que confirmarle a quien prueba identificadores.
   */
  @Test
  void unCorreoQueNoEsElDelPedidoSeTrataComoSiElPedidoNoExistiera() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    UUID reservaOriginal = pedido.lineas().get(0).idReserva();

    assertThrows(
        PedidoNoEncontradoException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "otro@tecnosport.co")));

    // Y no tocó nada: ni el estado, ni la reserva.
    assertEquals(EstadoPedido.PAGO_FALLIDO, pedido.estado());
    assertEquals(reservaOriginal, pedido.lineas().get(0).idReserva());
  }

  /** El correo se normaliza igual que en el seguimiento: espacios y mayúsculas no descalifican. */
  @Test
  void elCorreoAutorizaAunqueVengaConEspaciosYEnMayusculas() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);

    Pedido reintentado =
        caso.ejecutar(new ReintentarPagoComando(pedido.id(), "  CLIENTE@TecnoSport.CO  "));

    assertEquals(EstadoPedido.PAGO_PENDIENTE, reintentado.estado());
  }

  @Test
  void unPedidoQueNoEstaEnPagoFallidoNoSePuedeReintentarNiReservaNada() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    pedido.transicionar(
        EstadoPedido.PAGO_PENDIENTE, "cliente@tecnosport.co", "ya reintentado", AHORA);
    pedidos.guardar(pedido);

    assertThrows(
        TransicionDeEstadoInvalidaException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co")));

    // La transición inválida se detecta antes de reservar: el intento fallido no dejó ninguna
    // reserva nueva, el disponible sigue en la existencia completa.
    Inventario inventario = inventarios.buscarPorVarianteId(varianteId).orElseThrow();
    assertEquals(5, inventario.saldoDisponible(AHORA));
  }

  /**
   * Los bloqueos en orden de variante, como CrearPedido: en el orden de las líneas, dos reintentos
   * con las mismas variantes al revés se interbloqueaban. Lo que importa es que sea el mismo orden
   * que CrearPedido —{@code UUID.compareTo}, que compara con signo—, así que lo esperado se calcula
   * con él y las líneas van en el orden contrario.
   */
  @Test
  void reservaEnOrdenDeVarianteYNoEnElDeLasLineas() {
    ReintentarPago caso = crear();
    UUID a = UUID.fromString("00000000-0000-7000-8000-000000000000");
    UUID b = UUID.fromString("ffffffff-0000-7000-8000-000000000000");
    List<UUID> enOrdenDeVariante = java.util.stream.Stream.of(a, b).sorted().toList();
    UUID primeraEnLineas = enOrdenDeVariante.get(1);
    UUID segundaEnLineas = enOrdenDeVariante.get(0);
    for (UUID variante : enOrdenDeVariante) {
      Inventario inventario = Inventario.crear(variante);
      inventario.registrarEntrada(5, "siembra de prueba", AHORA);
      inventarios.conInventario(inventario);
      seVende(variante, false);
    }
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 2),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(linea(primeraEnLineas), linea(segundaEnLineas)),
            TipoEntrega.ENVIO_A_DOMICILIO,
            DIRECCION_MEDELLIN,
            MetodoPago.WOMPI,
            "cliente@tecnosport.co",
            AHORA);
    pedido.transicionar(EstadoPedido.PAGO_FALLIDO, "webhook-wompi", "pago rechazado", AHORA);
    pedidos.guardar(pedido);

    caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co"));

    assertEquals(enOrdenDeVariante, inventarios.ordenDeConsultas());
  }

  /**
   * Un modelo que el proveedor agotó sigue con existencia en el libro —agotar no toca el
   * inventario—: el reintento no lo reserva ni lo deja listo para cobrar.
   */
  @Test
  void loQueElProveedorAgotoNoSeVuelveACobrar() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    seVende(varianteId, true);

    assertThrows(
        VarianteNoEncontradaException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co")));

    assertEquals(
        EstadoPedido.PAGO_FALLIDO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
    assertEquals(
        5, inventarios.buscarPorVarianteId(varianteId).orElseThrow().saldoDisponible(AHORA));
  }

  /**
   * Un pedido de recogida que falló antes de que la recogida se apagara: reintentarlo hoy sería
   * cobrar una recogida que los términos vigentes ya no ofrecen, y no reserva nada antes de
   * negarse.
   */
  @Test
  void conLaRecogidaApagadaUnRetiroFallidoNoSeReintentaNiReserva() {
    modalidadesDeEntrega = new ModalidadesDeEntrega(false);
    ReintentarPago caso = crear();
    UUID variante = UUID.randomUUID();
    Inventario inventario = Inventario.crear(variante);
    inventario.registrarEntrada(5, "siembra de prueba", AHORA);
    inventarios.conInventario(inventario);
    seVende(variante, false);
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 3),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(linea(variante)),
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.WOMPI,
            "cliente@tecnosport.co",
            AHORA);
    pedido.transicionar(EstadoPedido.PAGO_FALLIDO, "webhook-wompi", "pago rechazado", AHORA);
    pedidos.guardar(pedido);

    org.junit.jupiter.api.Assertions.assertThrows(
        RetiroEnPuntoNoDisponibleException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co")));

    assertEquals(
        EstadoPedido.PAGO_FALLIDO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
    assertEquals(5, inventarios.buscarPorVarianteId(variante).orElseThrow().saldoDisponible(AHORA));
  }

  /** Un pedido de recogida fallido con una variante de ese proveedor y cinco unidades. */
  private Pedido retiroFallido(UUID proveedorId) {
    UUID variante = UUID.randomUUID();
    varianteId = variante;
    Inventario inventario = Inventario.crear(variante);
    inventario.registrarEntrada(5, "siembra de prueba", AHORA);
    inventarios.conInventario(inventario);
    seVende(variante, false, proveedorId);
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 4),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(linea(variante)),
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.WOMPI,
            "cliente@tecnosport.co",
            AHORA);
    pedido.transicionar(EstadoPedido.PAGO_FALLIDO, "webhook-wompi", "pago rechazado", AHORA);
    pedidos.guardar(pedido);
    return pedido;
  }

  /** Lo de un proveedor local se sigue pudiendo recoger: el reintento reserva y vuelve a cobrar. */
  @Test
  void unRetiroDeProveedorLocalSeReintenta() {
    ReintentarPago caso = crear();
    Proveedor violeta =
        Proveedor.crear(
            "Violeta",
            LineaCatalogo.ROPA,
            "+57 300",
            "Violeta",
            null,
            OrdenDePublicacion.TEXTO_PRIMERO);
    violeta.definirLocal(true);
    proveedores.conProveedores(violeta);
    Pedido pedido = retiroFallido(violeta.id());

    caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co"));

    assertEquals(
        EstadoPedido.PAGO_PENDIENTE, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
  }

  /**
   * Un retiro de antes del 26/10/2026 con algo que no es de un proveedor local —un celular del
   * mayorista— no se vuelve a cobrar como recogida, y no reserva nada antes de negarse.
   */
  @Test
  void unRetiroDeProveedorNoLocalNoSeReintentaNiReserva() {
    ReintentarPago caso = crear();
    Pedido pedido = retiroFallido(UUID.randomUUID());

    org.junit.jupiter.api.Assertions.assertThrows(
        RetiroEnPuntoNoDisponibleException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co")));

    assertEquals(
        EstadoPedido.PAGO_FALLIDO, pedidos.buscarPorId(pedido.id()).orElseThrow().estado());
    assertEquals(
        5, inventarios.buscarPorVarianteId(varianteId).orElseThrow().saldoDisponible(AHORA));
  }

  /**
   * Agotar la variante —una talla, un color— tampoco toca el libro. {@code CrearPedido} ya la
   * rechazaba; el reintento la volvía a reservar y a cobrar.
   */
  @Test
  void unaVarianteQueElProveedorAgotoNoSeVuelveACobrar() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    seVende(varianteId, false, UUID.randomUUID(), true, false);

    assertThrows(
        VarianteNoEncontradaException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co")));

    assertEquals(EstadoPedido.PAGO_FALLIDO, pedido.estado());
    assertEquals(
        5, inventarios.buscarPorVarianteId(varianteId).orElseThrow().saldoDisponible(AHORA));
  }

  /** El enlace con que se compró, usado en este pedido y emitido {@code horasAntes} atrás. */
  private void compradoConEnlace(Pedido pedido, long horasAntes) {
    Instant emitido = AHORA.minus(Duration.ofHours(horasAntes));
    EnlaceDeCompra enlace =
        EnlaceDeCompra.generar(varianteId, UUID.randomUUID(), Duration.ofHours(24), emitido);
    enlace.usar(pedido.id(), emitido.plusSeconds(60));
    enlaces.guardar(enlace);
  }

  /**
   * Lo que el proveedor confirmó vale lo que dura el enlace. Dentro de su plazo se reintenta, y la
   * unidad queda apartada hasta que el enlace habría vencido, no los 30 minutos del pago en línea.
   */
  @Test
  void unaVariantePorConfirmarSeReintentaMientrasSuEnlaceSigaEnPlazo() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    seVende(varianteId, false, UUID.randomUUID(), false, true);
    compradoConEnlace(pedido, 2);

    Pedido reintentado =
        caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co"));

    assertEquals(EstadoPedido.PAGO_PENDIENTE, reintentado.estado());
    Inventario inventario = inventarios.buscarPorVarianteId(varianteId).orElseThrow();
    assertEquals(4, inventario.saldoDisponible(AHORA.plus(Duration.ofHours(21))));
  }

  /**
   * Decisión del 27/10/2026: con el enlace vencido no se reintenta; quien compra pide otro por
   * WhatsApp. La misma excepción que {@code CrearPedido} con un enlace vencido.
   */
  @Test
  void unaVariantePorConfirmarConElEnlaceVencidoNoSeReintentaNiReserva() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    seVende(varianteId, false, UUID.randomUUID(), false, true);
    compradoConEnlace(pedido, 25);

    assertThrows(
        EnlaceDeCompraNoVigenteException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co")));

    assertEquals(EstadoPedido.PAGO_FALLIDO, pedido.estado());
    assertEquals(
        5, inventarios.buscarPorVarianteId(varianteId).orElseThrow().saldoDisponible(AHORA));
  }

  /** Pasó a «por confirmar» después de comprarse, sin enlace: lo mismo que crear el pedido. */
  @Test
  void unaVariantePorConfirmarSinEnlaceNoSeReintenta() {
    ReintentarPago caso = crear();
    Pedido pedido = pedidoFallidoConInventario(5);
    seVende(varianteId, false, UUID.randomUUID(), false, true);

    assertThrows(
        DisponibilidadPorConfirmarException.class,
        () -> caso.ejecutar(new ReintentarPagoComando(pedido.id(), "cliente@tecnosport.co")));

    assertEquals(EstadoPedido.PAGO_FALLIDO, pedido.estado());
  }

  private static LineaPedido linea(UUID variante) {
    return new LineaPedido(
        UUID.randomUUID(),
        variante,
        new Sku("TS-CAM-AZ-M"),
        "Camiseta",
        1,
        Dinero.deCop(50_000),
        BigDecimal.ZERO,
        null,
        UUID.randomUUID());
  }
}
