package co.tecnosport.api.presentation.catalogo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.catalogo.AgregarImagenDeGaleria;
import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.ConfirmarImagenPrincipal;
import co.tecnosport.api.application.catalogo.CrearProducto;
import co.tecnosport.api.application.catalogo.DespublicarProducto;
import co.tecnosport.api.application.catalogo.EditarProducto;
import co.tecnosport.api.application.catalogo.EliminarProducto;
import co.tecnosport.api.application.catalogo.ListarProductosAdmin;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.PublicarProducto;
import co.tecnosport.api.application.catalogo.QuitarImagenDeGaleria;
import co.tecnosport.api.application.catalogo.ReordenarGaleria;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenDeGaleria;
import co.tecnosport.api.application.catalogo.SolicitarSubidaDeImagenPrincipal;
import co.tecnosport.api.application.catalogo.VerProductoAdmin;
import co.tecnosport.api.application.pedido.RepositorioPedidos;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.math.BigDecimal;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;

@WebMvcTest(AdminProductoControlador.class)
@Import(AdminProductoControladorTest.Configuracion.class)
class AdminProductoControladorTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RepositorioProductosDobleDePrueba repositorio;
  @Autowired private RepositorioMarcasDobleDePrueba repositorioMarcas;
  @Autowired private RepositorioCategoriasDobleDePrueba repositorioCategorias;
  @Autowired private AlmacenDeImagenesDobleDePrueba almacenDeImagenes;
  @Autowired private RepositorioPedidosParaBorradoDobleDePrueba repositorioPedidos;

  // El bean del doble es un singleton compartido por Spring entre los métodos de esta clase de
  // prueba: sin esto, un producto sembrado por una prueba (p. ej. con slug "morral-urbano") queda
  // visible en la siguiente y CrearProducto le agrega un sufijo "-2" al creer que ya existe.
  @BeforeEach
  void limpiarRepositorio() {
    repositorio.limpiar();
    almacenDeImagenes.limpiar();
    repositorioPedidos.limpiar();
  }

  @Test
  void listaProductosEnBorradorYPublicadosConPaginacion() throws Exception {
    Producto borrador = productoEnBorrador();
    repositorio.devolverEnBusquedaAdmin(new ProductosPaginados(List.of(borrador), 0, 2, 3));

    mockMvc
        .perform(get("/api/v1/admin/productos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].id").value(borrador.id().toString()))
        .andExpect(jsonPath("$.items[0].estado").value("BORRADOR"))
        .andExpect(jsonPath("$.pagina").value(0))
        .andExpect(jsonPath("$.totalPaginas").value(2))
        .andExpect(jsonPath("$.totalProductos").value(3));
  }

  @Test
  void listaVaciaDevuelve200ConItemsVacios() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/productos").param("pagina", "0").param("tamano", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)));
  }

  @Test
  void crearDevuelve201ConElProductoEnBorrador() throws Exception {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(categoria);

    mockMvc
        .perform(
            post("/api/v1/admin/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"Morral Urbano","descripcion":"","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(marca.id(), categoria.id())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nombre").value("Morral Urbano"))
        .andExpect(jsonPath("$.slug").value("morral-urbano"))
        .andExpect(jsonPath("$.estado").value("BORRADOR"));
  }

  @Test
  void crearConMarcaInexistenteDevuelve404() throws Exception {
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioCategorias.conCategorias(categoria);

    mockMvc
        .perform(
            post("/api/v1/admin/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"Morral Urbano","descripcion":"","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(UUID.randomUUID(), categoria.id())))
        .andExpect(status().isNotFound());
  }

  @Test
  void crearConNombreVacioDevuelve422() throws Exception {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(categoria);

    mockMvc
        .perform(
            post("/api/v1/admin/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"","descripcion":"","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(marca.id(), categoria.id())))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void verDevuelve200ConElProducto() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);

    mockMvc
        .perform(get("/api/v1/admin/productos/{id}", producto.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value("Morral urbano"))
        .andExpect(jsonPath("$.marca.id").value(producto.marca().id().toString()))
        .andExpect(jsonPath("$.categoria.id").value(producto.categoria().id().toString()));
  }

  @Test
  void verConIdInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/productos/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void editarDevuelve200ConLosDatosActualizadosSinCambiarElSlug() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);
    Marca nuevaMarca = Marca.crear("Under Trail");
    Categoria nuevaCategoria =
        Categoria.crear("Celulares", new Slug("celulares"), LineaCatalogo.TECNOLOGIA);
    repositorioMarcas.conMarcas(nuevaMarca);
    repositorioCategorias.conCategorias(nuevaCategoria);

    mockMvc
        .perform(
            patch("/api/v1/admin/productos/{id}", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"Morral renovado","descripcion":"Nueva","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(nuevaMarca.id(), nuevaCategoria.id())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value("Morral renovado"))
        .andExpect(jsonPath("$.slug").value("morral-urbano"))
        .andExpect(jsonPath("$.marca.nombre").value("Under Trail"));
  }

  @Test
  void editarConIdInexistenteDevuelve404() throws Exception {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    repositorioMarcas.conMarcas(marca);
    repositorioCategorias.conCategorias(categoria);

    mockMvc
        .perform(
            patch("/api/v1/admin/productos/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"Nombre","descripcion":"","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(marca.id(), categoria.id())))
        .andExpect(status().isNotFound());
  }

  @Test
  void editarConNombreVacioDevuelve422() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);
    repositorioMarcas.conMarcas(producto.marca());
    repositorioCategorias.conCategorias(producto.categoria());

    mockMvc
        .perform(
            patch("/api/v1/admin/productos/{id}", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre":"","descripcion":"","marcaId":"%s","categoriaId":"%s"}
                    """
                        .formatted(producto.marca().id(), producto.categoria().id())))
        .andExpect(status().isUnprocessableContent());
  }

  @Test
  void solicitarUrlDeSubidaDevuelve201ConUrlYObjectKeyDelProducto() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/imagen-principal/url-subida", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"contentType":"image/webp"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.objectKey", startsWith("productos/" + producto.id() + "/")))
        .andExpect(jsonPath("$.url", containsString("storage.googleapis.com")));
  }

  @Test
  void solicitarUrlDeSubidaConProductoInexistenteDevuelve404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/imagen-principal/url-subida", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"contentType":"image/webp"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void confirmarImagenPrincipalDevuelve200ConLaImagenConfirmada() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/principal-abc.webp";
    almacenDeImagenes.conObjeto(objectKey, 45_000);

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/imagen-principal", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"variantes":[{"ancho":1000,"objectKey":"%s"}],"alto":800,"hash":"%s","altEs":"alt es","altEn":"alt en"}
                    """
                        .formatted(objectKey, "%064x".formatted(1))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ancho").value(1000))
        .andExpect(jsonPath("$.alto").value(800));
  }

  @Test
  void confirmarImagenPrincipalConObjetoInexistenteEnElAlmacenDevuelve404() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/principal-nunca-subido.webp";

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/imagen-principal", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"variantes":[{"ancho":1000,"objectKey":"%s"}],"alto":800,"hash":"%s","altEs":"alt es","altEn":"alt en"}
                    """
                        .formatted(objectKey, "%064x".formatted(1))))
        .andExpect(status().isNotFound());
  }

  @Test
  void solicitarUrlDeSubidaDeGaleriaDevuelve201ConSuPropioPrefijo() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/galeria/url-subida", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"contentType":"image/jpeg"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.objectKey", startsWith("productos/" + producto.id() + "/galeria-")));
  }

  @Test
  void agregarImagenDeGaleriaDevuelve201ConSuIdYSuOrden() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/galeria-abc.jpg";
    almacenDeImagenes.conObjeto(objectKey, 120_000);

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/galeria", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"variantes":[{"ancho":2000,"objectKey":"%s"}],"alto":2000,"hash":"%s","altEs":"alt es","altEn":"alt en"}
                    """
                        .formatted(objectKey, "%064x".formatted(1))))
        .andExpect(status().isCreated())
        // El id es lo que la pantalla necesita para poder quitarla después; sin él, la galería
        // sería de solo escritura.
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.orden").value(0))
        .andExpect(jsonPath("$.ancho").value(2000));
  }

  @Test
  void agregarLaMismaImagenDosVecesDevuelve409() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);
    String primera = "productos/" + producto.id() + "/galeria-uno.jpg";
    String otraVez = "productos/" + producto.id() + "/galeria-otra-vez.jpg";
    almacenDeImagenes.conObjeto(primera, 120_000);
    almacenDeImagenes.conObjeto(otraVez, 120_000);
    String mismoHash = "%064x".formatted(7);
    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/galeria", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeGaleria(primera, mismoHash)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/galeria", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeGaleria(otraVez, mismoHash)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("IMAGEN_DE_GALERIA_DUPLICADA"));
  }

  @Test
  void agregarConLaGaleriaLlenaDevuelve409() throws Exception {
    Producto producto = productoEnBorrador();
    for (int i = 0; i < 8; i++) {
      producto.agregarImagenGaleria(imagenDeGaleria(i));
    }
    repositorio.conProductos(producto);
    String objectKey = "productos/" + producto.id() + "/galeria-una-mas.jpg";
    almacenDeImagenes.conObjeto(objectKey, 120_000);

    mockMvc
        .perform(
            post("/api/v1/admin/productos/{id}/galeria", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoDeGaleria(objectKey, "%064x".formatted(99))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("GALERIA_LLENA"));
  }

  @Test
  void reordenarLaGaleriaDevuelve204YGrabaElOrdenPedido() throws Exception {
    Producto producto = productoEnBorrador();
    ImagenProducto primera = imagenDeGaleria(0);
    ImagenProducto segunda = imagenDeGaleria(1);
    ImagenProducto tercera = imagenDeGaleria(2);
    producto.agregarImagenGaleria(primera);
    producto.agregarImagenGaleria(segunda);
    producto.agregarImagenGaleria(tercera);
    repositorio.conProductos(producto);

    mockMvc
        .perform(
            put("/api/v1/admin/productos/{id}/galeria/orden", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"imagenIds\":[\"%s\",\"%s\",\"%s\"]}"
                        .formatted(tercera.id(), primera.id(), segunda.id())))
        .andExpect(status().isNoContent());

    org.junit.jupiter.api.Assertions.assertEquals(
        List.of(tercera.id(), primera.id(), segunda.id()),
        repositorio.ordenGuardado.stream().map(ImagenProducto::id).toList());
    org.junit.jupiter.api.Assertions.assertEquals(
        List.of(0, 1, 2), repositorio.ordenGuardado.stream().map(ImagenProducto::orden).toList());
  }

  /**
   * 422 y no 404, que es lo que daba: la galería del producto —el recurso del PUT— existe, y lo que
   * pasa es que la lista que mandaron ya no la describe. Es la misma carrera de dos pestañas que el
   * caso de abajo, y adr/0053 la resuelve igual en las dos direcciones; el 404 la partía en dos
   * respuestas distintas según si a la lista le faltaba o le sobraba una imagen, y el panel pintaba
   * el mensaje escrito para el borrado.
   */
  @Test
  void reordenarNombrandoUnaImagenQueNoEsDeEseProductoDevuelve422() throws Exception {
    Producto producto = productoEnBorrador();
    ImagenProducto primera = imagenDeGaleria(0);
    producto.agregarImagenGaleria(primera);
    repositorio.conProductos(producto);

    mockMvc
        .perform(
            put("/api/v1/admin/productos/{id}/galeria/orden", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imagenIds\":[\"%s\"]}".formatted(UUID.randomUUID())))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("IMAGEN_PRODUCTO_INVALIDA"));

    org.junit.jupiter.api.Assertions.assertNull(repositorio.ordenGuardado);
  }

  /** Media galería reordenada es peor que ninguna: el 422 llega antes de grabar nada. */
  @Test
  void reordenarSinNombrarTodaLaGaleriaDevuelve422YNoGrabaNada() throws Exception {
    Producto producto = productoEnBorrador();
    ImagenProducto primera = imagenDeGaleria(0);
    ImagenProducto segunda = imagenDeGaleria(1);
    producto.agregarImagenGaleria(primera);
    producto.agregarImagenGaleria(segunda);
    repositorio.conProductos(producto);

    mockMvc
        .perform(
            put("/api/v1/admin/productos/{id}/galeria/orden", producto.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imagenIds\":[\"%s\"]}".formatted(primera.id())))
        .andExpect(status().isUnprocessableContent());

    org.junit.jupiter.api.Assertions.assertNull(repositorio.ordenGuardado);
  }

  @Test
  void quitarImagenDeGaleriaDevuelve204YBorraSuObjeto() throws Exception {
    Producto producto = productoEnBorrador();
    String objectKey = "productos/" + producto.id() + "/galeria-uno.jpg";
    almacenDeImagenes.conObjeto(objectKey, 120_000);
    ImagenProducto imagen = imagenDeGaleriaEn(almacenDeImagenes.urlPublica(objectKey), 0, 1);
    producto.agregarImagenGaleria(imagen);
    repositorio.conProductos(producto);

    mockMvc
        .perform(
            delete("/api/v1/admin/productos/{id}/galeria/{imagenId}", producto.id(), imagen.id()))
        .andExpect(status().isNoContent());

    org.junit.jupiter.api.Assertions.assertEquals(
        List.of(imagen.id()), repositorio.imagenesDeGaleriaEliminadas);
    org.junit.jupiter.api.Assertions.assertFalse(almacenDeImagenes.existe(objectKey));
  }

  @Test
  void quitarUnaImagenQueNoEsDeEseProductoDevuelve404() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);

    mockMvc
        .perform(
            delete(
                "/api/v1/admin/productos/{id}/galeria/{imagenId}",
                producto.id(),
                UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("IMAGEN_DE_GALERIA_NO_ENCONTRADA"));
  }

  @Test
  void verDevuelveLaGaleriaOrdenadaConSusIds() throws Exception {
    Producto producto = productoEnBorrador();
    producto.agregarImagenGaleria(imagenDeGaleria(0));
    producto.agregarImagenGaleria(imagenDeGaleria(1));
    repositorio.conProductos(producto);

    mockMvc
        .perform(get("/api/v1/admin/productos/{id}", producto.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.galeria", hasSize(2)))
        .andExpect(jsonPath("$.galeria[0].orden").value(0))
        .andExpect(jsonPath("$.galeria[0].id").isNotEmpty())
        .andExpect(jsonPath("$.galeria[1].orden").value(1));
  }

  /**
   * La razón de ser del detalle: con una sola URL, nadie de fuera sabe qué anchos existen de la
   * imagen principal — y eso dejaba ciego al informe de huérfanos, que daba por no reclamados los
   * anchos pequeños y el JPEG de vista previa estando vivos (deuda 24).
   */
  @Test
  void verDevuelveLaImagenPrincipalConSusVariantesYSuVistaPrevia() throws Exception {
    Producto producto = productoEnBorrador();
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(
                new VarianteDeImagen(480, "https://x/p-480.avif", 12_000),
                new VarianteDeImagen(1200, "https://x/p-1200.avif", 58_000)),
            "https://x/p-previa.jpg",
            900,
            new HashContenido("%064x".formatted(9)),
            "alt es",
            "alt en"));
    repositorio.conProductos(producto);

    mockMvc
        .perform(get("/api/v1/admin/productos/{id}", producto.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imagenPrincipal.url").value("https://x/p-1200.avif"))
        .andExpect(jsonPath("$.imagenPrincipal.variantes", hasSize(2)))
        .andExpect(jsonPath("$.imagenPrincipal.variantes[0].ancho").value(480))
        .andExpect(jsonPath("$.imagenPrincipal.variantes[1].ancho").value(1200))
        .andExpect(jsonPath("$.imagenPrincipal.urlVistaPrevia").value("https://x/p-previa.jpg"));
  }

  @Test
  void verDevuelveLaImagenPrincipalNulaCuandoNoHay() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);

    mockMvc
        .perform(get("/api/v1/admin/productos/{id}", producto.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imagenPrincipal").doesNotExist());
  }

  private static String cuerpoDeGaleria(String objectKey, String hash) {
    return """
        {"variantes":[{"ancho":2000,"objectKey":"%s"}],"alto":2000,"hash":"%s","altEs":"alt es","altEn":"alt en"}
        """
        .formatted(objectKey, hash);
  }

  private static ImagenProducto imagenDeGaleria(int orden) {
    return imagenDeGaleriaEn("https://x/galeria-" + orden + ".jpg", orden, orden + 1);
  }

  private static ImagenProducto imagenDeGaleriaEn(String url, int orden, int semillaDelHash) {
    return ImagenProducto.crear(
        TipoImagen.GALERIA,
        orden,
        List.of(new VarianteDeImagen(2000, url, 120_000)),
        null,
        2000,
        new HashContenido("%064x".formatted(semillaDelHash)),
        "alt es",
        "alt en");
  }

  /** Un borrador al que ya se le asignó la principal: el único estado desde el que se publica. */
  private static Producto productoConImagen() {
    Producto producto = productoEnBorrador();
    producto.asignarImagenPrincipal(
        ImagenProducto.crear(
            TipoImagen.PRINCIPAL,
            0,
            List.of(new VarianteDeImagen(800, "https://x/0.jpg", 1000)),
            null,
            600,
            new HashContenido("%064x".formatted(0)),
            "alt es",
            "alt en"));
    return producto;
  }

  private static Producto productoEnBorrador() {
    Marca marca = Marca.crear("TecnoSport");
    Categoria categoria = Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
    Producto producto =
        Producto.crear("Morral urbano", new Slug("morral-urbano"), "", marca, categoria);
    assert producto.estado() == EstadoProducto.BORRADOR;
    return producto;
  }

  /**
   * El endpoint que faltaba. Sin él, un producto creado por el panel se quedaba en BORRADOR para
   * siempre: {@code Producto.publicar()} existía desde la Fase 1 y solo lo llamaban las pruebas.
   */
  @Test
  void publicarDejaElProductoPublicado() throws Exception {
    Producto producto = productoConImagen();
    repositorio.conProductos(producto);

    mockMvc
        .perform(post("/api/v1/admin/productos/" + producto.id() + "/publicacion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PUBLICADO"));
  }

  /**
   * Y la invariante del dominio sale como 409 y no como 500, que es lo que habría pasado hasta hoy:
   * {@code ProductoSinImagenPrincipalException} nunca tuvo traducción HTTP porque nada podía
   * dispararla.
   */
  @Test
  void publicarSinImagenPrincipalResponde409() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);

    mockMvc
        .perform(post("/api/v1/admin/productos/" + producto.id() + "/publicacion"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PRODUCTO_SIN_IMAGEN_PRINCIPAL"));
  }

  /**
   * {@code DELETE} sobre el mismo subrecurso: se borra la publicación, no el producto. Lo que hay
   * que demostrar es justo eso — vuelve a BORRADOR y sigue existiendo, con su nombre y su slug.
   */
  @Test
  void despublicarDevuelveElProductoABorradorSinBorrarlo() throws Exception {
    Producto producto = productoConImagen();
    producto.publicar();
    repositorio.conProductos(producto);

    mockMvc
        .perform(delete("/api/v1/admin/productos/" + producto.id() + "/publicacion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("BORRADOR"))
        .andExpect(jsonPath("$.nombre").value(producto.nombre()))
        .andExpect(jsonPath("$.slug").value(producto.slug().valor()));
  }

  /** Idempotente como su inverso: despublicar un borrador es el estado que se pedía. */
  @Test
  void despublicarUnBorradorResponde200() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);

    mockMvc
        .perform(delete("/api/v1/admin/productos/" + producto.id() + "/publicacion"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("BORRADOR"));
  }

  /**
   * El borrado de verdad, y lo que lo distingue del vecino de arriba: ahí el {@code DELETE} es
   * sobre {@code /publicacion} y el producto se queda; aquí es sobre el producto y no se queda
   * nada. {@code 204} sin cuerpo: devolver lo que acaba de dejar de existir invita a guardarlo.
   */
  @Test
  void eliminarBorraElProductoYResponde204() throws Exception {
    Producto producto = productoEnBorrador();
    repositorio.conProductos(producto);

    mockMvc
        .perform(delete("/api/v1/admin/productos/" + producto.id()))
        .andExpect(status().isNoContent());

    assertEquals(List.of(producto.id()), repositorio.productosEliminados);
  }

  /** Publicado no se borra: primero se retira de la vitrina. */
  @Test
  void eliminarUnProductoPublicadoResponde409() throws Exception {
    Producto producto = productoConImagen();
    producto.publicar();
    repositorio.conProductos(producto);

    mockMvc
        .perform(delete("/api/v1/admin/productos/" + producto.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PRODUCTO_PUBLICADO"));

    assertTrue(repositorio.productosEliminados.isEmpty());
  }

  /**
   * Y con ventas no se borra ni siquiera retirado: la garantía y el retracto siguen buscando el
   * producto a partir del id de la variante vendida.
   */
  @Test
  void eliminarUnProductoConVentasResponde409() throws Exception {
    Producto producto = productoEnBorrador();
    producto.agregarVariante(
        Variante.crear(
            new Sku("SKU-VENDIDO"),
            Dinero.deCop(120_000),
            new BigDecimal("0.00"),
            null,
            null,
            List.of()));
    repositorio.conProductos(producto);
    repositorioPedidos.hayVentas = true;

    mockMvc
        .perform(delete("/api/v1/admin/productos/" + producto.id()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("PRODUCTO_CON_VENTAS"));

    assertTrue(repositorio.productosEliminados.isEmpty());
  }

  @Test
  void eliminarUnProductoQueNoExisteResponde404() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/productos/" + UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioProductosDobleDePrueba repositorioProductos() {
      return new RepositorioProductosDobleDePrueba();
    }

    @Bean
    RepositorioMarcasDobleDePrueba repositorioMarcas() {
      return new RepositorioMarcasDobleDePrueba();
    }

    @Bean
    RepositorioCategoriasDobleDePrueba repositorioCategorias() {
      return new RepositorioCategoriasDobleDePrueba();
    }

    @Bean
    ListarProductosAdmin listarProductosAdmin(RepositorioProductos repositorio) {
      return new ListarProductosAdmin(repositorio);
    }

    @Bean
    CrearProducto crearProducto(
        RepositorioProductos repositorioProductos,
        RepositorioMarcas repositorioMarcas,
        RepositorioCategorias repositorioCategorias) {
      return new CrearProducto(repositorioProductos, repositorioMarcas, repositorioCategorias);
    }

    @Bean
    VerProductoAdmin verProductoAdmin(RepositorioProductos repositorioProductos) {
      return new VerProductoAdmin(repositorioProductos);
    }

    @Bean
    EditarProducto editarProducto(
        RepositorioProductos repositorioProductos,
        RepositorioMarcas repositorioMarcas,
        RepositorioCategorias repositorioCategorias) {
      return new EditarProducto(repositorioProductos, repositorioMarcas, repositorioCategorias);
    }

    @Bean
    AlmacenDeImagenesDobleDePrueba almacenDeImagenes() {
      return new AlmacenDeImagenesDobleDePrueba();
    }

    @Bean
    SolicitarSubidaDeImagenPrincipal solicitarSubidaDeImagenPrincipal(
        RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
      return new SolicitarSubidaDeImagenPrincipal(repositorioProductos, almacenDeImagenes);
    }

    @Bean
    ConfirmarImagenPrincipal confirmarImagenPrincipal(
        RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
      return new ConfirmarImagenPrincipal(repositorioProductos, almacenDeImagenes);
    }

    @Bean
    SolicitarSubidaDeImagenDeGaleria solicitarSubidaDeImagenDeGaleria(
        RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
      return new SolicitarSubidaDeImagenDeGaleria(repositorioProductos, almacenDeImagenes);
    }

    @Bean
    AgregarImagenDeGaleria agregarImagenDeGaleria(
        RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
      return new AgregarImagenDeGaleria(repositorioProductos, almacenDeImagenes);
    }

    @Bean
    QuitarImagenDeGaleria quitarImagenDeGaleria(
        RepositorioProductos repositorioProductos, AlmacenDeImagenes almacenDeImagenes) {
      return new QuitarImagenDeGaleria(repositorioProductos, almacenDeImagenes);
    }

    @Bean
    ReordenarGaleria reordenarGaleria(RepositorioProductos repositorioProductos) {
      return new ReordenarGaleria(repositorioProductos);
    }

    /**
     * El controlador abre una transacción para el borrado de galería. En {@code @WebMvcTest} no hay
     * base de datos ni gestor real, así que este ejecuta el callback tal cual — lo que se prueba
     * aquí es el endpoint, no la transacción.
     */
    @Bean
    PlatformTransactionManager transactionManager() {
      return new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
        @Override
        protected Object doGetTransaction() {
          return new Object();
        }

        @Override
        protected void doBegin(Object transaccion, TransactionDefinition definicion) {}

        @Override
        protected void doCommit(DefaultTransactionStatus estado) {}

        @Override
        protected void doRollback(DefaultTransactionStatus estado) {}
      };
    }

    @Bean
    PublicarProducto publicarProducto(RepositorioProductos repositorioProductos) {
      return new PublicarProducto(repositorioProductos);
    }

    @Bean
    DespublicarProducto despublicarProducto(RepositorioProductos repositorioProductos) {
      return new DespublicarProducto(repositorioProductos);
    }

    @Bean
    RepositorioPedidosParaBorradoDobleDePrueba repositorioPedidos() {
      return new RepositorioPedidosParaBorradoDobleDePrueba();
    }

    @Bean
    EliminarProducto eliminarProducto(
        RepositorioProductos repositorioProductos,
        RepositorioPedidos repositorioPedidos,
        AlmacenDeImagenes almacenDeImagenes) {
      return new EliminarProducto(repositorioProductos, repositorioPedidos, almacenDeImagenes);
    }

    @Bean
    MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo() {
      return new MapeadorRespuestasCatalogo();
    }

    @Bean
    MapeadorRespuestasProductoAdmin mapeadorRespuestasProductoAdmin(
        MapeadorRespuestasCatalogo mapeadorRespuestasCatalogo) {
      return new MapeadorRespuestasProductoAdmin(mapeadorRespuestasCatalogo);
    }
  }

  static class RepositorioMarcasDobleDePrueba implements RepositorioMarcas {

    private List<Marca> marcas = List.of();

    void conMarcas(Marca... marcas) {
      this.marcas = List.of(marcas);
    }

    @Override
    public List<Marca> listarTodas() {
      return marcas;
    }

    /** Este controlador no lo usa; aquí solo cumple el contrato del puerto. */
    @Override
    public List<Marca> listarConProductosPublicados() {
      return marcas;
    }

    @Override
    public boolean existeConNombre(String nombre) {
      return marcas.stream().anyMatch(marca -> marca.nombre().equalsIgnoreCase(nombre));
    }

    /** Este controlador no crea marcas; aquí solo cumple el contrato del puerto. */
    @Override
    public void guardar(Marca marca) {
      throw new UnsupportedOperationException("Este doble no guarda marcas.");
    }

    @Override
    public Optional<Marca> buscarPorId(UUID id) {
      return marcas.stream().filter(marca -> marca.id().equals(id)).findFirst();
    }
  }
}
