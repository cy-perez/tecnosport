package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.EstadoSetRotacion;
import co.tecnosport.api.domain.catalogo.EstadoVariante;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.infrastructure.catalogo.entidad.AtributoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.CategoriaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.MarcaJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.SetRotacionJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteAtributoValorJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Arma el agregado {@link Producto} de dominio a partir de varias tablas planas, sin relaciones JPA
 * entre entidades: cada consulta trae una tabla, y aquí se ensambla. Ver la decisión de diseño en
 * el plan de este paso.
 */
@Component
public class MapeadorCatalogo {

  private final ProductoJpaRepository productoJpaRepository;
  private final MarcaJpaRepository marcaJpaRepository;
  private final CategoriaJpaRepository categoriaJpaRepository;
  private final VarianteJpaRepository varianteJpaRepository;
  private final VarianteAtributoValorJpaRepository varianteAtributoValorJpaRepository;
  private final AtributoJpaRepository atributoJpaRepository;
  private final ImagenProductoJpaRepository imagenProductoJpaRepository;
  private final SetRotacionJpaRepository setRotacionJpaRepository;

  public MapeadorCatalogo(
      ProductoJpaRepository productoJpaRepository,
      MarcaJpaRepository marcaJpaRepository,
      CategoriaJpaRepository categoriaJpaRepository,
      VarianteJpaRepository varianteJpaRepository,
      VarianteAtributoValorJpaRepository varianteAtributoValorJpaRepository,
      AtributoJpaRepository atributoJpaRepository,
      ImagenProductoJpaRepository imagenProductoJpaRepository,
      SetRotacionJpaRepository setRotacionJpaRepository) {
    this.productoJpaRepository = productoJpaRepository;
    this.marcaJpaRepository = marcaJpaRepository;
    this.categoriaJpaRepository = categoriaJpaRepository;
    this.varianteJpaRepository = varianteJpaRepository;
    this.varianteAtributoValorJpaRepository = varianteAtributoValorJpaRepository;
    this.atributoJpaRepository = atributoJpaRepository;
    this.imagenProductoJpaRepository = imagenProductoJpaRepository;
    this.setRotacionJpaRepository = setRotacionJpaRepository;
  }

  /** Devuelve los productos hidratados en el mismo orden que {@code productoIds}. */
  public List<Producto> hidratar(List<UUID> productoIds) {
    if (productoIds.isEmpty()) {
      return List.of();
    }

    List<ProductoJpaEntity> productos = productoJpaRepository.findAllById(productoIds);

    Map<UUID, MarcaJpaEntity> marcasPorId =
        indexarPorId(
            marcaJpaRepository.findAllById(idsUnicos(productos, ProductoJpaEntity::getMarcaId)),
            MarcaJpaEntity::getId);
    Map<UUID, CategoriaJpaEntity> categoriasPorId =
        indexarPorId(
            categoriaJpaRepository.findAllById(
                idsUnicos(productos, ProductoJpaEntity::getCategoriaId)),
            CategoriaJpaEntity::getId);

    Map<UUID, List<VarianteJpaEntity>> variantesPorProducto =
        varianteJpaRepository.findByProductoIdIn(productoIds).stream()
            .collect(Collectors.groupingBy(VarianteJpaEntity::getProductoId));

    List<UUID> varianteIds =
        variantesPorProducto.values().stream()
            .flatMap(List::stream)
            .map(VarianteJpaEntity::getId)
            .toList();

    Map<UUID, List<VarianteAtributoValorJpaEntity>> valoresPorVariante =
        varianteAtributoValorJpaRepository.findByVarianteIdIn(varianteIds).stream()
            .collect(Collectors.groupingBy(VarianteAtributoValorJpaEntity::getVarianteId));

    List<UUID> atributoIds =
        valoresPorVariante.values().stream()
            .flatMap(List::stream)
            .map(VarianteAtributoValorJpaEntity::getAtributoId)
            .distinct()
            .toList();
    Map<UUID, AtributoJpaEntity> atributosPorId =
        indexarPorId(atributoJpaRepository.findAllById(atributoIds), AtributoJpaEntity::getId);

    Map<UUID, List<ImagenProductoJpaEntity>> imagenesPorProducto =
        imagenProductoJpaRepository.findByProductoIdIn(productoIds).stream()
            .collect(Collectors.groupingBy(ImagenProductoJpaEntity::getProductoId));

    Map<UUID, List<SetRotacionJpaEntity>> setsPorProducto =
        setRotacionJpaRepository.findByProductoIdIn(productoIds).stream()
            .collect(Collectors.groupingBy(SetRotacionJpaEntity::getProductoId));

    Map<UUID, Producto> productosPorId =
        productos.stream()
            .collect(
                Collectors.toMap(
                    ProductoJpaEntity::getId,
                    p ->
                        aProducto(
                            p,
                            marcasPorId,
                            categoriasPorId,
                            variantesPorProducto.getOrDefault(p.getId(), List.of()),
                            valoresPorVariante,
                            atributosPorId,
                            imagenesPorProducto.getOrDefault(p.getId(), List.of()),
                            setsPorProducto.getOrDefault(p.getId(), List.of()))));

    return productoIds.stream().map(productosPorId::get).filter(Objects::nonNull).toList();
  }

