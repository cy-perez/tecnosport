package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.ValorAtributo;
import co.tecnosport.api.domain.catalogo.Variante;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.Sku;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.infrastructure.catalogo.entidad.ImagenProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.ProductoJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteAtributoValorJpaEntity;
import co.tecnosport.api.infrastructure.catalogo.entidad.VarianteJpaEntity;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Adaptador del puerto {@link RepositorioProductos}. {@code buscarPorSlug} es un hallazgo simple
 * vía JPA + {@link MapeadorCatalogo}. {@code buscar} resuelve la página de ids con SQL nativo
 * (filtro + orden + cursor) tocando solo {@code producto}/{@code variante}, y delega la hidratación
 * completa al mismo mapeador — ver la decisión de diseño del plan de este paso.
 */
@Repository
public class RepositorioProductosJpa implements RepositorioProductos {

  private static final double UMBRAL_SIMILITUD = 0.1;

  private final ProductoJpaRepository productoJpaRepository;
  private final VarianteJpaRepository varianteJpaRepository;
  private final VarianteAtributoValorJpaRepository varianteAtributoValorJpaRepository;
  private final ImagenProductoJpaRepository imagenProductoJpaRepository;
  private final MapeadorCatalogo mapeadorCatalogo;
  private final NamedParameterJdbcTemplate jdbc;

  public RepositorioProductosJpa(
      ProductoJpaRepository productoJpaRepository,
      VarianteJpaRepository varianteJpaRepository,
      VarianteAtributoValorJpaRepository varianteAtributoValorJpaRepository,
      ImagenProductoJpaRepository imagenProductoJpaRepository,
      MapeadorCatalogo mapeadorCatalogo,
      NamedParameterJdbcTemplate jdbc) {
    this.productoJpaRepository = productoJpaRepository;
    this.varianteJpaRepository = varianteJpaRepository;
    this.varianteAtributoValorJpaRepository = varianteAtributoValorJpaRepository;
    this.imagenProductoJpaRepository = imagenProductoJpaRepository;
    this.mapeadorCatalogo = mapeadorCatalogo;
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Producto> buscarPorSlug(Slug slug) {
    return productoJpaRepository
        .findBySlug(slug.valor())
        .flatMap(p -> mapeadorCatalogo.hidratar(List.of(p.getId())).stream().findFirst());
  }

  @Override
  public Optional<Producto> buscarPorVarianteId(UUID varianteId) {
    return varianteJpaRepository
        .findById(varianteId)
        .flatMap(v -> mapeadorCatalogo.hidratar(List.of(v.getProductoId())).stream().findFirst());
  }

  @Override
  public ResultadoPaginado<Producto> buscar(
      FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {
    PaginaDeIds pagina = paginaDeIds(filtro, orden, cursor, tamanoPagina);
    List<Producto> productos = mapeadorCatalogo.hidratar(pagina.ids());
    return new ResultadoPaginado<>(productos, pagina.cursorSiguiente());
  }

  @Override
  public void guardar(Producto producto) {
    Instant ahora = Instant.now();
    productoJpaRepository.save(
        new ProductoJpaEntity(
            producto.id(),
            producto.nombre(),
            producto.slug().valor(),
            producto.descripcion(),
            producto.marca().id(),
            producto.categoria().id(),
            producto.estado().name(),
            ahora,
            ahora));
  }

  @Override
  public Optional<Producto> buscarPorId(UUID id) {
    return productoJpaRepository
        .findById(id)
        .flatMap(p -> mapeadorCatalogo.hidratar(List.of(p.getId())).stream().findFirst());
  }

  @Override
  public void actualizar(Producto producto) {
    ProductoJpaEntity existente =
        productoJpaRepository
            .findById(producto.id())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No existe el producto '"
                            + producto.id()
                            + "' que se intenta actualizar."));
    productoJpaRepository.save(
        new ProductoJpaEntity(
            producto.id(),
            producto.nombre(),
            producto.slug().valor(),
            producto.descripcion(),
            producto.marca().id(),
            producto.categoria().id(),
            producto.estado().name(),
            existente.getCreadoEn(),
            Instant.now()));
  }

