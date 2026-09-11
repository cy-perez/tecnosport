package co.tecnosport.api.presentation.envio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.application.envio.CotizadorEnvio;
import co.tecnosport.api.application.envio.CotizarEnvio;
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
            5,
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
        .formatted(varianteId);
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
        .andExpect(jsonPath("$.venceEn").value("2026-09-12T12:00:00Z"))
        .andExpect(jsonPath("$.admiteContraentrega").value(false));
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
        .andExpect(jsonPath("$.servicio").doesNotExist());
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
      return new CotizarEnvio(productos, cotizador, () -> AHORA);
    }
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  static final class CotizadorEnvioDobleDePrueba implements CotizadorEnvio {

    private List<TarifaEnvio> tarifas = List.of();

    void conTarifas(TarifaEnvio... tarifas) {
      this.tarifas = List.of(tarifas);
    }

    void sinTarifas() {
      this.tarifas = List.of();
    }

    @Override
    public List<TarifaEnvio> cotizar(CotizacionEnvio cotizacion) {
      return tarifas;
    }
  }
}