  private Producto aProducto(
      ProductoJpaEntity p,
      Map<UUID, MarcaJpaEntity> marcasPorId,
      Map<UUID, CategoriaJpaEntity> categoriasPorId,
      List<VarianteJpaEntity> variantesJpa,
      Map<UUID, List<VarianteAtributoValorJpaEntity>> valoresPorVariante,
      Map<UUID, AtributoJpaEntity> atributosPorId,
      List<ImagenProductoJpaEntity> imagenesDelProducto,
      List<SetRotacionJpaEntity> setsDelProducto) {

    Marca marca = aMarca(marcasPorId.get(p.getMarcaId()));
    Categoria categoria = aCategoria(categoriasPorId.get(p.getCategoriaId()));

    ImagenProducto imagenPrincipal =
        imagenesDelProducto.stream()
            .filter(i -> "PRINCIPAL".equals(i.getTipo()) && i.getVarianteId() == null)
            .findFirst()
            .map(this::aImagen)
            .orElse(null);

    List<ImagenProducto> galeria =
        imagenesDelProducto.stream()
            .filter(i -> "GALERIA".equals(i.getTipo()) && i.getVarianteId() == null)
            .sorted(Comparator.comparingInt(ImagenProductoJpaEntity::getOrden))
            .map(this::aImagen)
            .toList();

    // Solo el set PUBLICADO: un producto puede tener varios (el que se está capturando ahora, en
    // BORRADOR, conviviendo con el que ya se ve en la ficha). Sin este filtro, `findFirst` podía
    // devolver el borrador y la ficha se quedaba sin visor aunque hubiera uno publicado.
    SetRotacion setRotacionProducto =
        setsDelProducto.stream()
            .filter(s -> s.getVarianteId() == null)
            .filter(MapeadorCatalogo::estaPublicado)
            .findFirst()
            .map(s -> aSetRotacion(s, imagenesDelProducto))
            .orElse(null);

    List<Variante> variantes =
        variantesJpa.stream()
            // La ficha pública no expone variantes dadas de baja: mismo principio que ya aplica a
            // Producto.estado == PUBLICADO, el servidor no expone lo que no debe.
            .filter(v -> "ACTIVA".equals(v.getEstado()))
            .map(
                v ->
                    aVariante(
                        v,
                        valoresPorVariante.getOrDefault(v.getId(), List.of()),
                        atributosPorId,
                        setsDelProducto.stream()
                            .filter(s -> v.getId().equals(s.getVarianteId()))
                            .filter(MapeadorCatalogo::estaPublicado)
                            .findFirst(),
                        imagenesDelProducto))
            .toList();

    return new Producto(
        p.getId(),
        p.getNombre(),
        new Slug(p.getSlug()),
        p.getDescripcion(),
        marca,
        categoria,
        EstadoProducto.valueOf(p.getEstado()),
        imagenPrincipal,
        galeria,
        setRotacionProducto,
        variantes);
  }