  @Override
  public void agregarVariante(UUID productoId, Variante variante) {
    varianteJpaRepository.save(
        new VarianteJpaEntity(
            variante.id(),
            productoId,
            variante.sku().valor(),
            variante.precio().valor(),
            variante.tasaIva(),
            variante.existencia(),
            variante.codigoBarras().orElse(null),
            variante.paquete().pesoGramos(),
            variante.paquete().largoCm(),
            variante.paquete().anchoCm(),
            variante.paquete().altoCm(),
            variante.estado().name(),
            Instant.now()));
    List<VarianteAtributoValorJpaEntity> atributos =
        variante.atributos().stream().map(a -> aEntidad(variante.id(), a)).toList();
    varianteAtributoValorJpaRepository.saveAll(atributos);
  }

  @Override
  public void guardarImagenPrincipal(UUID productoId, ImagenProducto imagen) {
    // Hibernate ejecuta los EntityDeleteAction después de los EntityInsertAction dentro de un
    // mismo flush (orden fijo del ActionQueue), así que sin forzar el flush aquí el borrado de la
    // fila existente llegaría después del insert de la nueva y violaría el índice único parcial
    // (producto_id) where tipo = 'PRINCIPAL' and variante_id is null.
    imagenProductoJpaRepository
        .findByProductoIdAndTipoAndVarianteIdIsNull(productoId, imagen.tipo().name())
        .ifPresent(
            existente -> {
              imagenProductoJpaRepository.delete(existente);
              imagenProductoJpaRepository.flush();
            });
    imagenProductoJpaRepository.save(
        new ImagenProductoJpaEntity(
            imagen.id(),
            productoId,
            null,
            null,
            imagen.tipo().name(),
            imagen.orden(),
            imagen.url(),
            imagen.urlWebp(),
            imagen.ancho(),
            imagen.alto(),
            imagen.bytes(),
            imagen.hash().valor(),
            imagen.altEs(),
            imagen.altEn(),
            Instant.now()));
  }

  @Override
  public boolean existeVarianteConSku(Sku sku) {
    return varianteJpaRepository.existsBySku(sku.valor());
  }

  private VarianteAtributoValorJpaEntity aEntidad(UUID varianteId, ValorAtributo valorAtributo) {
    return new VarianteAtributoValorJpaEntity(
        GeneradorIdentificador.nuevo(),
        varianteId,
        valorAtributo.atributo().id(),
        valorAtributo.valor(),
        valorAtributo.colorHex());
  }

  @Override
  public ProductosPaginados buscarParaAdmin(int pagina, int tamanoPagina) {
    Page<ProductoJpaEntity> paginaEntidades =
        productoJpaRepository.findAll(
            PageRequest.of(pagina, tamanoPagina, Sort.by(Sort.Direction.DESC, "creadoEn")));
    List<UUID> ids = paginaEntidades.getContent().stream().map(ProductoJpaEntity::getId).toList();
    return new ProductosPaginados(
        mapeadorCatalogo.hidratar(ids),
        pagina,
        paginaEntidades.getTotalPages(),
        paginaEntidades.getTotalElements());
  }

  private record PaginaDeIds(List<UUID> ids, String cursorSiguiente) {}

  private record ClaveDeOrden(String expresionSql, boolean ascendente) {}

