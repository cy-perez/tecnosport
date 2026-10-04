package co.tecnosport.api.presentation.difusion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.MedidaDeVariante;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VarianteActiva;
import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.application.difusion.ArmadorDePieDeFoto;
import co.tecnosport.api.application.difusion.DifundirProducto;
import co.tecnosport.api.application.difusion.PublicadorEnRedSocial;
import co.tecnosport.api.application.difusion.RepositorioPublicaciones;
import co.tecnosport.api.application.difusion.ResultadoPublicacion;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.IntercambioDePrincipal;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Hashtag;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.difusion.PublicacionEnRed;
import co.tecnosport.api.domain.difusion.RedSocial;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
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
 * Con el caso de uso <b>de verdad</b> y puertos falsos, no con el caso de uso simulado.
 *
 * <p>Es el mismo criterio que {@code AdminCategoriaControladorTest}, y aquí importa más de lo
 * normal: lo que se está probando es que un producto sin imagen salga como 409 y un doble clic como
 * 429, y eso es una cadena que va del dominio al {@code ManejadorDeErrores}. Con el caso de uso
 * simulado se estaría probando que un doble lanza lo que se le dijo que lanzara.
 */
@WebMvcTest(AdminDifusionControlador.class)
@Import({AdminDifusionControladorTest.Configuracion.class, ManejadorDeErrores.class})
class AdminDifusionControladorTest {

