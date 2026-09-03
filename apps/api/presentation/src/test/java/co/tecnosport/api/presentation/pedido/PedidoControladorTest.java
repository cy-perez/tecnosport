package co.tecnosport.api.presentation.pedido;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.envio.MetodosDePagoDisponibles;
import co.tecnosport.api.application.envio.RepositorioCoberturaContraentrega;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.application.pedido.CrearPedido;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.pedido.CriteriosContraentrega;
import co.tecnosport.api.presentation.pedido.dto.CrearPedidoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
  @Autowired private RepositorioProductosDobleDePrueba productos;
  @Autowired private RepositorioInventarioDobleDePrueba inventarios;

  private final ObjectMapper json = new ObjectMapper();

  private static final CrearPedidoRequest.DireccionRequest DIRECCION_MEDELLIN =
      new CrearPedidoRequest.DireccionRequest(
          "05", "Antioquia", "05001", "Medellín", "Cra. 26C #38B-31", "Casa azul");

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
            "hash",
            "alt es",
            "alt en"));
    Variante variante =
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
        List.of(new CrearPedidoRequest.LineaRequest(variante.id(), 2)),
        tipoEntrega,
        direccion,
        metodoPago);
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
        .andExpect(jsonPath("$.total.valor").value(100_000))
        .andExpect(jsonPath("$.direccion.ciudad").value("Medellín"));
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
        .andExpect(jsonPath("$.estado").value("CONFIRMADO_CONTRAENTREGA"));
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

  @Test
  void crearPedidoConVarianteInexistenteDevuelve404() throws Exception {
    CrearPedidoRequest cuerpo =
        new CrearPedidoRequest(
            "cliente@tecnosport.co",
            List.of(new CrearPedidoRequest.LineaRequest(java.util.UUID.randomUUID(), 1)),
            "RETIRO_EN_PUNTO",
            null,
            "TARJETA");

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
    RepositorioCoberturaContraentregaDobleDePrueba repositorioCoberturaContraentrega() {
      return new RepositorioCoberturaContraentregaDobleDePrueba().conCiudadCubierta("05001");
    }

    @Bean
    CriteriosContraentrega criteriosContraentrega() {
      return new CriteriosContraentrega(true, Dinero.deCop(10_000_000), Set.of());
    }

    @Bean
    MetodosDePagoDisponibles metodosDePagoDisponibles(
        RepositorioProductos repositorioProductos,
        RepositorioCoberturaContraentrega repositorioCobertura,
        RepositorioPedidos repositorioPedidos,
        CriteriosContraentrega criteriosContraentrega) {
      return new MetodosDePagoDisponibles(
          repositorioProductos, repositorioCobertura, repositorioPedidos, criteriosContraentrega);
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
    CrearPedido crearPedido(
        RepositorioProductos repositorioProductos,
        RepositorioInventario repositorioInventario,
        RepositorioPedidos repositorioPedidos,
        MetodosDePagoDisponibles metodosDePagoDisponibles,
        Reloj reloj) {
      return new CrearPedido(
          repositorioProductos,
          repositorioInventario,
          repositorioPedidos,
          metodosDePagoDisponibles,
          reloj,
          Duration.ofMinutes(30),
          Duration.ofHours(24));
    }

    @Bean
    PropiedadesTransferenciaManual propiedadesTransferenciaManual() {
      return new PropiedadesTransferenciaManual(
          "Bancolombia", "ahorros", "123-456789-00", "TecnoSport SAS");
    }

    @Bean
    MapeadorRespuestasPedido mapeadorRespuestasPedido(
        PropiedadesTransferenciaManual propiedadesTransferencia) {
      return new MapeadorRespuestasPedido(propiedadesTransferencia);
    }
  }
}