  private Variante aVariante(
      VarianteJpaEntity v,
      List<VarianteAtributoValorJpaEntity> valoresJpa,
      Map<UUID, AtributoJpaEntity> atributosPorId,
      java.util.Optional<SetRotacionJpaEntity> setJpa,
      List<ImagenProductoJpaEntity> imagenesDelProducto) {

    List<ValorAtributo> atributos =
        valoresJpa.stream()
            .map(
                val ->
                    new ValorAtributo(
                        aAtributo(atributosPorId.get(val.getAtributoId())),
                        val.getValor(),
                        val.getColorHex()))
            .toList();

    SetRotacion setRotacionPropio =
        setJpa.map(s -> aSetRotacion(s, imagenesDelProducto)).orElse(null);

    return new Variante(
        v.getId(),
        new Sku(v.getSku()),
        Dinero.deCop(v.getPrecio()),
        v.getTasaIva(),
        v.getExistencia(),
        v.getCodigoBarras(),
        new Paquete(v.getPesoGramos(), v.getLargoCm(), v.getAnchoCm(), v.getAltoCm()),
        EstadoVariante.valueOf(v.getEstado()),
        atributos,
        setRotacionPropio);
  }

  private static boolean estaPublicado(SetRotacionJpaEntity set) {
    return EstadoSetRotacion.PUBLICADO.name().equals(set.getEstado());
  }

  /**
   * Público porque el adaptador del set de rotación reconstruye el agregado por su cuenta, sin
   * pasar por el producto: recibe el set y las filas de imagen que le pertenecen.
   */
  public SetRotacion aSetRotacion(
      SetRotacionJpaEntity s, List<ImagenProductoJpaEntity> imagenesDelProducto) {
    List<ImagenProducto> fotogramas =
        imagenesDelProducto.stream()
            .filter(i -> s.getId().equals(i.getSetRotacionId()))
            .sorted(Comparator.comparingInt(ImagenProductoJpaEntity::getOrden))
            .map(this::aImagen)
            .toList();

    return new SetRotacion(
        s.getId(),
        s.getProductoId(),
        s.getFotogramas(),
        fotogramas,
        EstadoSetRotacion.valueOf(s.getEstado()),
        s.getCapturadoPor(),
        s.getCapturadoEn(),
        s.getDispositivo(),
        s.getVersionAsistente());
  }

  private ImagenProducto aImagen(ImagenProductoJpaEntity i) {
    return new ImagenProducto(
        i.getId(),
        TipoImagen.valueOf(i.getTipo()),
        i.getOrden(),
        i.getUrl(),
        i.getUrlWebp(),
        i.getAncho(),
        i.getAlto(),
        i.getBytes(),
        new HashContenido(i.getHash()),
        i.getAltEs(),
        i.getAltEn());
  }

  private Marca aMarca(MarcaJpaEntity m) {
    return new Marca(m.getId(), m.getNombre());
  }

  private Categoria aCategoria(CategoriaJpaEntity c) {
    return new Categoria(
        c.getId(), c.getNombre(), new Slug(c.getSlug()), LineaCatalogo.valueOf(c.getLinea()));
  }

  private Atributo aAtributo(AtributoJpaEntity a) {
    return new Atributo(
        a.getId(), a.getNombre(), TipoAtributo.valueOf(a.getTipo()), a.getValoresPermitidos());
  }

  private static <T> List<UUID> idsUnicos(List<T> elementos, Function<T, UUID> extractor) {
    return elementos.stream().map(extractor).distinct().toList();
  }

  private static <T> Map<UUID, T> indexarPorId(List<T> elementos, Function<T, UUID> extractor) {
    return elementos.stream().collect(Collectors.toMap(extractor, Function.identity()));
  }
}
