package co.tecnosport.api.presentation.envio;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.pedido.ModalidadesDeEntrega;
import co.tecnosport.api.application.pedido.RecogidaEnPunto;
import co.tecnosport.api.application.proveedores.DependenciasDeProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

/**
 * El checkout pregunta aquí, con las variantes de su carrito, si hay recogida. Toda la regla de
 * quién recoge se prueba en {@code RecogidaEnPuntoTest}; aquí, que el endpoint responda la suya
 * para el carrito que le mandan.
 */
@WebMvcTest(ModalidadesDeEntregaControlador.class)
@Import(ModalidadesDeEntregaControladorTest.Configuracion.class)
class ModalidadesDeEntregaControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDobleDePrueba productos;
  @Autowired private ProveedoresDoble proveedores;

  private Proveedor riverah;
  private Variante deRiverah;
  private Variante deOtro;

  @BeforeEach
  void catalogo() {
    riverah = proveedor("La Riverah", true);
    Proveedor mayorista = proveedor("Mayorista", false);
    proveedores.conProveedores(riverah, mayorista);
    Producto pRiverah = deProveedor("blusa", riverah.id());
    Producto pOtro = deProveedor("celular", mayorista.id());
    deRiverah = conVariante(pRiverah, "TS-1");
    deOtro = conVariante(pOtro, "TS-2");
    publicar(pRiverah);
    publicar(pOtro);
    productos.conProductos(pRiverah, pOtro);
  }

  private String carrito(Variante... variantes) {
    return "{\"varianteIds\":["
        + String.join(
            ",", java.util.Arrays.stream(variantes).map(v -> "\"" + v.id() + "\"").toList())
        + "]}";
  }

  @Test
  void todoDeUnProveedorLocalOfreceLaRecogida() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/envios/modalidades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(carrito(deRiverah)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.envioADomicilio").value(true))
        .andExpect(jsonPath("$.retiroEnPunto").value(true));
  }

  /** La otra cara: un controlador que devolviera {@code true} fijo pasaría la prueba de arriba. */
  @Test
  void unArticuloQueNoEsLocalLaQuitaYElEnvioSigue() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/envios/modalidades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(carrito(deRiverah, deOtro)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.envioADomicilio").value(true))
        .andExpect(jsonPath("$.retiroEnPunto").value(false));
  }

  /** El contrato dice que la lista es obligatoria, y el servidor lo exige: 422, no un 200 mudo. */
  @Test
  void sinLaListaOConUnHuecoEsUnCuerpoMalFormado() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/envios/modalidades")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableContent());
    mockMvc
        .perform(
            post("/api/v1/envios/modalidades")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"varianteIds\":[null]}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void conLaListaVaciaNoHayRecogida() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/envios/modalidades")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"varianteIds\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.retiroEnPunto").value(false));
  }

  /** El GET de antes respondía lo mismo a todo carrito: ya no existe, para no seguir diciéndolo. */
  @Test
  void elGetGlobalYaNoResponde() throws Exception {
    mockMvc.perform(get("/api/v1/envios/modalidades")).andExpect(status().is4xxClientError());
  }

  private static Proveedor proveedor(String nombre, boolean local) {
    Proveedor proveedor =
        Proveedor.crear(
            nombre, LineaCatalogo.ROPA, "+57 300", nombre, null, OrdenDePublicacion.TEXTO_PRIMERO);
    proveedor.definirLocal(local);
    return proveedor;
  }

  private static Producto deProveedor(String slug, UUID proveedorId) {
    return Producto.crearDeProveedor(
        slug,
        new Slug(slug),
        slug + ".",
        Marca.crear("TecnoSport"),
        Categoria.crear("Blusas", new Slug("ropa-dama-blusas"), LineaCatalogo.ROPA),
        proveedorId,
        Dinero.deCop(40_000),
        HuellaProveedor.calcular(proveedorId, slug, Dinero.deCop(40_000)),
        Instant.parse("2026-10-26T15:00:00Z"));
  }

  /** Lo que se vende está publicado, y publicar pide la imagen principal. */
  private static void publicar(Producto producto) {
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://cdn.tecnosport.co/img.jpg", 1000)),
            null,
            600,
            new HashContenido("%064x".formatted(1)),
            "alt es",
            "alt en"));
    producto.publicar();
  }

  private static Variante conVariante(Producto producto, String sku) {
    Variante variante =
        Variante.crear(
            new Sku(sku),
            Dinero.deCop(60_000),
            new BigDecimal("0.19"),
            null,
            new Paquete(300, 30, 25, 4),
            List.of());
    producto.agregarVariante(variante);
    return variante;
  }

  /** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
  static final class ProveedoresDoble implements RepositorioProveedores {

    private final java.util.Map<UUID, Proveedor> porId = new java.util.HashMap<>();

    void conProveedores(Proveedor... proveedores) {
      porId.clear();
      for (Proveedor proveedor : proveedores) {
        porId.put(proveedor.id(), proveedor);
      }
    }

    @Override
    public void guardar(Proveedor proveedor) {
      porId.put(proveedor.id(), proveedor);
    }

    @Override
    public void actualizar(Proveedor proveedor) {
      porId.put(proveedor.id(), proveedor);
    }

    @Override
    public Optional<Proveedor> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<Proveedor> listar() {
      return List.copyOf(porId.values());
    }

    @Override
    public DependenciasDeProveedor dependenciasDe(UUID id) {
      throw new UnsupportedOperationException("El endpoint de modalidades no lo usa.");
    }

    @Override
    public void eliminarConSuHistorial(UUID id) {
      porId.remove(id);
    }
  }

  @TestConfiguration
  static class Configuracion {
    @Bean
    ModalidadesDeEntrega modalidadesDeEntrega() {
      return new ModalidadesDeEntrega(true);
    }

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductos() {
      return new RepositorioProductosDobleDePrueba();
    }

    @Bean
    ProveedoresDoble repositorioProveedores() {
      return new ProveedoresDoble();
    }

    @Bean
    RecogidaEnPunto recogidaEnPunto(
        ModalidadesDeEntrega modalidadesDeEntrega,
        RepositorioProductosDobleDePrueba productos,
        ProveedoresDoble proveedores) {
      return new RecogidaEnPunto(modalidadesDeEntrega, productos, proveedores);
    }
  }
}
