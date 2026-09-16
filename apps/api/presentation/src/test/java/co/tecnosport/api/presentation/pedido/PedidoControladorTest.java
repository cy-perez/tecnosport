package co.tecnosport.api.presentation.pedido;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.CotizarEnvio;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.RepositorioEnvios;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.application.pedido.ConsultarSeguimientoPedido;
import co.tecnosport.api.application.pedido.CrearPedido;
import co.tecnosport.api.application.pedido.ReintentarPago;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.application.reintegro.RepositorioReintegros;
import co.tecnosport.api.application.reintegro.TopeDeReintegro;
import co.tecnosport.api.application.retracto.RepositorioSolicitudesRetracto;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.envio.Envio;
import co.tecnosport.api.domain.envio.GuiaEnvio;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.EstadoPedido;
import co.tecnosport.api.domain.pedido.LineaPedido;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.Pedido;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import co.tecnosport.api.presentation.compartido.RepositorioSolicitudesReversionVacio;
import co.tecnosport.api.presentation.pedido.dto.CrearPedidoRequest;
import co.tecnosport.api.presentation.pedido.dto.MetodosDePagoDisponiblesRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

@WebMvcTest(PedidoControlador.class)
@Import(PedidoControladorTest.Configuracion.class)
class PedidoControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioEnviosDobleDePrueba envios;
  @Autowired private RepositorioProductosDobleDePrueba productos;
  @Autowired private RepositorioInventarioDobleDePrueba inventarios;
  @Autowired private RepositorioPedidosDobleDePrueba pedidos;
  @Autowired private LimitadorDeIntentosDobleDePrueba limitadorDeIntentos;

  @BeforeEach
  void reiniciarLimitadorDeIntentos() {
    // Bean compartido por todo el contexto de @WebMvcTest: sin esto, denegarSiempre() de una
    // prueba contaminaría a las que corran después en la misma clase.
    limitadorDeIntentos.reiniciar();
  }

  private final ObjectMapper json = new ObjectMapper();

  private static final CrearPedidoRequest.DireccionRequest DIRECCION_MEDELLIN =
      new CrearPedidoRequest.DireccionRequest(
          "05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

  // Sin cobertura en el doble de prueba (que solo cubre "05001"): sirve para probar el camino sin
  // contraentrega disponible.
  private static final CrearPedidoRequest.DireccionRequest DIRECCION_BOGOTA =
      new CrearPedidoRequest.DireccionRequest(
          "11", "Bogotá D.C.", "11001", "Bogotá", "Cra. 7 #12-34", null);

  private Variante publicarProductoConVarianteYExistencia(int existencia) {
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
    Variante variante =
        Variante.crear(
            new Sku("TS-CAM-AZ-M"),
            Dinero.deCop(50_000),
            new BigDecimal("0.19"),
            0,
            null,
            new Paquete(180, 30, 25, 4),
            List.of());
    producto.agregarVariante(variante);
    producto.publicar();
    productos.conProductos(producto);

    Inventario inventario = Inventario.crear(variante.id());
    inventario.registrarEntrada(existencia, "siembra de prueba", Instant.now());
    inventarios.conInventario(inventario);
    return variante;
  }

  private CrearPedidoRequest solicitud(
      Variante variante,
      String tipoEntrega,
      CrearPedidoRequest.DireccionRequest direccion,
      String metodoPago) {
    return new CrearPedidoRequest(
        "cliente@tecnosport.co",
        "Ana Pérez",
        "313 881 6711",
        List.of(new CrearPedidoRequest.LineaRequest(variante.id(), 2)),
        tipoEntrega,
        direccion,
        metodoPago,
        true);
  }

  @Test
  void crearPedidoDevuelveElPedidoCreadoEnPagoPendiente() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo =
        solicitud(variante, "ENVIO_A_DOMICILIO", DIRECCION_MEDELLIN, "NEQUI");

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.numeroPedido").value(matchesPattern("TS-\\d{4}-\\d{6}")))
        .andExpect(jsonPath("$.estado").value("PAGO_PENDIENTE"))
        .andExpect(jsonPath("$.metodoPago").value("NEQUI"))
        .andExpect(jsonPath("$.lineas[0].sku").value("TS-CAM-AZ-M"))
        .andExpect(jsonPath("$.subtotal.valor").value(100_000))
        .andExpect(jsonPath("$.costoEnvio.valor").value(14_900))
        .andExpect(jsonPath("$.total.valor").value(114_900))
        .andExpect(jsonPath("$.direccion.ciudad").value("Medellín"));
  }

  @Test
  void crearPedidoConLimiteDeIntentosExcedidoDevuelve429() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo =
        solicitud(variante, "ENVIO_A_DOMICILIO", DIRECCION_MEDELLIN, "NEQUI");
    limitadorDeIntentos.denegarSiempre();

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_INTENTOS_EXCEDIDO"));
  }

  @Test
  void crearPedidoConTransferenciaManualDevuelveLosDatosDeLaCuenta() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo =
        solicitud(variante, "ENVIO_A_DOMICILIO", DIRECCION_MEDELLIN, "TRANSFERENCIA_MANUAL");

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datosTransferencia.banco").value("Bancolombia"))
        .andExpect(jsonPath("$.datosTransferencia.tipoCuenta").value("ahorros"))
        .andExpect(jsonPath("$.datosTransferencia.numeroCuenta").value("123-456789-00"))
        .andExpect(jsonPath("$.datosTransferencia.titular").value("TecnoSport SAS"))
        .andExpect(
            jsonPath("$.datosTransferencia.referencia").value(matchesPattern("TS-\\d{4}-\\d{6}")));
  }

  @Test
  void crearPedidoConMetodoDistintoDeTransferenciaNoTraeDatosDeCuenta() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo =
        solicitud(variante, "ENVIO_A_DOMICILIO", DIRECCION_MEDELLIN, "NEQUI");

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datosTransferencia").isEmpty());
  }

  @Test
  void crearPedidoContraentregaQuedaConfirmadoSinPagoPendiente() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo =
        solicitud(variante, "ENVIO_A_DOMICILIO", DIRECCION_MEDELLIN, "CONTRAENTREGA");

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("CONFIRMADO_CONTRAENTREGA"))
        // Y con el plazo de entrega dentro, que es deliberado y no un descuido de reutilizar el DTO
        // del panel: en contraentrega el contrato queda celebrado aquí mismo, así que el plazo ya
        // arrancó, y decirle a quien compró hasta cuándo tenemos para entregarle es información
        // suya. Lo que no sale por aquí —costo real del flete, comisión de recaudo— se afirma en
        // `seguimientoNoExponeLosCostosInternos`.
        .andExpect(jsonPath("$.plazoDeEntrega.limite").exists())
        .andExpect(jsonPath("$.plazoDeEntrega.verdicto").value("EN_PLAZO"))
        .andExpect(jsonPath("$.plazoDeEntrega.avisadoEn").doesNotExist());
  }

  @Test
  void crearPedidoDeRetiroEnPuntoSinDireccion() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo = solicitud(variante, "RETIRO_EN_PUNTO", null, "TARJETA");

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.direccion").isEmpty());
  }

  @Test
  void crearPedidoSinCorreoDevuelve422() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"lineas":[{"varianteId":"%s","cantidad":1}],"tipoEntrega":"RETIRO_EN_PUNTO","metodoPago":"TARJETA"}
                    """
                        .formatted(variante.id())))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void crearPedidoConTipoEntregaInvalidoDevuelve422() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo = solicitud(variante, "TELETRANSPORTE", null, "TARJETA");

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isUnprocessableContent());
  }

  /**
   * La casilla del navegador es una comodidad; la regla es del servidor. El checkout recoge nombre,
   * dirección, teléfono y correo, así que aquí también hace falta autorización expresa (Ley 1581 de
   * 2012), no solo en el registro.
   */
  @Test
  void crearPedidoSinAutorizarElTratamientoDeDatosDevuelve422() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo =
        new CrearPedidoRequest(
            "cliente@tecnosport.co",
            "Ana Pérez",
            "313 881 6711",
            List.of(new CrearPedidoRequest.LineaRequest(variante.id(), 1)),
            "RETIRO_EN_PUNTO",
            null,
            "TARJETA",
            false);

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.codigo").value("AUTORIZACION_REQUERIDA"));
  }

  /** Sin quien reciba no hay guía ni mensajero: el cuerpo se rechaza antes de tocar nada. */
  @Test
  void crearPedidoSinTelefonoDevuelve422() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    String cuerpo =
        """
        {"correo":"cliente@tecnosport.co","nombre":"Ana Pérez","telefono":"",
         "lineas":[{"varianteId":"%s","cantidad":1}],
         "tipoEntrega":"RETIRO_EN_PUNTO","metodoPago":"TARJETA","autorizaDatos":true}
        """
            .formatted(variante.id());

    mockMvc
        .perform(post("/api/v1/pedidos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void elPedidoCreadoDevuelveElContactoNormalizado() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo = solicitud(variante, "RETIRO_EN_PUNTO", null, "TARJETA");

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contacto.nombre").value("Ana Pérez"))
        .andExpect(jsonPath("$.contacto.telefono").value("3138816711"));
  }

  @Test
  void crearPedidoConVarianteInexistenteDevuelve404() throws Exception {
    CrearPedidoRequest cuerpo =
        new CrearPedidoRequest(
            "cliente@tecnosport.co",
            "Ana Pérez",
            "313 881 6711",
            List.of(new CrearPedidoRequest.LineaRequest(java.util.UUID.randomUUID(), 1)),
            "RETIRO_EN_PUNTO",
            null,
            "TARJETA",
            true);

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("VARIANTE_NO_ENCONTRADA"));
  }

  @Test
  void crearPedidoConExistenciaInsuficienteDevuelve409() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(1);
    CrearPedidoRequest cuerpo = solicitud(variante, "RETIRO_EN_PUNTO", null, "TARJETA");

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("EXISTENCIA_INSUFICIENTE"));
  }

  @Test
  void crearPedidoContraentregaDevuelve409SiNadieRecaudaAhi() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo =
        solicitud(variante, "ENVIO_A_DOMICILIO", DIRECCION_BOGOTA, "CONTRAENTREGA");

    mockMvc
        .perform(
            post("/api/v1/pedidos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CONTRAENTREGA_NO_DISPONIBLE"));
  }

  @Test
  void metodosDePagoDisponiblesIncluyeContraentregaCuandoLaCiudadEstaCubierta() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    MetodosDePagoDisponiblesRequest cuerpo =
        new MetodosDePagoDisponiblesRequest(
            List.of(new CrearPedidoRequest.LineaRequest(variante.id(), 1)),
            "cliente@tecnosport.co",
            "ENVIO_A_DOMICILIO",
            DIRECCION_MEDELLIN);

    mockMvc
        .perform(
            post("/api/v1/pedidos/metodos-de-pago-disponibles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasItem("CONTRAENTREGA")));
  }

  @Test
  void metodosDePagoDisponiblesExcluyeContraentregaSiNadieRecaudaAhi() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    MetodosDePagoDisponiblesRequest cuerpo =
        new MetodosDePagoDisponiblesRequest(
            List.of(new CrearPedidoRequest.LineaRequest(variante.id(), 1)),
            "cliente@tecnosport.co",
            "ENVIO_A_DOMICILIO",
            DIRECCION_BOGOTA);

    mockMvc
        .perform(
            post("/api/v1/pedidos/metodos-de-pago-disponibles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", not(hasItem("CONTRAENTREGA"))))
        .andExpect(jsonPath("$", hasItem("TARJETA")));
  }

  @Test
  void reintentarPagoDeUnPedidoFallidoLoRegresaAPagoPendiente() throws Exception {
    java.util.UUID varianteId = java.util.UUID.randomUUID();
    Inventario inventario = Inventario.crear(varianteId);
    inventario.registrarEntrada(5, "siembra de prueba", Instant.now());
    inventarios.conInventario(inventario);

    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    java.util.UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    java.util.UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            new Direccion("05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", null),
            MetodoPago.NEQUI,
            "cliente@tecnosport.co",
            Instant.now());
    pedido.transicionar(
        EstadoPedido.PAGO_FALLIDO, "webhook-wompi", "pago rechazado", Instant.now());
    pedidos.guardar(pedido);

    mockMvc
        .perform(post("/api/v1/pedidos/{id}/reintentar-pago", pedido.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PAGO_PENDIENTE"));
  }

  @Test
  void reintentarPagoDeUnPedidoQueNoEstaEnPagoFallidoDevuelve422() throws Exception {
    Variante variante = publicarProductoConVarianteYExistencia(5);
    CrearPedidoRequest cuerpo =
        solicitud(variante, "ENVIO_A_DOMICILIO", DIRECCION_MEDELLIN, "NEQUI");
    String respuesta =
        mockMvc
            .perform(
                post("/api/v1/pedidos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(cuerpo)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    java.util.UUID pedidoId =
        java.util.UUID.fromString(json.readTree(respuesta).get("id").asText());

    mockMvc
        .perform(post("/api/v1/pedidos/{id}/reintentar-pago", pedidoId))
        .andExpect(status().isUnprocessableContent());
  }

  private Pedido pedidoDePruebaContraentrega(String correo) {
    java.util.UUID varianteId = java.util.UUID.randomUUID();
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 1),
            null,
            new CorreoElectronico(correo),
            List.of(
                new LineaPedido(
                    java.util.UUID.randomUUID(),
                    varianteId,
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    java.util.UUID.randomUUID())),
            TipoEntrega.RETIRO_EN_PUNTO,
            null,
            MetodoPago.CONTRAENTREGA,
            correo,
            Instant.now());
    pedidos.guardar(pedido);
    return pedido;
  }

  @Test
  void seguimientoConElCorreoCorrectoDevuelveElPedido() throws Exception {
    Pedido pedido = pedidoDePruebaContraentrega("cliente@tecnosport.co");

    mockMvc
        .perform(
            get("/api/v1/pedidos/{id}/seguimiento", pedido.id())
                .param("correo", "cliente@tecnosport.co"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(pedido.id().toString()))
        .andExpect(jsonPath("$.estado").value("CONFIRMADO_CONTRAENTREGA"))
        // El seguimiento público no expone actor ni motivo del historial interno — solo
        // AdminPedidosControlador ve el historial completo (ver AdminPedidosControladorTest).
        .andExpect(jsonPath("$.historial[0].actor").value(nullValue()))
        .andExpect(jsonPath("$.historial[0].motivo").value(nullValue()))
        .andExpect(jsonPath("$.historial[0].estado").value("CONFIRMADO_CONTRAENTREGA"));
  }

  /**
   * El hallazgo 3 de docs/12-legales-de-envio.md, con su prueba. Era un defecto en produccion: el
   * seguimiento devolvia el {@code Envio} completo, con lo que la transportadora nos cobra y la
   * comision del recaudo, a cualquiera con un id de pedido y el correo correcto.
   *
   * <p><b>El guardian cambio de forma cuando el seguimiento empezo a desglosar lo cobrado.</b>
   * Afirmaba {@code !cuerpo.contains("costoEnvio")} sobre el texto crudo, y esa cadena hoy aparece
   * de forma legitima: es el precio congelado que el comprador pago ({@code Pedido.costoEnvio()}),
   * que es su factura.
   *
   * <p><b>El primer reemplazo se quedo corto, y conviene dejar escrito por que.</b> Fijaba el juego
   * de llaves del bloque {@code envio} —que es mas fuerte que antes para ese bloque, y ahi si se
   * gano— pero perdia lo que la afirmacion vieja tenia de bueno: cubria <b>todo</b> el payload. La
   * diferencia importa porque {@link MapeadorSeguimiento} no escribe todo a mano, pese a lo que
   * dice su propio javadoc: copia objetos anidados enteros de la respuesta del panel ({@code
   * lineas}, {@code contacto}, {@code direccion}, {@code datosTransferencia}). Un campo nuevo en
   * {@code LineaPedidoRespuesta} —un flete prorrateado por linea, por ejemplo— saldria al comprador
   * y ninguna prueba caeria. Con la afirmacion vieja caia.
   *
   * <p>Por eso ahora se fijan <b>los dos</b> juegos de llaves: el de la raiz y el del bloque {@code
   * envio}. Cualquier campo que aparezca en cualquiera de los dos niveles tumba esta prueba, se
   * llame como se llame y lo escriba quien lo escriba. Y se afirma ademas el valor: 0, el del
   * pedido, y no los 12.000 de la transportadora.
   *
   * <p>La leccion general, que vale mas que el arreglo: <b>cuando un guardian estorba se reformula
   * sobre lo que de verdad protege, y hay que comprobar que la formulacion nueva cubre todo lo que
   * cubria la vieja</b> — no solo el caso que motivo el cambio.
   */
  @Test
  void elSeguimientoNoExponeElCostoRealDelFleteNiLaComisionDeRecaudo() throws Exception {
    Pedido pedido = pedidoDePruebaContraentrega("cliente@tecnosport.co");
    // Con envio sembrado, y con recaudo conciliado: sin esto la prueba no ejercitaba el bloque de
    // envio en absoluto —un pedido recien confirmado todavia no tiene envio— y pasaba en verde
    // aunque el mapeador filtrara. Se comprobo metiendo la fuga a proposito.
    Envio envio =
        Envio.crear(
            pedido.id(),
            List.of(GuiaEnvio.crear("Interrapidisimo", "GUIA-99", Dinero.deCop(12_000))),
            Instant.now());
    envio.conciliarRecaudo(Dinero.deCop(3_500), Instant.now());
    envios.guardar(envio);

    String cuerpo =
        mockMvc
            .perform(
                get("/api/v1/pedidos/{id}/seguimiento", pedido.id())
                    .param("correo", "cliente@tecnosport.co"))
            .andExpect(status().isOk())
            // El bloque del envio lleva estos dos campos y ni uno mas, y cada guia otros dos.
            // La cuenta sobre el mapa entero y no un `exists` por campo: lo que hay que impedir
            // es el campo que nadie previo. Desde adr/0031 hay que contar los dos niveles — el
            // costo de cada guia es margen, y sin la cuenta de adentro se colaria sin que la de
            // afuera se enterara.
            .andExpect(jsonPath("$.envio.guias[0].transportadora").value("Interrapidisimo"))
            .andExpect(jsonPath("$.envio.guias[0].guia").value("GUIA-99"))
            .andExpect(jsonPath("$.envio.guias[0].*", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.envio.despachadoEn").exists())
            .andExpect(jsonPath("$.envio.*", org.hamcrest.Matchers.hasSize(2)))
            // El juego de llaves de la raiz: los diecisiete campos de PedidoSeguimientoRespuesta.
            .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(17)))
            // Y el de la linea, que es un objeto que este mapeador NO escribe a mano: lo copia
            // entero del panel. Un campo nuevo ahi sale bajo $.lineas[0] y la cuenta de la raiz
            // ni se entera — comprobado agregandolo a proposito.
            .andExpect(jsonPath("$.lineas[0].*", org.hamcrest.Matchers.hasSize(8)))
            // Y el costo de envio del pedido es el precio congelado, no el costo real del flete.
            // Este pedido es de retiro en punto, asi que su precio de envio es 0; si alguien
            // mapeara aqui `Envio.costoEnvio`, saldrian 12.000 y esta linea lo dice.
            .andExpect(jsonPath("$.costoEnvio.valor").value(0))
            .andExpect(jsonPath("$.subtotal.valor").value(50_000))
            .andExpect(jsonPath("$.total.valor").value(50_000))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // La red que no depende de ningun nombre de campo, y es la mas importante de este metodo: el
    // costo real del flete son 12.000 en este escenario, y esa cifra no puede aparecer en NINGUNA
    // parte del cuerpo. Da igual como se llame el campo que la filtre, en que nivel este anidado o
    // quien lo haya agregado — si el numero sale, la fuga esta abierta. Las cuentas de llaves de
    // arriba dicen DONDE, esta dice QUE. Vale la pena porque el mapeador copia objetos enteros del
    // panel y las cuentas solo cubren los niveles que alguien se acordo de contar.
    org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains("12000"), cuerpo);
    // Sobre el texto crudo y no sobre un jsonPath: lo que hay que afirmar es que esos nombres no
    // aparecen en ninguna parte de la respuesta, no que un campo concreto venga nulo.
    org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains("comisionRecaudo"));
    // Las dos cifras que el panel necesita para decidir un reintegro tampoco salen: cuanto entro
    // por el pedido y cuanto ya se devolvio son datos de operacion, no del comprador. Se afirma
    // aqui porque MapeadorRespuestasPedido las agrego y MapeadorSeguimiento comparte su origen.
    org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains("dineroRecibido"));
    org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains("yaDevuelto"));
  }

  /**
   * El desglose que el articulo 50 exige antes de pagar tiene que seguir siendo legible despues de
   * comprar. Hasta aqui la respuesta del seguimiento solo traia el total, y la pantalla de estado
   * lo pintaba bajo la etiqueta "Subtotal" — falso desde que el flete se cobra aparte.
   */
  @Test
  void elSeguimientoDesglosaSubtotalYEnvioYElTotalEsLaSuma() throws Exception {
    Pedido pedido =
        Pedido.crear(
            NumeroPedido.de(2026, 7),
            null,
            new CorreoElectronico("cliente@tecnosport.co"),
            List.of(
                new LineaPedido(
                    java.util.UUID.randomUUID(),
                    java.util.UUID.randomUUID(),
                    new Sku("TS-CAM-AZ-M"),
                    "Camiseta running Dry-Fit",
                    1,
                    Dinero.deCop(50_000),
                    new BigDecimal("0.19"),
                    "https://cdn.tecnosport.co/img.webp",
                    java.util.UUID.randomUUID())),
            TipoEntrega.ENVIO_A_DOMICILIO,
            new co.tecnosport.api.domain.pedido.Direccion(
                "05", "Antioquia", "05001", "Medellin", "Cra. 26C #38B-31", null),
            MetodoPago.CONTRAENTREGA,
            "cliente@tecnosport.co",
            Instant.now(),
            new co.tecnosport.api.domain.envio.TarifaEnvio(
                "tar-1",
                "Servientrega",
                "Estandar",
                Dinero.deCop(14_500),
                3,
                true,
                Instant.now().plusSeconds(3600)));
    pedidos.guardar(pedido);

    mockMvc
        .perform(
            get("/api/v1/pedidos/{id}/seguimiento", pedido.id())
                .param("correo", "cliente@tecnosport.co"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subtotal.valor").value(50_000))
        .andExpect(jsonPath("$.costoEnvio.valor").value(14_500))
        .andExpect(jsonPath("$.total.valor").value(64_500))
        .andExpect(jsonPath("$.subtotal.moneda").value("COP"));
  }

  @Test
  void seguimientoConElCorreoEquivocadoDevuelve404() throws Exception {
    Pedido pedido = pedidoDePruebaContraentrega("cliente@tecnosport.co");

    mockMvc
        .perform(
            get("/api/v1/pedidos/{id}/seguimiento", pedido.id()).param("correo", "otro@correo.co"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("PEDIDO_NO_ENCONTRADO"));
  }

  @Test
  void seguimientoDeUnPedidoInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/pedidos/{id}/seguimiento", java.util.UUID.randomUUID())
                .param("correo", "cliente@tecnosport.co"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("PEDIDO_NO_ENCONTRADO"));
  }

  /** Medellín recauda; el resto del país, no. Es lo que hace hoy el sandbox. */
  private static final String CIUDAD_QUE_RECAUDA = "05001";

  private static final TarifaEnvio TARIFA =
      new TarifaEnvio(
          "rate_1",
          "Coordinadora",
          "Standard",
          Dinero.deCop(14_900),
          2,
          false,
          Instant.parse("2026-09-30T12:00:00Z"));

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductos() {
      return new RepositorioProductosDobleDePrueba();
    }

    @Bean
    RepositorioInventarioDobleDePrueba repositorioInventario() {
      return new RepositorioInventarioDobleDePrueba();
    }

    @Bean
    RepositorioPedidosDobleDePrueba repositorioPedidos() {
      return new RepositorioPedidosDobleDePrueba();
    }

    @Bean
    CriteriosContraentrega criteriosContraentrega() {
      return new CriteriosContraentrega(true, Dinero.deCop(1), Dinero.deCop(10_000_000), Set.of());
    }

    @Bean
    MetodosDePagoDisponibles metodosDePagoDisponibles(
        RepositorioProductos repositorioProductos,
        CotizarEnvio cotizarEnvio,
        RepositorioPedidos repositorioPedidos,
        CriteriosContraentrega criteriosContraentrega) {
      return new MetodosDePagoDisponibles(
          repositorioProductos,
          cotizarEnvio,
          repositorioPedidos,
          criteriosContraentrega,
          // Los mismos que el valor por omisión de application.yml: Addi fuera.
          EnumSet.of(MetodoPago.TARJETA, MetodoPago.PSE, MetodoPago.NEQUI, MetodoPago.BANCOLOMBIA));
    }

    @Bean
    ConsultarSeguimientoPedido consultarSeguimientoPedido(RepositorioPedidos repositorioPedidos) {
      return new ConsultarSeguimientoPedido(repositorioPedidos);
    }

    @Bean
    ReintentarPago reintentarPago(
        RepositorioPedidos repositorioPedidos,
        RepositorioInventario repositorioInventario,
        Reloj reloj) {
      return new ReintentarPago(
          repositorioPedidos, repositorioInventario, reloj, Duration.ofMinutes(30));
    }

    @Bean
    Reloj reloj() {
      return Instant::now;
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManagerDobleDePrueba();
    }

    @Bean
    LimitadorDeIntentosDobleDePrueba limitadorDeIntentos() {
      return new LimitadorDeIntentosDobleDePrueba();
    }

    /**
     * Una tarifa fija: lo que se prueba aquí es el controlador, no la cotización. {@code
     * CotizadorEnvio} tiene un solo método, así que el doble cabe en una lambda.
     */
    @Bean
    CotizadorEnvio cotizadorEnvio() {
      // Como el adaptador real: en una cotización pedida con recaudo solo responden las
      // transportadoras que lo admiten, y la tarifa que sobrevive queda marcada. Aquí recauda
      // Medellín y nadie más, que es además lo que hace hoy el sandbox de Skydropx.
      return cotizacion -> {
        boolean recaudaAhi = CIUDAD_QUE_RECAUDA.equals(cotizacion.destino().codigoDaneCiudad());
        if (cotizacion.conRecaudo() && !recaudaAhi) {
          return List.of();
        }
        return List.of(
            new TarifaEnvio(
                TARIFA.idTarifa(),
                TARIFA.transportadora(),
                TARIFA.servicio(),
                TARIFA.costo(),
                TARIFA.diasEstimados(),
                cotizacion.conRecaudo(),
                TARIFA.venceEn()));
      };
    }

    @Bean
    CotizarEnvio cotizarEnvio(
        RepositorioProductos repositorioProductos, CotizadorEnvio cotizadorEnvio, Reloj reloj) {
      return new CotizarEnvio(repositorioProductos, cotizadorEnvio, reloj);
    }

    @Bean
    CrearPedido crearPedido(
        RepositorioProductos repositorioProductos,
        RepositorioInventario repositorioInventario,
        RepositorioPedidos repositorioPedidos,
        MetodosDePagoDisponibles metodosDePagoDisponibles,
        CotizarEnvio cotizarEnvio,
        Reloj reloj,
        LimitadorDeIntentos limitadorDeIntentos,
        RepositorioAutorizaciones repositorioAutorizaciones) {
      return new CrearPedido(
          repositorioProductos,
          repositorioInventario,
          repositorioPedidos,
          metodosDePagoDisponibles,
          cotizarEnvio,
          reloj,
          Duration.ofMinutes(30),
          Duration.ofHours(24),
          limitadorDeIntentos,
          5,
          Duration.ofMinutes(60),
          repositorioAutorizaciones,
          "2026-09-07");
    }

    @Bean
    RepositorioAutorizaciones repositorioAutorizaciones() {
      return new RepositorioAutorizacionesDobleDePrueba();
    }

    @Bean
    PropiedadesTransferenciaManual propiedadesTransferenciaManual() {
      return new PropiedadesTransferenciaManual(
          "Bancolombia", "ahorros", "123-456789-00", "TecnoSport SAS");
    }

    @Bean
    RepositorioEnviosDobleDePrueba repositorioEnvios() {
      return new RepositorioEnviosDobleDePrueba();
    }

    @Bean
    MapeadorRespuestasPedido mapeadorRespuestasPedido(
        PropiedadesTransferenciaManual propiedadesTransferencia,
        RepositorioEnvios repositorioEnvios,
        RepositorioReintegros repositorioReintegros) {
      return new MapeadorRespuestasPedido(
          propiedadesTransferencia,
          repositorioEnvios,
          new TopeDeReintegro(repositorioReintegros, new RepositorioSolicitudesReversionVacio()),
          java.time.Instant::now);
    }

    @Bean
    RepositorioSolicitudesRetracto repositorioSolicitudesRetracto() {
      return new RepositorioSolicitudesRetractoDobleDePrueba();
    }

    @Bean
    MapeadorSeguimiento mapeadorSeguimiento(
        MapeadorRespuestasPedido mapeadorPedido,
        RepositorioEnvios repositorioEnvios,
        RepositorioSolicitudesRetracto repositorioSolicitudes,
        RepositorioReintegros repositorioReintegros) {
      return new MapeadorSeguimiento(
          mapeadorPedido, repositorioEnvios, repositorioSolicitudes, repositorioReintegros);
    }

    @Bean
    RepositorioReintegros repositorioReintegros() {
      return new RepositorioReintegrosDobleDePrueba();
    }
  }
}
