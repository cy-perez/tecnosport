package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.MedidaDeVariante;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.application.catalogo.VarianteActiva;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.application.inventario.RepositorioInventario;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.IntercambioDePrincipal;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.inventario.Inventario;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.PHash;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Los dobles del catálogo que la resolución y la aprobación necesitan. */
final class ApoyoDeCatalogoParaIngesta {

  static final Marca MARCA = new Marca(UUID.randomUUID(), "Genérica");
  static final Categoria BOLSOS_DAMA =
      Categoria.crear("Bolsos", new Slug("bolsos"), LineaCatalogo.BOLSOS);
  static final Categoria BOLSOS_DE_MANO =
      Categoria.crearBajo(BOLSOS_DAMA, "Bolsos de mano", new Slug("bolsos-de-mano"));
  static final Atributo COLOR = Atributo.crear("Color", TipoAtributo.COLOR, List.of());
  static final Atributo TALLA =
      // Sin valores permitidos, como en producción desde V73: la lista la dicta cada prenda.
      Atributo.crear("Talla", TipoAtributo.TEXTO, List.of());

  private ApoyoDeCatalogoParaIngesta() {}

  /** Guarda por referencia: lo que un caso de uso muta en el agregado, el siguiente lo ve. */
  static final class RepositorioProductosEnMemoria implements RepositorioProductos {

    final Map<UUID, Producto> porId = new LinkedHashMap<>();
    final List<ImagenProducto> imagenesGuardadas = new ArrayList<>();

    @Override
    public void guardar(Producto producto) {
      porId.put(producto.id(), producto);
    }

    @Override
    public void actualizar(Producto producto) {
      porId.put(producto.id(), producto);
    }

    @Override
    public Optional<Producto> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    @Override
    public Optional<Producto> buscarPorSlug(Slug slug) {
      return porId.values().stream().filter(p -> p.slug().equals(slug)).findFirst();
    }

    @Override
    public void agregarVariante(UUID productoId, Variante variante) {
      // El agregado ya la tiene: AgregarVariante la añadió al objeto que devolvió buscarPorId.
    }

    @Override
    public boolean existeVarianteConSku(Sku sku) {
      return porId.values().stream()
          .flatMap(p -> p.variantes().stream())
          .anyMatch(v -> v.sku().equals(sku));
    }

    @Override
    public void guardarImagenPrincipal(UUID productoId, ImagenProducto imagen) {
      imagenesGuardadas.add(imagen);
    }

    @Override
    public void guardarImagenDeGaleria(UUID productoId, ImagenProducto imagen) {
      imagenesGuardadas.add(imagen);
    }

