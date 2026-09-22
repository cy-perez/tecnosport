package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.FiltroProductos;
import co.tecnosport.api.application.catalogo.MedidaDeVariante;
import co.tecnosport.api.application.catalogo.OrdenProductos;
import co.tecnosport.api.application.catalogo.ProductosPaginados;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.VarianteActiva;
import co.tecnosport.api.application.compartido.ResultadoPaginado;
import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Paquete;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
            variante.codigoBarras().orElse(null),
            variante.paquete().map(Paquete::pesoGramos).orElse(null),
            variante.paquete().map(Paquete::largoCm).orElse(null),
            variante.paquete().map(Paquete::anchoCm).orElse(null),
            variante.paquete().map(Paquete::altoCm).orElse(null),
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
  public void guardarImagenDeGaleria(UUID productoId, ImagenProducto imagen) {
    // Sin el borrado previo de guardarImagenPrincipal: aquí no hay fila que reemplazar ni índice
    // único que respetar. El orden lo trae ya puesto el agregado.
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

  /**
   * {@code @Transactional} como {@code RepositorioSetsRotacionJpa.eliminar}: un borrado derivado de
   * Spring Data no trae transacción propia —a diferencia de {@code save}, que la hereda de {@code
   * SimpleJpaRepository}—, y sin ella revienta con {@code TransactionRequiredException}. Las
   * pruebas no lo atrapan: la clase de Testcontainers es {@code @Transactional} entera, así que
   * siempre hay una abierta. Esto se cae en {@code bootRun}, no en verde.
   */
  @Override
  @Transactional
  public boolean eliminarImagenDeGaleria(UUID productoId, UUID imagenId) {
    return imagenProductoJpaRepository.deleteByIdAndProductoId(imagenId, productoId) > 0;
  }

  /**
   * Cambia el {@code orden} de las filas que ya existen, y solo eso.
   *
   * <p><b>Se modifica la fila en vez de volver a guardarla entera</b>, que era lo otro que se podía
   * hacer: construir una {@code ImagenProductoJpaEntity} nueva con el mismo id hace un merge —o
   * sea, un update— pero obliga a rellenar todas las columnas, y la única que el dominio no conoce
   * es {@code creada_en}. Reescribirla con {@code Instant.now()} pondría todas las imágenes de la
   * galería como recién creadas cada vez que alguien arrastra una foto.
   *
   * <p>{@code @Transactional} por lo mismo que {@code eliminarImagenDeGaleria}: aquí el cambio lo
   * detecta Hibernate al volcar la sesión, y sin transacción abierta no hay volcado que valga. Las
   * pruebas de Testcontainers no lo verían —la clase entera es transaccional—, así que esto se
   * caería en {@code bootRun} y no en verde.
   */
  @Override
  @Transactional
  public void guardarOrdenDeGaleria(UUID productoId, List<ImagenProducto> galeria) {
    Map<UUID, Integer> ordenPorImagen =
        galeria.stream()
            .collect(Collectors.toMap(ImagenProducto::id, ImagenProducto::orden, (a, b) -> a));
    List<ImagenProductoJpaEntity> filas =
        imagenProductoJpaRepository.findByProductoIdIn(List.of(productoId));
    List<ImagenProductoJpaEntity> cambiadas = new ArrayList<>();
    for (ImagenProductoJpaEntity fila : filas) {
      Integer nuevoOrden = ordenPorImagen.get(fila.getId());
      if (nuevoOrden != null && nuevoOrden != fila.getOrden()) {
        fila.cambiarOrden(nuevoOrden);
        cambiadas.add(fila);
      }
    }
    imagenProductoJpaRepository.saveAll(cambiadas);
  }

  @Override
  public boolean existeVarianteConSku(Sku sku) {
    return varianteJpaRepository.existsBySku(sku.valor());
  }

  /**
   * La consulta pregunta por <b>cualquiera</b> de las cuatro columnas en nulo, y no solo por el
   * peso, aunque la restricción {@code variante_paquete_completo_o_ausente} de la V55 garantice que
   * van juntas. Es el mismo criterio del {@code check} de positividad de la V32: {@code
   * SembradorCatalogo} escribe entidades JPA directo, sin pasar por {@link Paquete}, y una fila a
   * medias tiene que salir en esta lista —que es donde alguien la mira— en vez de reventar más
   * tarde al hidratarla.
   */
  @Override
  public List<MedidaDeVariante> medidasDeVariantes() {
    return jdbc.query(
        "select v.id as variante_id, p.id as producto_id, p.nombre as nombre_producto, "
            + "       v.sku as sku, p.estado as estado_producto, "
            + "       v.peso_gramos, v.largo_cm, v.ancho_cm, v.alto_cm "
            + "from variante v "
            + "join producto p on p.id = v.producto_id "
            + "where v.estado = 'ACTIVA'",
        new MapSqlParameterSource(),
        (rs, fila) ->
            new MedidaDeVariante(
                rs.getObject("variante_id", UUID.class),
                rs.getObject("producto_id", UUID.class),
                rs.getString("nombre_producto"),
                rs.getString("sku"),
                EstadoProducto.valueOf(rs.getString("estado_producto")),
                paqueteDeLaFila(rs)));
  }

  /**
   * Las cuatro o ninguna, igual que en el mapeador del agregado: una fila con tres reventaría al
   * construir el {@link Paquete}, y eso es un error de carga y no un estado del negocio. La base lo
   * impide desde la {@code V55}; esto es el cinturón del otro lado.
   */
  private static Paquete paqueteDeLaFila(java.sql.ResultSet rs) throws java.sql.SQLException {
    int peso = rs.getInt("peso_gramos");
    if (rs.wasNull()) {
      return null;
    }
    int largo = rs.getInt("largo_cm");
    int ancho = rs.getInt("ancho_cm");
    int alto = rs.getInt("alto_cm");
    return rs.wasNull() ? null : new Paquete(peso, largo, ancho, alto);
  }

  @Override
  public void actualizarPaquete(UUID varianteId, Paquete paquete) {
    VarianteJpaEntity existente =
        varianteJpaRepository
            .findById(varianteId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No existe la variante '" + varianteId + "' que se intenta medir."));
    varianteJpaRepository.save(
        new VarianteJpaEntity(
            existente.getId(),
            existente.getProductoId(),
            existente.getSku(),
            existente.getPrecio(),
            existente.getTasaIva(),
            existente.getCodigoBarras(),
            paquete.pesoGramos(),
            paquete.largoCm(),
            paquete.anchoCm(),
            paquete.altoCm(),
            existente.getEstado(),
            existente.getCreadoEn()));
  }

  @Override
  public List<VarianteActiva> variantesActivas() {
    return jdbc.query(
        "select v.id as variante_id, p.id as producto_id, p.nombre as nombre_producto, "
            + "       v.sku as sku, p.estado as estado_producto "
            + "from variante v "
            + "join producto p on p.id = v.producto_id "
            + "where v.estado = 'ACTIVA'",
        new MapSqlParameterSource(),
        (rs, fila) ->
            new VarianteActiva(
                rs.getObject("variante_id", UUID.class),
                rs.getObject("producto_id", UUID.class),
                rs.getString("nombre_producto"),
                rs.getString("sku"),
                EstadoProducto.valueOf(rs.getString("estado_producto"))));
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
