package co.tecnosport.api.application.difusion;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.MedidaDeVariante;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VarianteActiva;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Escenarios compartidos por las pruebas de difusión. */
final class ApoyoDeDifusion {

  private ApoyoDeDifusion() {}

  static Producto jblGrip(EstadoProducto estado, String urlVistaPrevia) {
    return jblGrip(estado, urlVistaPrevia, true);
  }

  static Producto jblGrip(EstadoProducto estado, String urlVistaPrevia, boolean conImagen) {
    return jblGrip(estado, urlVistaPrevia, conImagen, List.of());
  }

  /**
   * La principal de un producto aprobado desde un borrador de proveedor: la foto se publica tal
   * como llegó, en JPEG, y nunca se genera vista previa. Meta la sabe descargar igual, y eso es
   * justo lo que la difusión no reconocía.
   */
  static final String PRINCIPAL_JPEG = "https://storage.googleapis.com/b/principal-abc.jpg";

  static Producto jblGripConPrincipalJpeg() {
    return jblGripConGaleria(List.of());
  }

  /**
   * Con la principal en JPEG y la galería que acaba en el carrusel. Cada entrada lleva sus medidas:
   * la proporción importa, porque Instagram descarta las que se salen de su rango.
   */
  static Producto jblGripConGaleria(List<ImagenProducto> galeria) {
    return new Producto(
        UUID.randomUUID(),
        "JBL Grip",
        new Slug("jbl-grip"),
        "Un parlante portátil de 385 gramos.",
        new Marca(UUID.randomUUID(), "JBL"),
        categoriaDeParlantes(),
        EstadoProducto.PUBLICADO,
        imagen(PRINCIPAL_JPEG, 1183, 1280),
        galeria,
        null,
        List.of(varianteUnica()));
  }

  /** Una imagen ya publicada, con su URL tal cual y sin vista previa aparte. */
  static ImagenProducto imagen(String url, int ancho, int alto) {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        List.of(new VarianteDeImagen(ancho, url, 900)),
        null,
        alto,
        new HashContenido("%064x".formatted(0)),
        "Parlante JBL Grip",
        "JBL Grip speaker");
  }

  /** Una foto de galería ya publicada: la URL tal cual, sin vista previa aparte. */
  static ImagenProducto deGaleria(int orden, String url, int ancho, int alto) {
    return ImagenProducto.crear(
        TipoImagen.GALERIA,
        orden,
        List.of(new VarianteDeImagen(ancho, url, 900)),
        null,
        alto,
        new HashContenido("%064x".formatted(orden + 1)),
        "Parlante JBL Grip",
        "JBL Grip speaker");
  }

  static Producto jblGrip(
      EstadoProducto estado,
      String urlVistaPrevia,
      boolean conImagen,
      List<ImagenProducto> galeria) {
    return new Producto(
        UUID.randomUUID(),
        "JBL Grip",
        new Slug("jbl-grip"),
        "Un parlante portátil de 385 gramos.",
        new Marca(UUID.randomUUID(), "JBL"),
        categoriaDeParlantes(),
        estado,
        conImagen ? imagenPrincipal(urlVistaPrevia) : null,
        galeria,
        null,
        List.of(varianteUnica()));
  }

  private static Categoria categoriaDeParlantes() {
    return new Categoria(
        UUID.randomUUID(),
        "Parlantes",
        new Slug("parlantes"),
        LineaCatalogo.TECNOLOGIA,
        null,
        List.of(new Hashtag("Parlantes")));
  }

  private static Variante varianteUnica() {
    return Variante.crear(
        new Sku("JBL-GRIP"),
        new Dinero(BigDecimal.valueOf(299900)),
        BigDecimal.ZERO,
        null,
        null,
        List.of());
  }

  /**
   * La principal del sitio es <b>AVIF</b>, que es lo que sirve el catálogo desde {@code ADR-0056} y
   * lo que Meta no sabe descargar. Con {@code urlVistaPrevia} en nulo, este producto no es
   * difundible, y así es como lo usan las pruebas del caso que falta.
   */
  private static ImagenProducto imagenPrincipal(String urlVistaPrevia) {
    return ImagenProducto.crear(
        TipoImagen.PRINCIPAL,
        0,
        List.of(new VarianteDeImagen(1200, "https://storage.googleapis.com/b/principal.avif", 900)),
        urlVistaPrevia,
        1200,
        new HashContenido("%064x".formatted(0)),
        "Parlante JBL Grip",
        "JBL Grip speaker");
  }

  /**
   * Solo sabe buscar por id, que es lo único que {@link DifundirProducto} le pide. Los demás
   * métodos del puerto lanzan en vez de devolver vacío: un doble que contesta a lo que no le
   * preguntaron esconde el día en que el caso de uso empiece a preguntarlo.
   */
  static final class RepositorioProductosFalso implements RepositorioProductos {

    private final Producto producto;

    RepositorioProductosFalso(Producto producto) {
      this.producto = producto;
    }

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
}