  private PaginaDeIds paginaDeIds(
      FiltroProductos filtro, OrdenProductos orden, String cursor, int tamanoPagina) {
    boolean conTexto = filtro.texto() != null && !filtro.texto().isBlank();
    ClaveDeOrden clave = claveDeOrden(orden, conTexto);

    MapSqlParameterSource parametros = new MapSqlParameterSource();
    StringBuilder sql =
        new StringBuilder(
            "select p.id as id, "
                + clave.expresionSql()
                + " as clave "
                + "from producto p "
                + "join categoria c on c.id = p.categoria_id "
                + "left join (select producto_id, min(precio) as precio_desde from variante "
                + "  where estado = 'ACTIVA' group by producto_id) v on v.producto_id = p.id "
                + "where p.estado = 'PUBLICADO' ");

    if (filtro.categoriaSlug() != null) {
      sql.append("and c.slug = :categoriaSlug ");
      parametros.addValue("categoriaSlug", filtro.categoriaSlug().valor());
    }
    if (filtro.marcaId() != null) {
      sql.append("and p.marca_id = :marcaId ");
      parametros.addValue("marcaId", filtro.marcaId());
    }
    if (filtro.linea() != null) {
      sql.append("and c.linea = :linea ");
      parametros.addValue("linea", filtro.linea().name());
    }
    if (filtro.precioMinimo() != null) {
      sql.append("and v.precio_desde >= :precioMinimo ");
      parametros.addValue("precioMinimo", filtro.precioMinimo());
    }
    if (filtro.precioMaximo() != null) {
      sql.append("and v.precio_desde <= :precioMaximo ");
      parametros.addValue("precioMaximo", filtro.precioMaximo());
    }
    if (conTexto) {
      sql.append("and similarity(lower(p.nombre), lower(:texto)) > :umbralSimilitud ");
      parametros.addValue("texto", filtro.texto());
      parametros.addValue("umbralSimilitud", UMBRAL_SIMILITUD);
    }

    if (cursor != null) {
      CodificadorCursor.Decodificado decodificado = CodificadorCursor.decodificar(cursor);
      String comparador = clave.ascendente() ? ">" : "<";
      sql.append("and (")
          .append(clave.expresionSql())
          .append(' ')
          .append(comparador)
          .append(" :claveCursor or (")
          .append(clave.expresionSql())
          .append(" = :claveCursor and p.id ")
          .append(comparador)
          .append(" :idCursor)) ");
      parametros.addValue("claveCursor", parsearClave(orden, conTexto, decodificado.claveOrden()));
      parametros.addValue("idCursor", decodificado.id());
    }

    String sentido = clave.ascendente() ? "asc" : "desc";
    sql.append("order by clave ").append(sentido).append(", p.id ").append(sentido).append(' ');
    sql.append("limit :limite");
    parametros.addValue("limite", tamanoPagina + 1);

    List<Map<String, Object>> filas = jdbc.queryForList(sql.toString(), parametros);

    boolean hayMas = filas.size() > tamanoPagina;
    List<Map<String, Object>> pagina = hayMas ? filas.subList(0, tamanoPagina) : filas;
    List<UUID> ids = pagina.stream().map(fila -> (UUID) fila.get("id")).toList();

    String cursorSiguiente = null;
    if (hayMas && !pagina.isEmpty()) {
      Map<String, Object> ultima = pagina.get(pagina.size() - 1);
      cursorSiguiente =
          CodificadorCursor.codificar(formatearClave(ultima.get("clave")), (UUID) ultima.get("id"));
    }

    return new PaginaDeIds(ids, cursorSiguiente);
  }

  private ClaveDeOrden claveDeOrden(OrdenProductos orden, boolean conTexto) {
    return switch (orden) {
      case PRECIO_ASC -> new ClaveDeOrden("v.precio_desde", true);
      case PRECIO_DESC -> new ClaveDeOrden("v.precio_desde", false);
      case MAS_RECIENTES -> new ClaveDeOrden("p.creado_en", false);
      case RELEVANCIA ->
          conTexto
              ? new ClaveDeOrden("similarity(lower(p.nombre), lower(:texto))", false)
              : new ClaveDeOrden("p.creado_en", false);
    };
  }

  private Object parsearClave(OrdenProductos orden, boolean conTexto, String claveTexto) {
    return switch (orden) {
      case PRECIO_ASC, PRECIO_DESC -> new BigDecimal(claveTexto);
      case MAS_RECIENTES -> Instant.parse(claveTexto);
      case RELEVANCIA -> conTexto ? Double.parseDouble(claveTexto) : Instant.parse(claveTexto);
    };
  }

  private String formatearClave(Object valor) {
    if (valor instanceof Timestamp marca) {
      return marca.toInstant().toString();
    }
    return String.valueOf(valor);
  }
}
