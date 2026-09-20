package co.tecnosport.api.presentation.envio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.envio.ArmadorDeBultos;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.CotizarEnvio;
import co.tecnosport.api.application.envio.ResultadoCotizacion;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CotizacionEnvioControlador.class)
@Import(CotizacionEnvioControladorTest.Configuracion.class)
class CotizacionEnvioControladorTest {

  private static final Instant AHORA = Instant.parse("2026-09-11T12:00:00Z");
  private static final Instant VENCE = AHORA.plusSeconds(24 * 3600);

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDobleDePrueba productos;
  @Autowired private CotizadorEnvioDobleDePrueba cotizador;

  private UUID varianteId;

  @BeforeEach
  void publicarUnProducto() {
    Producto producto =
        Producto.crear(
            "Camiseta running Dry-Fit",
            new Slug("camiseta-running-dry-fit"),
            "Descripción",
            Marca.crear("TecnoSport"),
            Categoria.crear(
                "Ropa deportiva", new Slug("ropa-deportiva"), LineaCatalogo.ROPA_Y_CALZADO));
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
            null,
            new Paquete(180, 30, 25, 4),
            List.of());
    producto.agregarVariante(variante);
    producto.publicar();
    productos.conProductos(producto);
    varianteId = variante.id();
    cotizador.sinTarifas();
  }

  private String cuerpo() {
    return cuerpoCon(varianteId);
  }

  private String cuerpoCon(UUID variante) {
    return """
        {
          "lineas": [ { "varianteId": "%s", "cantidad": 2 } ],
          "direccion": {
            "codigoDaneDepartamento": "11",
            "departamento": "Bogotá, D.C.",
            "codigoDaneCiudad": "11001",
            "ciudad": "Bogotá, D.C.",
            "direccion": "Calle 72 # 10-34",
            "indicaciones": "Apto. 502"
          }
        }
        """
        .formatted(variante);
  }

  @Test
  void devuelveLaTarifaMasEconomicaConSuVencimiento() throws Exception {
    cotizador.conTarifas(
        new TarifaEnvio("cara", "Servientrega", "Standard", Dinero.deCop(19_616), 2, false, VENCE),
        new TarifaEnvio(
            "barata", "Coordinadora", "Standard", Dinero.deCop(10_540), 1, false, VENCE));

    mockMvc
        .perform(
            post("/api/v1/envios/cotizacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.costoEnvio.valor").value(10540))
        .andExpect(jsonPath("$.costoEnvio.moneda").value("COP"))
        .andExpect(jsonPath("$.transportadora").value("Coordinadora"))
        .andExpect(jsonPath("$.diasEstimados").value(1))
        .andExpect(jsonPath("$.venceEn").value("2026-09-12T12:00:00Z"));
  }

  /**
   * No viaja el identificador de la tarifa del proveedor. Si llegara al navegador, alguien podría
   * devolverlo alterado al crear el pedido (docs/03-api.md).
   */
  @Test
  void laRespuestaNoExponeElIdentificadorDelProveedor() throws Exception {
    cotizador.conTarifas(
        new TarifaEnvio(
            "rate_abc123", "Coordinadora", "Standard", Dinero.deCop(10_540), 1, false, VENCE));

    mockMvc
        .perform(
            post("/api/v1/envios/cotizacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.idTarifa").doesNotExist())
        .andExpect(jsonPath("$.servicio").doesNotExist())
        // Tampoco la cobertura de recaudo: esta cotización se pide sin recaudo, así que no sabe
        // nada de contraentrega y no puede fingir que sí. Eso lo responde
        // /pedidos/metodos-de-pago-disponibles.
        .andExpect(jsonPath("$.admiteContraentrega").doesNotExist());
  }

  /**
   * Sin tarifa responde 409 y no 502: el destino no se puede despachar hoy, y echarle la culpa al
   * proveedor sería mentir. El checkout lo traduce a "solo recogida en el punto".
   */
  @Test
  void sinTarifaResponde409ConElCodigoDeNegocio() throws Exception {
    cotizador.sinTarifas();

    mockMvc
        .perform(
            post("/api/v1/envios/cotizacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("ENVIO_SIN_COBERTURA"));
  }

  /**
   * Un artículo que vale más de lo que la transportadora asegura responde 409 con su propio código
   * y, sobre todo, <strong>nombrando el artículo</strong>: el checkout tiene que poder decir cuál
   * de las cosas del carrito cambió la entrega sin leerle la prosa al `detail` (adr/0036).
   */
  @Test
  void unArticuloQueSuperaElMaximoAsegurableResponde409ConSuArticulo() throws Exception {
    UUID varianteCara = catalogoConVarianteCara();

    mockMvc
        .perform(
            post("/api/v1/envios/cotizacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoCon(varianteCara)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("ARTICULO_NO_ASEGURABLE"))
        .andExpect(jsonPath("$.articulos[0].varianteId").value(varianteCara.toString()))
        .andExpect(jsonPath("$.articulos[0].nombre").value("Portátil para diseño"));
  }

  /** Un producto de 8.000.000, por encima del máximo asegurable de la cuenta. */
  private UUID catalogoConVarianteCara() {
    Producto caro =
        Producto.crear(
            "Portátil para diseño",
            new Slug("portatil-para-diseno"),
            "Descripción",
            Marca.crear("TecnoSport"),
            Categoria.crear("Computadores", new Slug("computadores"), LineaCatalogo.TECNOLOGIA));
    caro.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            "https://cdn.tecnosport.co/img.jpg",
            "https://cdn.tecnosport.co/img.webp",
            800,
            600,
            1000,
            new HashContenido("%064x".formatted(2)),
            "alt es",
            "alt en"));
    Variante variante =
        Variante.crear(
            new Sku("TS-PC-M4-16"),
            Dinero.deCop(8_000_000),
            new BigDecimal("0.19"),
            null,
            new Paquete(2200, 40, 30, 5),
            List.of());
    caro.agregarVariante(variante);
    caro.publicar();
    productos.conProductos(caro);
    return variante.id();
  }

  /**
   * Y cuando no se pudo cotizar, 503 y un codigo distinto. No es un conflicto con el estado del
   * negocio: es un servicio del que dependemos que no respondio, y reintentar sirve — la consulta
   * del checkout ya reintenta una vez, y la deduplicacion de Skydropx hace que el segundo intento
   * traiga la cotizacion completa (docs/13 6.9).
   */
  @Test
  void unFalloAlCotizarResponde503YNoElCodigoDeCobertura() throws Exception {
    cotizador.fallar(ResultadoCotizacion.Motivo.SONDEO_AGOTADO);

    mockMvc
        .perform(
            post("/api/v1/envios/cotizacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.codigo").value("COTIZACION_NO_DISPONIBLE"));
  }

  /**
   * El proveedor caido va por el mismo camino: tampoco sabemos si esa ciudad se puede despachar.
   */
  @Test
  void elProveedorCaidoTampocoSeAnunciaComoFaltaDeCobertura() throws Exception {
    cotizador.fallar(ResultadoCotizacion.Motivo.PROVEEDOR_NO_DISPONIBLE);

    mockMvc
        .perform(
            post("/api/v1/envios/cotizacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.codigo").value("COTIZACION_NO_DISPONIBLE"));
  }

  /**
   * Y el rechazo del cuerpo no va por ahi: 409 y no 503, porque un 503 le promete al cliente que
   * reintentar sirve y aqui no sirve — Skydropx deduplica las cotizaciones por contenido, asi que
   * la misma pregunta trae el mismo rechazo. El checkout lo trata como sus dos hermanos de negocio
   * y ofrece la recogida en el punto (docs/03-api.md).
   */
  @Test
  void unCuerpoRechazadoPorElProveedorResponde409YNoUn503() throws Exception {
    cotizador.fallar(ResultadoCotizacion.Motivo.DATOS_RECHAZADOS);

    mockMvc
        .perform(
            post("/api/v1/envios/cotizacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("COTIZACION_RECHAZADA"));
  }

  @Test
  void unaVarianteDesconocidaNoCotiza() throws Exception {
    String cuerpoConVarianteFantasma =
        cuerpo().replace(varianteId.toString(), UUID.randomUUID().toString());

    mockMvc
        .perform(
            post("/api/v1/envios/cotizacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoConVarianteFantasma))
        .andExpect(status().isNotFound());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductos() {
      return new RepositorioProductosDobleDePrueba();
    }

    @Bean
    CotizadorEnvioDobleDePrueba cotizadorEnvio() {
      return new CotizadorEnvioDobleDePrueba();
    }

    @Bean
    CotizarEnvio cotizarEnvio(RepositorioProductos productos, CotizadorEnvio cotizador) {
      return new CotizarEnvio(
          new ArmadorDeBultos(productos, Dinero.deCop(10_000), Dinero.deCop(5_000_000)),
          cotizador,
          () -> AHORA);
    }
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  static final class CotizadorEnvioDobleDePrueba implements CotizadorEnvio {

    private List<TarifaEnvio> tarifas = List.of();
    private ResultadoCotizacion.Motivo falla;

    void conTarifas(TarifaEnvio... tarifas) {
      this.tarifas = List.of(tarifas);
      this.falla = null;
    }

    void sinTarifas() {
      this.tarifas = List.of();
      this.falla = null;
    }

    /**
     * No se pudo saber si hay cobertura: el checkout tiene que decir otra cosa, y con otro codigo.
     */
    void fallar(ResultadoCotizacion.Motivo motivo) {
      this.falla = motivo;
    }

    @Override
    public ResultadoCotizacion cotizar(CotizacionEnvio cotizacion) {
      if (falla != null) {
        return new ResultadoCotizacion.NoSePudoCotizar(falla);
      }
      return tarifas.isEmpty()
          ? new ResultadoCotizacion.SinCobertura()
          : new ResultadoCotizacion.ConTarifas(tarifas);
    }
  }
}
