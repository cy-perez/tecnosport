package co.tecnosport.api.application.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.LimitadorDeIntentosFalso;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.envio.ArmadorDeBultos;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.CotizarEnvio;
import co.tecnosport.api.application.envio.EnvioSinCoberturaException;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.ResultadoCotizacion;
import co.tecnosport.api.application.legal.RepositorioAutorizacionesFalso;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.inventario.ExistenciaInsuficienteException;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.inventario.MovimientoInventario;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.legal.AutorizacionRequeridaException;
import co.tecnosport.api.domain.legal.OrigenAutorizacion;
import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.LineasDuplicadasException;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CrearPedidoTest {

  private static final Instant AHORA = Instant.parse("2026-09-02T12:00:00Z");
  private static final Duration RESERVA_PAGO_EN_LINEA = Duration.ofMinutes(30);
  private static final Duration RESERVA_TRANSFERENCIA = Duration.ofHours(24);

  private static final Direccion DIRECCION_MEDELLIN =
      Direccion.sinBarrio("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  private static final CriteriosContraentrega CRITERIOS_CONTRAENTREGA_PERMISIVOS =
      new CriteriosContraentrega(true, Dinero.deCop(1), Dinero.deCop(10_000_000), Set.of());

  private static final int MAXIMO_INTENTOS_POR_CUENTA = 5;
  private static final Duration VENTANA_INTENTOS_POR_CUENTA = Duration.ofMinutes(60);

  private RepositorioProductosFalso productos;
  private RepositorioInventarioFalso inventarios;
  private RepositorioPedidosFalso pedidos;
  private LimitadorDeIntentosFalso limitadorDeIntentos;
  private RepositorioAutorizacionesFalso autorizaciones;
  private CotizadorEnvioFalso cotizador;

  /** Lo que devuelve el cotizador en todas las pruebas que no digan otra cosa. */
  private static final TarifaEnvio TARIFA =
      new TarifaEnvio(
          "rate_1",
          "Coordinadora",
          "Standard",
          Dinero.deCop(14_900),
          2,
          false,
          Instant.parse("2026-09-03T12:00:00Z"));

  private static final String VERSION_POLITICA = "2026-09-07";
  private static final String IP = "190.24.10.5";
  private static final Contacto CONTACTO = new Contacto("Ana Pérez", "3138816711");

  private Variante variante;
  private Producto primerProducto;

  private CrearPedido crear() {
    return crear(CRITERIOS_CONTRAENTREGA_PERMISIVOS, true);
  }

  /**
   * Los mismos que el valor por omisión de {@code application.yml}: Addi fuera hasta que Wompi lo
   * active (docs/11-pagos-y-envios.md).
   */
  private static final Set<MetodoPago> HABILITADOS_EN_PASARELA =
      EnumSet.of(MetodoPago.TARJETA, MetodoPago.PSE, MetodoPago.NEQUI, MetodoPago.BANCOLOMBIA);

  /**
   * Valor de prueba, NO el dato real: el mínimo del crédito lo define Sistecrédito y todavía no lo
   * tenemos (adr/0048). Lo que estas pruebas comprueban es que el corte exista y se aplique, no
   * cuánto vale.
   */
  private static final Dinero MONTO_MINIMO_SISTECREDITO = Dinero.deCop(30_000);

  private CrearPedido crear(CriteriosContraentrega criterios, boolean recaudaEnElDestino) {
    productos = new RepositorioProductosFalso();
    inventarios = new RepositorioInventarioFalso();
    pedidos = new RepositorioPedidosFalso();
    limitadorDeIntentos = new LimitadorDeIntentosFalso();
    autorizaciones = new RepositorioAutorizacionesFalso();
    cotizador = new CotizadorEnvioFalso();
    cotizador.conTarifas(TARIFA);
    cotizador.recaudaEnElDestino(recaudaEnElDestino);
    CotizarEnvio cotizarEnvio =
        new CotizarEnvio(
            new ArmadorDeBultos(productos, Dinero.deCop(10_000), Dinero.deCop(5_000_000)),
            cotizador,
            () -> AHORA);
    MetodosDePagoDisponibles metodosDePagoDisponibles =
        new MetodosDePagoDisponibles(
            productos,
            new ArmadorDeBultos(productos, Dinero.deCop(10_000), Dinero.deCop(5_000_000)),
            cotizarEnvio,
            pedidos,
            criterios,
            HABILITADOS_EN_PASARELA,
            MONTO_MINIMO_SISTECREDITO);
    return new CrearPedido(
        productos,
        inventarios,
        pedidos,
        metodosDePagoDisponibles,
        cotizarEnvio,
        new RelojFalso(AHORA),
        RESERVA_PAGO_EN_LINEA,
        RESERVA_TRANSFERENCIA,
        limitadorDeIntentos,
        MAXIMO_INTENTOS_POR_CUENTA,
        VENTANA_INTENTOS_POR_CUENTA,
        autorizaciones,
        VERSION_POLITICA);
  }

  /**
   * Un segundo producto publicado, con su propia variante y su propio libro. Hace falta para las
   * pruebas del orden de los bloqueos, que necesitan dos variantes en el mismo pedido.
   */
  private Variante publicarSegundaVarianteConExistencia(int existencia) {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria =
        Categoria.crear("Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO);
    Producto producto =
        Producto.crear(
            "Pantaloneta running",
            new Slug("pantaloneta-running"),
            "Descripción",
            marca,
            categoria);
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            "https://cdn.tecnosport.co/img2.jpg",
            "https://cdn.tecnosport.co/img2.webp",
            800,
            600,
            1000,
            new HashContenido("%064x".formatted(2)),
            "alt es",
            "alt en"));
    Variante segunda =
        Variante.crear(
            new Sku("TS-PAN-NE-M"),
            Dinero.deCop(40_000),
            new BigDecimal("0.19"),
            null,
            new Paquete(150, 28, 22, 3),
            List.of());
    producto.agregarVariante(segunda);
    producto.publicar();
    // `conProductos` reemplaza la lista entera: hay que volver a pasar el primero.
    productos.conProductos(primerProducto, producto);

    Inventario inventario = Inventario.crear(segunda.id());
    inventario.registrarEntrada(existencia, "siembra de prueba", AHORA);
    inventarios.conInventario(inventario);
    return segunda;
  }

  private CrearPedidoComando comandoCon(List<CrearPedidoComando.LineaComando> lineas) {
    return new CrearPedidoComando(
        null,
        "cliente@tecnosport.co",
        CONTACTO,
        lineas,
        TipoEntrega.ENVIO_A_DOMICILIO,
        DIRECCION_MEDELLIN,
        MetodoPago.NEQUI,
        true,
        IP);
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
            null,
            new Paquete(180, 30, 25, 4),
            List.of());
    producto.agregarVariante(variante);
    producto.publicar();
    primerProducto = producto;
    productos.conProductos(producto);

    Inventario inventario = Inventario.crear(variante.id());
    inventario.registrarEntrada(existencia, "siembra de prueba", AHORA);
    inventarios.conInventario(inventario);
  }

  private CrearPedidoComando comandoRetiroEnPunto(MetodoPago metodoPago, int cantidad) {
    return new CrearPedidoComando(
        null,
        "cliente@tecnosport.co",
        CONTACTO,
        List.of(new CrearPedidoComando.LineaComando(variante.id(), cantidad)),
        TipoEntrega.RETIRO_EN_PUNTO,
        null,
        metodoPago,
        true,
        IP);
  }

  private CrearPedidoComando comando(MetodoPago metodoPago, int cantidad) {
    return new CrearPedidoComando(
        null,
        "cliente@tecnosport.co",
        CONTACTO,
        List.of(new CrearPedidoComando.LineaComando(variante.id(), cantidad)),
        TipoEntrega.ENVIO_A_DOMICILIO,
        DIRECCION_MEDELLIN,
        metodoPago,
        true,
        IP);
  }

  /**
   * Sin nombre ni teléfono no hay guía que emitir ni mensajero que avise. La exigencia vive aquí y
   * no en el agregado, que también reconstruye los pedidos anteriores a este campo.
   */
  @Test
  void sinContactoNoCreaElPedidoNiReservaNada() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);
    CrearPedidoComando sinContacto =
        new CrearPedidoComando(
            null,
            "cliente@tecnosport.co",
            null,
            List.of(new CrearPedidoComando.LineaComando(variante.id(), 1)),
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.NEQUI,
            true,
            IP);

    assertThrows(ExcepcionDeDominio.class, () -> caso.ejecutar(sinContacto));

    assertEquals(
        5, inventarios.buscarPorVarianteId(variante.id()).orElseThrow().saldoDisponible(AHORA));
    assertTrue(pedidos.todos().isEmpty());
  }

  @Test
  void elPedidoCreadoConservaElContacto() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    Pedido pedido = caso.ejecutar(comando(MetodoPago.NEQUI, 1));

    assertEquals(CONTACTO, pedido.contacto().orElseThrow());
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
    assertEquals(Dinero.deCop(100_000), pedido.subtotal());
    assertEquals(Dinero.deCop(114_900), pedido.total());
  }

  /**
   * El costo del envío lo fija el servidor cotizando otra vez al confirmar, y el pedido congela la
   * tarifa: una cotización vive 24 horas y el pedido vive para siempre (adr/0021).
   */
  @Test
  void congelaLaTarifaConLaQueCotizoAlConfirmar() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    Pedido pedido = caso.ejecutar(comando(MetodoPago.NEQUI, 2));

    assertEquals(TARIFA, pedido.tarifaEnvio().orElseThrow());
    assertEquals(Dinero.deCop(14_900), pedido.costoEnvio());
  }

  /**
   * Sin tarifa no hay envío a domicilio. Lo que importa aquí no es solo que falle, sino
   * <strong>dónde</strong>: la cotización va antes de reservar, así que un destino sin cobertura no
   * deja existencias comprometidas ni quema un número de pedido.
   */
  @Test
  void sinCoberturaNoSeCreaElPedidoNiSeTocaElInventario() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);
    cotizador.sinTarifas();

    assertThrows(
        EnvioSinCoberturaException.class, () -> caso.ejecutar(comando(MetodoPago.NEQUI, 2)));

    assertEquals(
        5, inventarios.buscarPorVarianteId(variante.id()).orElseThrow().saldoDisponible(AHORA));
    assertTrue(pedidos.todos().isEmpty());
  }

  /** El retiro en punto no cotiza: no hay a dónde despachar y el flete es cero por definición. */
  @Test
  void elRetiroEnPuntoNiCotizaNiCobraEnvio() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    Pedido pedido = caso.ejecutar(comandoRetiroEnPunto(MetodoPago.NEQUI, 2));

    assertEquals(0, cotizador.vecesLlamado());
    assertTrue(pedido.tarifaEnvio().isEmpty());
    assertEquals(Dinero.deCop(0), pedido.costoEnvio());
    assertEquals(pedido.subtotal(), pedido.total());
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  private static final class CotizadorEnvioFalso implements CotizadorEnvio {

    private List<TarifaEnvio> tarifas = List.of();
    private ResultadoCotizacion.Motivo falla;
    private boolean recauda = true;
    private int veces;

    void conTarifas(TarifaEnvio... tarifas) {
      this.tarifas = List.of(tarifas);
    }

    void sinTarifas() {
      this.tarifas = List.of();
    }

    void recaudaEnElDestino(boolean recauda) {
      this.recauda = recauda;
    }

    int vecesLlamado() {
      return veces;
    }

    /**
     * Imita a Skydropx: en una cotización pedida con recaudo solo responden las transportadoras que
     * lo admiten, y toda tarifa que sobrevive queda marcada como que recauda. Si ninguna lo admite,
     * la cotización vuelve vacía — que es distinto de no tener envío.
     */
    @Override
    public ResultadoCotizacion cotizar(CotizacionEnvio cotizacion) {
      veces++;
      if (cotizacion.conRecaudo() && !recauda) {
        return respuesta(List.of());
      }
      return respuesta(tarifas.stream().map(t -> conRecaudo(t, cotizacion.conRecaudo())).toList());
    }

    /** El proveedor no respondio, o la cotizacion no completo: no sabemos si hay cobertura. */
    void fallar(ResultadoCotizacion.Motivo motivo) {
      this.falla = motivo;
    }

    /**
     * Lista vacia es "sin cobertura" y no un fallo: el proveedor respondio. Los fallos se piden
     * aparte, con {@link #falla}, porque desde el 16 de septiembre de 2026 el puerto los distingue
     * y al comprador se le dice otra cosa (docs/13 6.9).
     */
    private ResultadoCotizacion respuesta(List<TarifaEnvio> tarifas) {
      if (falla != null) {
        return new ResultadoCotizacion.NoSePudoCotizar(falla);
      }
      return tarifas.isEmpty()
          ? new ResultadoCotizacion.SinCobertura()
          : new ResultadoCotizacion.ConTarifas(tarifas);
    }

    private static TarifaEnvio conRecaudo(TarifaEnvio tarifa, boolean admite) {
      return new TarifaEnvio(
          tarifa.idTarifa(),
          tarifa.transportadora(),
          tarifa.servicio(),
          tarifa.costo(),
          tarifa.diasEstimados(),
          admite,
          tarifa.venceEn());
    }
  }

  /**
   * El orden de los bloqueos no lo elige quien postea. Dos compradores con las mismas variantes en
   * distinto orden se bloqueaban en cruz y Postgres abortaba uno con `40P01`, que el comprador veía
   * como un 500. Aquí se comprueba con una sola petición lo que allí pasaba con dos: que la
   * secuencia de libros que se piden sea la del `varianteId` y no la del cuerpo.
   */
  @Test
  void tomaLosBloqueosEnOrdenDeVarianteIdYNoEnElQueMandaElCliente() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);
    Variante otra = publicarSegundaVarianteConExistencia(5);

    List<UUID> esperado = Stream.of(variante.id(), otra.id()).sorted().toList();
    // El cuerpo llega justo al revés del orden canónico.
    List<CrearPedidoComando.LineaComando> alReves =
        esperado.reversed().stream().map(id -> new CrearPedidoComando.LineaComando(id, 1)).toList();

    caso.ejecutar(comandoCon(alReves));

    assertEquals(esperado, inventarios.ordenDeConsultas());
  }

  @Test
  void elPedidoConservaElOrdenDeLineasDelComprador() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);
    Variante otra = publicarSegundaVarianteConExistencia(5);

    List<UUID> comoLasMando = Stream.of(variante.id(), otra.id()).sorted().toList().reversed();

    Pedido pedido =
        caso.ejecutar(
            comandoCon(
                comoLasMando.stream()
                    .map(id -> new CrearPedidoComando.LineaComando(id, 1))
                    .toList()));

    assertEquals(comoLasMando, pedido.lineas().stream().map(LineaPedido::varianteId).toList());
  }

  /**
   * Y no se reserva nada al rechazarlo: la guarda va antes de tocar el inventario, como la de la
   * autorización de datos. Reservar y fallar después dejaría existencias comprometidas para un
   * pedido que no va a existir.
   */
  @Test
  void rechazaDosLineasDeLaMismaVarianteSinReservarNada() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    CrearPedidoComando comando =
        comandoCon(
            List.of(
                new CrearPedidoComando.LineaComando(variante.id(), 1),
                new CrearPedidoComando.LineaComando(variante.id(), 1)));

    assertThrows(LineasDuplicadasException.class, () -> caso.ejecutar(comando));
    assertEquals(0, inventarios.consultasConBloqueo());
    assertEquals(5, inventarios.buscarPorVarianteId(variante.id()).orElseThrow().saldoTotal());
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

  /**
   * Regla dura #7: el servidor no se fía de que el cliente haya consultado la lista. Sin esta
   * comprobación, un {@code POST} con {@code ADDI} creaba el pedido igual —solo contraentrega se
   * revalidaba— y el comprador acababa en un Web Checkout donde Addi no aparece, pagando con
   * tarjeta un pedido que dice Addi.
   */
  @Test
  void seRechazaUnMetodoDePagoQueLaPasarelaNoTieneHabilitado() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    assertThrows(
        MetodoDePagoNoHabilitadoException.class, () -> caso.ejecutar(comando(MetodoPago.ADDI, 1)));
  }

  /** Y no reserva inventario al rechazarlo: la comprobación va antes de congelar las líneas. */
  @Test
  void unMetodoNoHabilitadoNoReservaInventario() {
    CrearPedido caso = crear();
    publicarProductoConVarianteYExistencia(5);

    assertThrows(
        MetodoDePagoNoHabilitadoException.class, () -> caso.ejecutar(comando(MetodoPago.ADDI, 1)));

    assertEquals(
        5, inventarios.buscarPorVarianteId(variante.id()).orElseThrow().saldoDisponible(AHORA));
  }

  /** Ninguna transportadora cobra en la puerta en ese destino (adr/0023). */
  @Test
  void contraentregaSeRechazaSiNingunaTarifaRecauda() {
    CrearPedido caso = crear(CRITERIOS_CONTRAENTREGA_PERMISIVOS, false);
    publicarProductoConVarianteYExistencia(5);

    assertThrows(
        ContraentregaNoDisponibleException.class,
        () -> caso.ejecutar(comando(MetodoPago.CONTRAENTREGA, 1)));
  }

  @Test
  void contraentregaSeRechazaSiEstaDeshabilitadaGlobalmente() {
    CriteriosContraentrega deshabilitada =
        new CriteriosContraentrega(false, Dinero.deCop(1), Dinero.deCop(10_000_000), Set.of());
    CrearPedido caso = crear(deshabilitada, true);
    publicarProductoConVarianteYExistencia(5);

    assertThrows(
        ContraentregaNoDisponibleException.class,
        () -> caso.ejecutar(comando(MetodoPago.CONTRAENTREGA, 1)));
  }

  @Test
  void contraentregaSeRechazaSiElTotalSuperaElMontoMaximo() {
    CriteriosContraentrega montoBajo =
        new CriteriosContraentrega(true, Dinero.deCop(1), Dinero.deCop(10_000), Set.of());
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
            true, Dinero.deCop(1), Dinero.deCop(10_000_000), Set.of(LineaCatalogo.ROPA_Y_CALZADO));
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
            CONTACTO,
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
            CONTACTO,
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
            null,
            new Paquete(180, 30, 25, 4),
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
            CONTACTO,
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
            CONTACTO,
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
            CONTACTO,
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