  private static final Instant AHORA = Instant.parse("2026-09-29T15:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDoble productos;
  @Autowired private RepositorioPublicacionesDoble publicaciones;

  @BeforeEach
  void catalogoLimpio() {
    productos.producto = jblGrip(EstadoProducto.PUBLICADO, true);
    publicaciones.hayReciente = false;
    publicaciones.guardadas.clear();
  }

  @Test
  void difundeYDevuelveUnaFilaPorRed() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/difusion", productos.producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"redes\":[\"FACEBOOK\",\"INSTAGRAM\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].red").value("FACEBOOK"))
        .andExpect(jsonPath("$[0].estado").value("PUBLICADA"))
        .andExpect(jsonPath("$[0].idPublicacionExterna").value("ID-EXTERNO"))
        .andExpect(jsonPath("$[1].red").value("INSTAGRAM"));
  }

  /** Lo que falta se dice con un 409, no con un 500: se arregla en la ficha y se vuelve. */
  @Test
  void unProductoSinImagenResponde409ConSuCodigo() throws Exception {
    productos.producto = jblGrip(EstadoProducto.PUBLICADO, false);

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/difusion", productos.producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"redes\":[\"FACEBOOK\"]}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_DIFUNDIBLE"));
  }

  @Test
  void unBorradorTampocoSeDifunde() throws Exception {
    productos.producto = jblGrip(EstadoProducto.BORRADOR, true);

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/difusion", productos.producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"redes\":[\"FACEBOOK\"]}"))
        .andExpect(status().isConflict());
  }

  /** El doble clic es 429 y no 409: no es el estado lo que estorba, es la prisa. */
  @Test
  void elDobleClicResponde429() throws Exception {
    publicaciones.hayReciente = true;

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/difusion", productos.producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"redes\":[\"INSTAGRAM\"]}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("DIFUSION_REPETIDA"));
  }

  @Test
  void unProductoQueNoExisteResponde404() throws Exception {
    productos.producto = null;

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/difusion", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"redes\":[\"FACEBOOK\"]}"))
        .andExpect(status().isNotFound());
  }

  /**
   * Aquí no hay Bean Validation: lo que protege es el constructor compacto del record, que Jackson
   * envuelve y sale como 422.
   */
  @Test
  void sinRedesNoSeDifundeNada() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/difusion", productos.producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"redes\":[]}"))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void elHistorialDevuelveElPieCompletoDeCadaPublicacion() throws Exception {
    publicaciones.guardadas.add(
        new PublicacionEnRed(
            UUID.randomUUID(),
            productos.producto.id(),
            RedSocial.INSTAGRAM,
            "JBL Grip — $299.900",
            "https://storage.googleapis.com/b/principal.jpg",
            co.tecnosport.api.domain.difusion.EstadoPublicacion.PUBLICADA,
            "181961",
            AHORA,
            AHORA,
            null));

    mockMvc
        .perform(get("/api/v1/admin/productos/{id}/difusion", productos.producto.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].pieDeFoto").value("JBL Grip — $299.900"));
  }

  /** El pie lo propone el servidor porque lleva el precio, y el precio no lo decide el cliente. */
  @Test
  void laPropuestaDePieLaCalculaElServidor() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/productos/{id}/difusion/propuesta", productos.producto.id())
                .param("red", "INSTAGRAM"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pieDeFoto").value(org.hamcrest.Matchers.containsString("$299.900")))
        .andExpect(
            jsonPath("$.pieDeFoto")
                .value(org.hamcrest.Matchers.containsString("Enlace en la bio")));
  }

  // --- escenarios ---

  private static Producto jblGrip(EstadoProducto estado, boolean conImagen) {
    Categoria categoria =
        new Categoria(
            UUID.randomUUID(),
            "Parlantes",
            new Slug("parlantes"),
            LineaCatalogo.TECNOLOGIA,
            null,
            List.of(new Hashtag("Parlantes")));
    return new Producto(
        UUID.randomUUID(),
        "JBL Grip",
        new Slug("jbl-grip"),
        "Un parlante portátil.",
        new Marca(UUID.randomUUID(), "JBL"),
        categoria,
        estado,
        conImagen ? imagen() : null,
        List.of(),
        null,
        List.of(
            Variante.crear(
                new Sku("JBL-GRIP"),
                new Dinero(BigDecimal.valueOf(299900)),
                BigDecimal.ZERO,
                null,
                null,
                List.of())));
  }

  private static ImagenProducto imagen() {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        List.of(new VarianteDeImagen(1200, "https://storage.googleapis.com/b/p.avif", 900)),
        "https://storage.googleapis.com/b/principal.jpg",
        1200,
        new HashContenido("%064x".formatted(0)),
        "Parlante JBL Grip",
        "JBL Grip speaker");
  }

  static final class PublicadorDoble implements PublicadorEnRedSocial {
    @Override
    public ResultadoPublicacion publicar(RedSocial red, String urlImagen, String pieDeFoto) {
      return ResultadoPublicacion.publicada("ID-EXTERNO");
    }
  }

  static final class RepositorioPublicacionesDoble implements RepositorioPublicaciones {
    private final List<PublicacionEnRed> guardadas = new ArrayList<>();
    private boolean hayReciente;

    @Override
    public void guardar(PublicacionEnRed publicacion) {
      if (!guardadas.contains(publicacion)) {
        guardadas.add(publicacion);
      }
    }

    @Override
    public Optional<PublicacionEnRed> ultimaDe(UUID productoId, RedSocial red) {
      return Optional.empty();
    }

    @Override
    public List<PublicacionEnRed> historialDe(UUID productoId) {
      return List.copyOf(guardadas);
    }

    @Override
    public boolean hayUnaReciente(UUID productoId, RedSocial red, Instant desde) {
      return hayReciente;
    }
  }

  /** Solo sabe buscar por id; lo demás lanza, para que se note si alguien empieza a pedirlo. */
  static final class RepositorioProductosDoble implements RepositorioProductos {
    private Producto producto;

    @Override
    public Optional<Producto> buscarPorId(UUID id) {
      return Optional.ofNullable(producto).filter(p -> p.id().equals(id));
    }

    @Override
    public ResultadoPaginado<Producto> buscar(
        FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Producto> buscarPorSlug(Slug slug) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Producto> buscarPorVarianteId(UUID varianteId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ProductosPaginados buscarParaAdmin(int pagina, int tamanoPagina) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void guardar(Producto producto) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void actualizar(Producto producto) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void eliminar(UUID productoId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void agregarVariante(UUID productoId, Variante variante) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean existeVarianteConSku(Sku sku) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<MedidaDeVariante> medidasDeVariantes() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void actualizarPaquete(UUID varianteId, Paquete paquete) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<VarianteActiva> variantesActivas() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void guardarImagenPrincipal(UUID productoId, ImagenProducto imagen) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void guardarImagenDeGaleria(UUID productoId, ImagenProducto imagen) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean eliminarImagenDeGaleria(UUID productoId, UUID imagenId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void guardarOrdenDeGaleria(UUID productoId, List<ImagenProducto> galeria) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void guardarVarianteDeImagen(UUID imagenId, UUID varianteId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void guardarIntercambioDePrincipal(UUID productoId, IntercambioDePrincipal intercambio) {
      throw new UnsupportedOperationException();
    }
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProductosDoble repositorioProductos() {
      return new RepositorioProductosDoble();
    }

    @Bean
    RepositorioPublicacionesDoble repositorioPublicaciones() {
      return new RepositorioPublicacionesDoble();
    }

    @Bean
    DifundirProducto difundirProducto(
        RepositorioProductosDoble productos, RepositorioPublicacionesDoble publicaciones) {
      return new DifundirProducto(
          productos,
          publicaciones,
          new PublicadorDoble(),
          new ArmadorDePieDeFoto("https://www.tecnosport.co", List.of()),
          new EnTransaccionPropia() {
            @Override
            public <T> T ejecutar(Supplier<T> trabajo) {
              return trabajo.get();
            }
          },
          (Reloj) () -> AHORA);
    }
  }
}
