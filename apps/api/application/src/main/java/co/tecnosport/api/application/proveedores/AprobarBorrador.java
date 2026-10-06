package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AgregarVarianteComando;
import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.CategoriaNoEncontradaException;
import co.tecnosport.api.application.catalogo.CategoriaNoEsHojaException;
import co.tecnosport.api.application.catalogo.MarcaNoEncontradaException;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.ValorAtributoComando;
import co.tecnosport.api.application.catalogo.VarianteCreada;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.GaleriaLlenaException;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.BorradorProducto;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoSubida;
import co.tecnosport.api.domain.proveedores.HuellaProveedor;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoDeTalla;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * De borrador a producto publicado, en un paso.
 *
 * <p>Lo que hace, en orden: crea el producto con origen {@code PROVEEDOR} y la huella del borrador;
 * una variante por tono —y por talla, cuando el borrador trae una lista o la talla única— con el
 * precio de venta final, IVA en cero y la existencia inicial que la persona decidió, reutilizando
 * {@code AgregarVariante} para que el SKU, los atributos y el libro de inventario sigan las mismas
 * reglas que el panel; copia cada foto del bucket privado al público con la huella del contenido
 * calculada aquí —el servidor sí tiene los bytes—, la primera como principal y las demás en la
 * galería, cada una colgada de la variante de su tono; y publica.
 *
 * <p>Las reglas del catálogo se aplican enteras: la categoría es una hoja, la marca existe, sin
 * imagen principal no se publica, y {@code AgregarVariante} rechaza un SKU repetido. El SKU se
 * genera aquí —{@code PRV-} más un trozo del id del producto y un correlativo— porque el proveedor
 * no manda ninguno.
 */
public final class AprobarBorrador {

  private static final String ATRIBUTO_COLOR = "color";
  private static final String ATRIBUTO_TALLA = "talla";

  /** El valor de Talla de una prenda de talla única. La vitrina lo reconoce por este texto. */
  static final String TALLA_UNICA = "Única";

  private final RepositorioBorradores repositorioBorradores;
  private final RepositorioPublicacionesProveedor repositorioPublicaciones;
  private final RepositorioMensajesProveedor repositorioMensajes;
  private final RepositorioProveedores repositorioProveedores;
  private final RepositorioProductos repositorioProductos;
  private final RepositorioProductosDeProveedor productosDeProveedor;
  private final RepositorioMarcas repositorioMarcas;
  private final RepositorioCategorias repositorioCategorias;
  private final RepositorioAtributos repositorioAtributos;
  private final AgregarVariante agregarVariante;
  private final AlmacenDeArchivosDeProveedor almacenPrivado;
  private final AlmacenDeImagenes almacenDeImagenes;
  private final ProcesadorDeImagenes procesador;
  private final CalculadorDePHash calculadorDePHash;
  private final Reloj reloj;

