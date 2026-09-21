package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.MedidaDeVariante;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VarianteActiva;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
class RepositorioProductosDobleDePrueba implements RepositorioProductos {

  private List<VarianteActiva> activas = List.of();
  UUID ultimaVarianteConExistenciaActualizada;
  Integer ultimaExistenciaGrabada;

  private List<Producto> productos = List.of();
  private ResultadoPaginado<Producto> resultadoBusqueda = new ResultadoPaginado<>(List.of(), null);
  private ProductosPaginados resultadoAdmin = new ProductosPaginados(List.of(), 0, 0, 0);
  Producto ultimoGuardado;
  Producto ultimoActualizado;
  UUID ultimoProductoIdConVariante;
  Variante ultimaVarianteAgregada;
  UUID ultimoProductoIdConImagen;
  ImagenProducto ultimaImagenPrincipal;
  final List<ImagenProducto> imagenesDeGaleriaGuardadas = new ArrayList<>();
  final List<UUID> imagenesDeGaleriaEliminadas = new ArrayList<>();

  /** La galería tal como la dejó el último reordenamiento. */
  List<ImagenProducto> ordenGuardado;

  UUID ultimaVarianteMedida;
  Paquete ultimoPaqueteGrabado;
  private List<MedidaDeVariante> sinMedir = List.of();
  private final Set<String> skusEnUso = new HashSet<>();

  void conProductos(Producto... productos) {
    this.productos = List.of(productos);
  }

  void conSkusEnUso(String... skus) {
    this.skusEnUso.addAll(List.of(skus));
  }

  /**
   * Limpia el estado sembrado — el bean es un singleton compartido entre los métodos de la clase de
   * prueba.
   */
  void limpiar() {
    this.productos = List.of();
    this.resultadoBusqueda = new ResultadoPaginado<>(List.of(), null);
    this.resultadoAdmin = new ProductosPaginados(List.of(), 0, 0, 0);
    this.ultimoGuardado = null;
    this.ultimoActualizado = null;
    this.ultimoProductoIdConVariante = null;
    this.ultimaVarianteAgregada = null;
    this.ultimoProductoIdConImagen = null;
    this.ultimaImagenPrincipal = null;
    this.imagenesDeGaleriaGuardadas.clear();
    this.imagenesDeGaleriaEliminadas.clear();
    this.ordenGuardado = null;
    this.ultimaVarianteMedida = null;
    this.ultimoPaqueteGrabado = null;
    this.sinMedir = List.of();
    this.skusEnUso.clear();
  }

  void conVariantesActivas(VarianteActiva... variantes) {
    this.activas = List.of(variantes);
  }

  void conVariantesSinMedir(MedidaDeVariante... variantes) {
    this.sinMedir = List.of(variantes);
  }

  void devolverEnBusqueda(ResultadoPaginado<Producto> resultado) {
    this.resultadoBusqueda = resultado;
  }

  void devolverEnBusquedaAdmin(ProductosPaginados resultado) {
    this.resultadoAdmin = resultado;
  }

  /** El último filtro con el que se buscó, para poder afirmar cómo lo tradujo el controlador. */
  private FiltroProductos ultimoFiltro;

  FiltroProductos ultimoFiltro() {
    return ultimoFiltro;
  }

  @Override
  public ResultadoPaginado<Producto> buscar(
      FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {
    this.ultimoFiltro = filtro;
    return resultadoBusqueda;
  }

  @Override
  public Optional<Producto> buscarPorSlug(Slug slug) {
    return productos.stream().filter(producto -> producto.slug().equals(slug)).findFirst();
  }

  @Override
  public Optional<Producto> buscarPorVarianteId(UUID varianteId) {
    return productos.stream()
        .filter(producto -> producto.variantes().stream().anyMatch(v -> v.id().equals(varianteId)))
        .findFirst();
  }

  @Override
  public ProductosPaginados buscarParaAdmin(int pagina, int tamanoPagina) {
    return resultadoAdmin;
  }

  @Override
  public void guardar(Producto producto) {
    this.ultimoGuardado = producto;
  }

  @Override
  public Optional<Producto> buscarPorId(UUID id) {
    return productos.stream().filter(producto -> producto.id().equals(id)).findFirst();
  }

  @Override
  public void actualizar(Producto producto) {
    this.ultimoActualizado = producto;
  }

  @Override
  public void agregarVariante(UUID productoId, Variante variante) {
    this.ultimoProductoIdConVariante = productoId;
    this.ultimaVarianteAgregada = variante;
    this.skusEnUso.add(variante.sku().valor());
  }

  @Override
  public boolean existeVarianteConSku(Sku sku) {
    return skusEnUso.contains(sku.valor());
  }

  @Override
  public void guardarImagenPrincipal(UUID productoId, ImagenProducto imagen) {
    this.ultimoProductoIdConImagen = productoId;
    this.ultimaImagenPrincipal = imagen;
  }

  @Override
  public void guardarImagenDeGaleria(UUID productoId, ImagenProducto imagen) {
    this.imagenesDeGaleriaGuardadas.add(imagen);
  }

  @Override
  public void eliminarImagenDeGaleria(UUID productoId, UUID imagenId) {
    this.imagenesDeGaleriaEliminadas.add(imagenId);
  }

  @Override
  public void guardarOrdenDeGaleria(UUID productoId, List<ImagenProducto> galeria) {
    this.ordenGuardado = List.copyOf(galeria);
  }

  @Override
  public List<MedidaDeVariante> medidasDeVariantes() {
    return sinMedir;
  }

  @Override
  public void actualizarPaquete(UUID varianteId, Paquete paquete) {
    this.ultimaVarianteMedida = varianteId;
    this.ultimoPaqueteGrabado = paquete;
  }

  @Override
  public List<VarianteActiva> variantesActivas() {
    return activas;
  }
}