    @Override
    public ResultadoPaginado<Producto> buscar(
        FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {
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
    public void eliminar(UUID productoId) {
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

  static final class RepositorioProductosDeProveedorEnMemoria
      implements RepositorioProductosDeProveedor {

    private final RepositorioProductosEnMemoria productos;

    RepositorioProductosDeProveedorEnMemoria(RepositorioProductosEnMemoria productos) {
      this.productos = productos;
    }

    @Override
    public Optional<Producto> buscarPorHuella(UUID proveedorId, HuellaProveedor huella) {
      return productos.porId.values().stream()
          .filter(p -> p.proveedorId().map(proveedorId::equals).orElse(false))
          .filter(p -> p.huellaProveedor().map(huella::equals).orElse(false))
          .findFirst();
    }

    @Override
    public List<Producto> disponiblesVistosAntesDe(Instant limite) {
      return productos.porId.values().stream()
          .filter(Producto::esDeProveedor)
          .filter(
              p ->
                  p.estadoDisponibilidad()
                      == co.tecnosport.api.domain.catalogo.EstadoDisponibilidad.DISPONIBLE)
          .filter(p -> p.vistoPorUltimaVez().map(v -> v.isBefore(limite)).orElse(false))
          .toList();
    }
  }

  static final class RepositorioBorradoresEnMemoria implements RepositorioBorradores {

    final Map<UUID, BorradorProducto> porId = new LinkedHashMap<>();

    @Override
    public void guardar(BorradorProducto borrador) {
      porId.put(borrador.id(), borrador);
    }

    @Override
    public void actualizar(BorradorProducto borrador) {
      if (!porId.containsKey(borrador.id())) {
        throw new IllegalStateException("No se actualiza lo que no se guardó.");
      }
      porId.put(borrador.id(), borrador);
    }

    @Override
    public Optional<BorradorProducto> buscarPorId(UUID id) {
      return Optional.ofNullable(porId.get(id));
    }

    /** Sin concurrencia en la prueba, bloquear es buscar. */
    @Override
    public Optional<BorradorProducto> buscarPorIdParaActualizar(UUID id) {

      return buscarPorId(id);
    }

    @Override
    public BorradoresPaginados listar(
        EstadoBorrador estado, UUID proveedorId, int pagina, int tamanoPagina) {
      List<BorradorProducto> items =
          porId.values().stream()
              .filter(b -> estado == null || b.estado() == estado)
              .filter(b -> proveedorId == null || b.proveedorId().equals(proveedorId))
              .sorted(Comparator.comparing(BorradorProducto::creadoEn).reversed())
              .toList();
      return new BorradoresPaginados(items, 0, 1, items.size());
    }

    @Override
    public List<HuellaVisual> huellasVisualesDelProveedor(UUID proveedorId) {
      return porId.values().stream()
          .filter(b -> b.proveedorId().equals(proveedorId))
          .filter(b -> b.productoId().isPresent() && b.pHash().isPresent())
          .map(b -> new HuellaVisual(b.productoId().get(), b.pHash().get()))
          .toList();
    }

    @Override
    public boolean existeEnRevisionConHuella(UUID proveedorId, HuellaProveedor huella) {
      return porId.values().stream()
          .filter(b -> b.proveedorId().equals(proveedorId))
          .filter(b -> b.estado() == EstadoBorrador.EN_REVISION)
          .anyMatch(b -> b.huella().map(huella::equals).orElse(false));
    }

    @Override
    public long contarDePublicacion(UUID publicacionId) {
      return porId.values().stream().filter(b -> b.publicacionId().equals(publicacionId)).count();
    }

    @Override
    public void eliminar(UUID id) {
      porId.remove(id);
    }

    List<BorradorProducto> enEstado(EstadoBorrador estado) {
      return porId.values().stream().filter(b -> b.estado() == estado).toList();
    }
  }

  /** La huella visual sale del contenido: bytes iguales, huella igual; distintos, lejana. */
  static final class CalculadorDePHashPorContenido implements CalculadorDePHash {
    @Override
    public Optional<PHash> de(byte[] imagen) {
      String contenido = new String(imagen, StandardCharsets.UTF_8);
      if (contenido.startsWith("ilegible")) {
        return Optional.empty();
      }
      long bits = 0;
      for (int i = 0; i < 64; i++) {
        if (((contenido.hashCode() * 31L + i * 2654435761L) & 1L << (i % 63)) != 0) {
          bits |= 1L << i;
        }
      }
      return Optional.of(new PHash(bits));
    }
  }

  static final class ProcesadorNulo implements ProcesadorDeImagenes {
    @Override
    public ImagenProcesada procesar(byte[] original, String contentType) {
      return new ImagenProcesada(original, contentType, 1200, 1500);
    }
  }

  static final class AlmacenDeImagenesEnMemoria implements AlmacenDeImagenes {
    final Map<String, byte[]> objetos = new HashMap<>();

    @Override
    public void subir(String objectKey, String contentType, byte[] bytes) {
      objetos.put(objectKey, bytes);
    }

    @Override
    public String urlPublica(String objectKey) {
      return "https://publico.local/" + objectKey;
    }

    @Override
    public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Long> tamanoBytes(String objectKey) {
      return Optional.ofNullable(objetos.get(objectKey)).map(b -> (long) b.length);
    }

    @Override
    public Optional<String> objectKeyDe(String urlPublica) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean eliminar(String objectKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int eliminarPorPrefijo(String prefijo, Set<String> conservar) {
      throw new UnsupportedOperationException();
    }
  }

  static final class RepositorioMarcasFijo implements RepositorioMarcas {
    @Override
    public Optional<Marca> buscarPorId(UUID id) {
      return MARCA.id().equals(id) ? Optional.of(MARCA) : Optional.empty();
    }

    @Override
    public List<Marca> listarTodas() {
      return List.of(MARCA);
    }

    @Override
    public List<Marca> listarConProductosPublicados() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean existeConNombre(String nombre) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void guardar(Marca marca) {
      throw new UnsupportedOperationException();
    }
  }

  static final class RepositorioCategoriasFijo implements RepositorioCategorias {
    @Override
    public Optional<Categoria> buscarPorId(UUID id) {
      if (BOLSOS_DAMA.id().equals(id)) {
        return Optional.of(BOLSOS_DAMA);
      }
      return BOLSOS_DE_MANO.id().equals(id) ? Optional.of(BOLSOS_DE_MANO) : Optional.empty();
    }

    @Override
    public List<Categoria> hijasDe(UUID padreId) {
      return BOLSOS_DAMA.id().equals(padreId) ? List.of(BOLSOS_DE_MANO) : List.of();
    }

    @Override
    public List<Categoria> listarTodas() {
      return List.of(BOLSOS_DAMA, BOLSOS_DE_MANO);
    }

    @Override
    public Optional<Categoria> buscarPorSlug(Slug slug) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void guardar(Categoria categoria) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void eliminar(UUID id) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean tieneProductos(UUID categoriaId) {
      throw new UnsupportedOperationException();
    }
  }

  static final class RepositorioAtributosFijo implements RepositorioAtributos {
    final List<Atributo> atributos = new ArrayList<>(List.of(COLOR, TALLA));

    @Override
    public List<Atributo> listarTodas() {
      return List.copyOf(atributos);
    }

    @Override
    public Optional<Atributo> buscarPorId(UUID id) {
      return atributos.stream().filter(a -> a.id().equals(id)).findFirst();
    }
  }

  static final class RepositorioInventarioEnMemoria implements RepositorioInventario {
    final Map<UUID, Inventario> porVariante = new LinkedHashMap<>();

    @Override
    public void guardar(Inventario inventario) {
      porVariante.put(inventario.varianteId(), inventario);
    }

    @Override
    public Optional<Inventario> buscarPorVarianteId(UUID varianteId) {
      return Optional.ofNullable(porVariante.get(varianteId));
    }

    @Override
    public List<Inventario> buscarPorVarianteIds(Collection<UUID> varianteIds) {
      return varianteIds.stream().map(porVariante::get).filter(i -> i != null).toList();
    }

    @Override
    public List<Inventario> listarTodos() {
      return new ArrayList<>(porVariante.values());
    }

    @Override
    public Inventario abrirLibroConBloqueo(UUID varianteId) {
      throw new UnsupportedOperationException();
    }
  }
}