  public AprobarBorrador(
      RepositorioBorradores repositorioBorradores,
      RepositorioPublicacionesProveedor repositorioPublicaciones,
      RepositorioMensajesProveedor repositorioMensajes,
      RepositorioProveedores repositorioProveedores,
      RepositorioProductos repositorioProductos,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioMarcas repositorioMarcas,
      RepositorioCategorias repositorioCategorias,
      RepositorioAtributos repositorioAtributos,
      AgregarVariante agregarVariante,
      AlmacenDeArchivosDeProveedor almacenPrivado,
      AlmacenDeImagenes almacenDeImagenes,
      ProcesadorDeImagenes procesador,
      CalculadorDePHash calculadorDePHash,
      Reloj reloj) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.repositorioPublicaciones = Objects.requireNonNull(repositorioPublicaciones);
    this.repositorioMensajes = Objects.requireNonNull(repositorioMensajes);
    this.repositorioProveedores = Objects.requireNonNull(repositorioProveedores);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
    this.repositorioMarcas = Objects.requireNonNull(repositorioMarcas);
    this.repositorioCategorias = Objects.requireNonNull(repositorioCategorias);
    this.repositorioAtributos = Objects.requireNonNull(repositorioAtributos);
    this.agregarVariante = Objects.requireNonNull(agregarVariante);
    this.almacenPrivado = Objects.requireNonNull(almacenPrivado);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
    this.procesador = Objects.requireNonNull(procesador);
    this.calculadorDePHash = Objects.requireNonNull(calculadorDePHash);
    this.reloj = Objects.requireNonNull(reloj);
  }

  public Producto ejecutar(AprobarBorradorComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    BorradorProducto borrador =
        repositorioBorradores
            .buscarPorIdParaActualizar(comando.borradorId())
            .orElseThrow(() -> new BorradorNoEncontradoException(comando.borradorId()));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    if (comando.fotos().isEmpty()) {
      throw new BorradorSinFotosException();
    }
    // Antes de subir una sola foto al bucket público: lo que se pueda saber sin los bytes se sabe
    // aquí, o cada rechazo dejaría objetos huérfanos ya subidos.
    if (comando.fotos().size() - 1 > Producto.TOPE_DE_GALERIA) {
      throw new GaleriaLlenaException(
          "La publicación trae "
              + comando.fotos().size()
              + " fotos y la galería admite la principal más "
              + Producto.TOPE_DE_GALERIA
              + ". Deja fuera las que sobren.");
    }
    Dinero precioProveedor =
        borrador.precioProveedor().orElseThrow(BorradorSinPrecioException::new);
    Proveedor proveedor =
        repositorioProveedores
            .buscarPorId(borrador.proveedorId())
            .orElseThrow(() -> new ProveedorNoEncontradoException(borrador.proveedorId()));
    PublicacionProveedor publicacion =
        repositorioPublicaciones
            .buscarPorId(borrador.publicacionId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "El borrador apunta a una publicación que no existe."));
    Marca marca =
        repositorioMarcas
            .buscarPorId(comando.marcaId())
            .orElseThrow(() -> new MarcaNoEncontradaException(comando.marcaId()));
    Categoria categoria =
        repositorioCategorias
            .buscarPorId(comando.categoriaId())
            .orElseThrow(() -> new CategoriaNoEncontradaException(comando.categoriaId()));
    if (!repositorioCategorias.hijasDe(categoria.id()).isEmpty()) {
      throw new CategoriaNoEsHojaException(categoria.nombre());
    }
    Map<UUID, String> archivoPorFoto = archivosDe(publicacion, borrador);
    for (AprobarBorradorComando.FotoAprobada foto : comando.fotos()) {
      if (!archivoPorFoto.containsKey(foto.mensajeId())
          || borrador.fotosDescartadas().contains(foto.mensajeId())) {
        throw new FotoNoEsDelBorradorException(foto.mensajeId());
      }
    }

    String titulo =
        comando.titulo() == null || comando.titulo().isBlank()
            ? borrador.titulo().orElseThrow(BorradorSinTituloException::new)
            : comando.titulo().strip();
    String descripcion =
        comando.descripcion() == null || comando.descripcion().isBlank()
            ? borrador.descripcion().orElseThrow(BorradorSinDescripcionException::new)
            : comando.descripcion().strip();
    HuellaProveedor huella =
        borrador
            .huella()
            .orElseGet(() -> HuellaProveedor.calcular(proveedor.id(), titulo, precioProveedor));
    // El mismo anuncio repetido deja dos borradores en revisión; aprobar el segundo chocaría con
    // el índice único de la huella. Se consulta antes, no se atrapa después (apps/api/CLAUDE.md).
    productosDeProveedor
        .buscarPorHuella(proveedor.id(), huella)
        .ifPresent(
            existente -> {
              throw new ProductoDeProveedorYaExisteException(existente.id());
            });
    // Visto ahora, no en la fecha del mensaje: entre exportar y aprobar pasan días, y con la fecha
    // del mensaje el producto nacía ya vencido y el job lo ocultaba en su primera vuelta.
    Instant ahora = reloj.ahora();
    Instant vistoPorUltimaVez = publicacion.fecha().isAfter(ahora) ? publicacion.fecha() : ahora;
    Producto producto =
        Producto.crearDeProveedor(
            titulo,
            slugDisponible(Slug.generarDesde(titulo)),
            descripcion,
            marca,
            categoria,
            proveedor.id(),
            precioProveedor,
            huella,
            vistoPorUltimaVez);
    Tallas tallasAprobadas = comando.tallas() == null ? borrador.tallas() : comando.tallas();
    if (tallasAprobadas.tipo() == TipoDeTalla.UNICA) {
      producto.definirTallaSirveHasta(tallasAprobadas.sirveHasta());
    }
    producto.definirFotosGeneralesEnCadaColor(comando.fotosGeneralesEnCadaColor());
    repositorioProductos.guardar(producto);

    Map<String, UUID> variantePorTono = crearVariantes(producto, comando, borrador);

    // Las fotos se releen del repositorio: agregarVariante las dejó ya guardadas y el agregado en
    // memoria tiene que verlas para poder publicar.
    Producto conVariantes = repositorioProductos.buscarPorId(producto.id()).orElseThrow();
    PHash pHashDeLaPrincipal =
        publicarFotos(
            conVariantes, comando, archivoPorFoto, variantePorTono, borrador.pHash().isEmpty());
    conVariantes.publicar();
    repositorioProductos.actualizar(conVariantes);

    borrador.aprobar(conVariantes.id(), pHashDeLaPrincipal);
    repositorioBorradores.actualizar(borrador);
    return conVariantes;
  }

  /** Una variante por tono; y por talla cuando el borrador trae una lista. Sin tonos, una sola. */
  private Map<String, UUID> crearVariantes(
      Producto producto, AprobarBorradorComando comando, BorradorProducto borrador) {
    List<String> tonos =
        comando.fotos().stream()
            .map(AprobarBorradorComando.FotoAprobada::tono)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<String, String> hexPorTono =
        comando.fotos().stream()
            .filter(f -> f.tono() != null && f.colorHex() != null)
            .collect(Collectors.toMap(f -> f.tono(), f -> f.colorHex(), (a, b) -> a));
    Tallas tallas = comando.tallas() == null ? borrador.tallas() : comando.tallas();
    List<String> valoresDeTalla =
        switch (tallas.tipo()) {
          case LISTA -> tallas.valores();
          // La talla única es una talla: la ficha y la tarjeta la dicen, y el carrito la muestra.
          case UNICA -> List.of(TALLA_UNICA);
          case DESCONOCIDA -> List.of();
        };

    Optional<Atributo> color =
        tonos.isEmpty()
            ? Optional.empty()
            : Optional.of(atributo(ATRIBUTO_COLOR, TipoAtributo.COLOR));
    Optional<Atributo> talla =
        valoresDeTalla.isEmpty() ? Optional.empty() : Optional.of(atributo(ATRIBUTO_TALLA, null));

    Map<String, UUID> variantePorTono = new LinkedHashMap<>();
    List<String> ejesDeTono = tonos.isEmpty() ? List.of("") : tonos;
    List<String> ejesDeTalla = valoresDeTalla.isEmpty() ? List.of("") : valoresDeTalla;
    int correlativo = 1;
    for (String tono : ejesDeTono) {
      for (String valorTalla : ejesDeTalla) {
        List<ValorAtributoComando> atributos = new ArrayList<>();
        color.ifPresent(
            a -> atributos.add(new ValorAtributoComando(a.id(), tono, hexPorTono.get(tono))));
        talla.ifPresent(a -> atributos.add(new ValorAtributoComando(a.id(), valorTalla, null)));
        VarianteCreada creada =
            agregarVariante.ejecutar(
                new AgregarVarianteComando(
                    producto.id(),
                    sku(producto.id(), correlativo++),
                    comando.precioVenta(),
                    BigDecimal.ZERO,
                    null,
                    comando.existenciaInicial(),
                    null,
                    null,
                    null,
                    null,
                    atributos));
        variantePorTono.putIfAbsent(tono, creada.variante().id());
      }
    }
    return variantePorTono;
  }

  /**
   * Si algo falla después de la primera subida —una foto repetida, una ilegible, la base—, lo que
   * ya está en el bucket público se borra antes de relanzar: el informe de huérfanos informa, no
   * limpia.
   *
   * @param conHuellaVisual si hay que calcular el pHash de la principal: el borrador de un mensaje
   *     con varios productos nace sin él, y la foto que la persona puso primero es la que dice cuál
   *     es este producto
   * @return ese pHash, o nulo si no se pidió o la foto no se pudo decodificar
   */
  private PHash publicarFotos(
      Producto producto,
      AprobarBorradorComando comando,
      Map<UUID, String> fotos,
      Map<String, UUID> variantePorTono,
      boolean conHuellaVisual) {
    List<String> subidas = new ArrayList<>();
    try {
      return publicarFotos(producto, comando, fotos, variantePorTono, conHuellaVisual, subidas);
    } catch (RuntimeException e) {
      for (String key : subidas) {
        almacenDeImagenes.eliminar(key);
      }
      throw e;
    }
  }

  private PHash publicarFotos(
      Producto producto,
      AprobarBorradorComando comando,
      Map<UUID, String> fotos,
      Map<String, UUID> variantePorTono,
      boolean conHuellaVisual,
      List<String> subidas) {
    PHash pHashDeLaPrincipal = null;
    int orden = 0;
    for (AprobarBorradorComando.FotoAprobada foto : comando.fotos()) {
      String referencia = fotos.get(foto.mensajeId());
      byte[] original =
          almacenPrivado
              .leer(referencia)
              .orElseThrow(() -> new ImagenDeProveedorIlegibleException(referencia));
      ImagenProcesada procesada = procesador.procesar(original, contentTypeDe(referencia));
      boolean principal = orden == 0;
      if (principal && conHuellaVisual) {
        pHashDeLaPrincipal = calculadorDePHash.de(original).orElse(null);
      }
      String key = keyDe(producto.id(), principal, procesada.contentType());
      almacenDeImagenes.subir(key, procesada.contentType(), procesada.bytes());
      subidas.add(key);
      // La principal nunca cuelga de una variante: el índice único de PRINCIPAL y el reemplazo
      // desde el panel solo conocen la que tiene variante_id nulo. El tono de esa foto se pierde,
      // y es el precio de que el panel pueda reemplazarla.
      UUID varianteId = principal || foto.tono() == null ? null : variantePorTono.get(foto.tono());
      ImagenProducto imagen =
          ImagenProducto.crearDeVariante(
              principal ? TipoImagen.PRINCIPAL : TipoImagen.GALERIA,
              principal ? 0 : producto.siguienteOrdenDeGaleria(),
              List.of(
                  new VarianteDeImagen(
                      procesada.ancho(),
                      almacenDeImagenes.urlPublica(key),
                      procesada.bytes().length)),
              null,
              procesada.alto(),
              new HashContenido(sha256(procesada.bytes())),
              comando.altEs(),
              comando.altEn(),
              varianteId);
      if (principal) {
        producto.asignarImagenPrincipal(imagen);
        repositorioProductos.guardarImagenPrincipal(producto.id(), imagen);
      } else {
        producto.agregarImagenGaleria(imagen);
        repositorioProductos.guardarImagenDeGaleria(producto.id(), imagen);
      }
      orden++;
    }
    return pHashDeLaPrincipal;
  }

  /**
   * El archivo de cada foto que se puede aprobar, por su id: las de la publicación que traen
   * archivo y las que se subieron al borrador desde el panel.
   */
  private Map<UUID, String> archivosDe(
      PublicacionProveedor publicacion, BorradorProducto borrador) {
    Map<UUID, String> archivos =
        repositorioMensajes.listarDeLote(publicacion.loteId()).stream()
            .filter(m -> publicacion.medios().contains(m.id()))
            .filter(m -> m.referenciaArchivo().isPresent())
            .collect(
                Collectors.toMap(
                    MensajeProveedor::id,
                    m -> m.referenciaArchivo().orElseThrow(),
                    (a, b) -> a,
                    LinkedHashMap::new));
    for (FotoSubida subida : borrador.fotosSubidas()) {
      archivos.put(subida.id(), subida.referenciaArchivo());
    }
    return archivos;
  }

  private Atributo atributo(String nombre, TipoAtributo tipo) {
    return repositorioAtributos.listarTodas().stream()
        .filter(a -> a.nombre().toLowerCase(Locale.ROOT).equals(nombre))
        .filter(a -> tipo == null || a.tipo() == tipo)
        .findFirst()
        .orElseThrow(() -> new AtributoDeCatalogoNoDefinidoException(nombre));
  }

  private Slug slugDisponible(Slug candidato) {
    Slug intento = candidato;
    int sufijo = 2;
    while (repositorioProductos.buscarPorSlug(intento).isPresent()) {
      intento = new Slug(candidato.valor() + "-" + sufijo);
      sufijo++;
    }
    return intento;
  }

  /**
   * Los ÚLTIMOS ocho caracteres del id, no los primeros: un UUID v7 empieza por la marca de tiempo
   * y dos productos aprobados en el mismo milisegundo comparten ese prefijo. Lo destapó una prueba
   * que aprobaba dos borradores seguidos.
   */
  private static String sku(UUID productoId, int correlativo) {
    String sinGuiones = productoId.toString().replace("-", "");
    String trozo = sinGuiones.substring(sinGuiones.length() - 8).toUpperCase(Locale.ROOT);
    return "PRV-" + trozo + "-" + correlativo;
  }

  private static String keyDe(UUID productoId, boolean principal, String contentType) {
    String extension =
        switch (contentType) {
          case "image/png" -> "png";
          case "image/webp" -> "webp";
          case "image/avif" -> "avif";
          default -> "jpg";
        };
    return "productos/"
        + productoId
        + "/"
        + (principal ? "principal-" : "galeria-")
        + GeneradorIdentificador.nuevo()
        + "."
        + extension;
  }

  private static String contentTypeDe(String referencia) {
    String nombre = referencia.toLowerCase(Locale.ROOT);
    if (nombre.endsWith(".png")) {
      return "image/png";
    }
    if (nombre.endsWith(".webp")) {
      return "image/webp";
    }
    if (nombre.endsWith(".gif")) {
      return "image/gif";
    }
    return "image/jpeg";
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("La JVM no ofrece SHA-256.", e);
    }
  }
}
