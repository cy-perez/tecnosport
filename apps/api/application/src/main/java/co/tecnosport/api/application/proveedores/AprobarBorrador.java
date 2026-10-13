package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AgregarVarianteComando;
import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.CategoriaNoEncontradaException;
import co.tecnosport.api.application.catalogo.CategoriaNoEsHojaException;
import co.tecnosport.api.application.catalogo.ClavesDeGaleria;
import co.tecnosport.api.application.catalogo.ClavesDePrincipal;
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
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.AjusteDeTalla;
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
 * una variante por prenda —y por talla, cuando el borrador trae una lista o la talla única— con el
 * precio de venta final, IVA en cero y la existencia inicial que la persona decidió, reutilizando
 * {@code AgregarVariante} para que el SKU, los atributos y el libro de inventario sigan las mismas
 * reglas que el panel; copia cada foto del bucket privado al público con la huella del contenido
 * calculada aquí —el servidor sí tiene los bytes—, la primera como principal y las demás en la
 * galería, cada una colgada de la variante de su prenda; y publica.
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

    // Las prendas y el tono que le toca a cada foto, por posición. Se calcula una vez y lo usan las
    // dos mitades —crear las variantes y publicar las fotos—, porque tienen que coincidir
    // exactamente. Y antes de crear nada: una prenda incoherente es un error del cuerpo y no debe
    // dejar producto.
    Tallas tallasAprobadas = comando.tallas() == null ? borrador.tallas() : comando.tallas();
    PrendasDeLaAprobacion.Resueltas prendas =
        PrendasDeLaAprobacion.resolver(
            comando.fotos(),
            comando.prendas(),
            valoresDeTalla(tallasAprobadas),
            tallasAprobadas.tipo() == TipoDeTalla.LISTA);
    List<String> tonoPorFoto = prendas.valorPorFoto();
    // También antes de crear nada: una talla que no se sabe cómo pedirle al proveedor no se vende.
    exigirTallasQueSeSabenPedir(
        borrador.ajusteDeTalla(), valoresDeTalla(tallasAprobadas), categoria);

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
            .orElseGet(
                () ->
                    HuellaProveedor.deAnuncio(
                        proveedor.id(), titulo, precioProveedor, publicacion.fecha()));
    // Dos borradores con la misma huella pueden quedar en revisión —la misma referencia desde dos
    // lotes que corren antes de aprobar ninguno—; aprobar el segundo chocaría con el índice único.
    // Se consulta antes, no se atrapa después (apps/api/CLAUDE.md).
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
    if (tallasAprobadas.tipo() == TipoDeTalla.UNICA) {
      producto.definirTallaSirveHasta(tallasAprobadas.sirveHasta());
    }
    // La talla que se le pide al proveedor sale de este ajuste y de la talla de cada variante, al
    // consultar el pedido (ADR-0085).
    producto.definirAjusteDeTalla(borrador.ajusteDeTalla());
    // La gorra de un set de camiseta o polo viaja con el promedio de su categoría y 100 g más.
    borrador.set().ifPresent(set -> producto.definirPesoAdicional(set.gramosAdicionales()));
    repositorioProductos.guardar(producto);

    Map<String, UUID> variantePorTono =
        crearVariantes(producto, comando, borrador, tallasAprobadas, prendas);

    // Las fotos se releen del repositorio: agregarVariante las dejó ya guardadas y el agregado en
    // memoria tiene que verlas para poder publicar.
    Producto conVariantes = repositorioProductos.buscarPorId(producto.id()).orElseThrow();
    PHash pHashDeLaPrincipal =
        publicarFotos(
            conVariantes,
            comando,
            archivoPorFoto,
            variantePorTono,
            tonoPorFoto,
            prendas.muestraPorFoto(),
            borrador.pHash().isEmpty());
    conVariantes.publicar();
    repositorioProductos.actualizar(conVariantes);

    borrador.aprobar(conVariantes.id(), pHashDeLaPrincipal);
    repositorioBorradores.actualizar(borrador);
    return conVariantes;
  }

  /**
   * Una variante por prenda —con su tono ya numerado si se repite—, tenga foto propia o no; y por
   * talla cuando el borrador trae una lista: las de la prenda si quien aprueba las dijo, y si no,
   * las que el proveedor le dio a ese tono. Sin prendas, una sola variante por talla.
   */
  private Map<String, UUID> crearVariantes(
      Producto producto,
      AprobarBorradorComando comando,
      BorradorProducto borrador,
      Tallas tallas,
      PrendasDeLaAprobacion.Resueltas prendas) {
    List<String> valoresDeTalla = valoresDeTalla(tallas);
    Optional<Atributo> color =
        prendas.prendas().isEmpty()
            ? Optional.empty()
            : Optional.of(atributo(ATRIBUTO_COLOR, TipoAtributo.COLOR));
    Optional<Atributo> talla =
        valoresDeTalla.isEmpty() ? Optional.empty() : Optional.of(atributo(ATRIBUTO_TALLA, null));

    List<PrendasDeLaAprobacion.Prenda> ejesDeTono =
        prendas.prendas().isEmpty()
            ? List.of(new PrendasDeLaAprobacion.Prenda("", null, null))
            : prendas.prendas();
    List<String> ejesDeTalla = valoresDeTalla.isEmpty() ? List.of("") : valoresDeTalla;
    Map<String, UUID> variantePorTono = new LinkedHashMap<>();
    Map<UUID, String> referenciaPorVariante = new LinkedHashMap<>();
    int correlativo = 1;
    for (PrendasDeLaAprobacion.Prenda prenda : ejesDeTono) {
      String tono = prenda.valor();
      List<String> tallasDeLaPrenda =
          prenda.tallas() != null
              ? prenda.tallas()
              : tallasDelTono(borrador, comando, prendas.valorPorFoto(), tono, ejesDeTalla);
      for (String valorTalla : tallasDeLaPrenda) {
        List<ValorAtributoComando> atributos = new ArrayList<>();
        color.ifPresent(
            a -> atributos.add(new ValorAtributoComando(a.id(), tono, prenda.colorHex())));
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
        if (prenda.referencia() != null) {
          referenciaPorVariante.put(creada.variante().id(), prenda.referencia());
        }
      }
    }
    // El SKU de cada prenda de un álbum queda en sus variantes: es con lo que el proveedor la agota
    // sin agotar las demás (13 de octubre de 2026).
    if (!referenciaPorVariante.isEmpty()) {
      productosDeProveedor.asignarReferencias(referenciaPorVariante);
    }
    return variantePorTono;
  }

  /**
   * Cada talla que se aprueba tiene con qué nombre pedírsela al proveedor (ADR-0085). Las de una
   * prenda son siempre de estas: {@link PrendasDeLaAprobacion} lo exige. Las numéricas se cuentan
   * sobre la escala de la categoría donde se publica.
   *
   * @throws TallaDelProveedorDesconocidaException con la primera que no se puede contar
   */
  private void exigirTallasQueSeSabenPedir(
      AjusteDeTalla ajuste, List<String> valoresDeTalla, Categoria categoria) {
    if (ajuste.ninguno()) {
      return;
    }
    List<String> escala =
        categoria.escalaEfectiva(categoria.padreId().flatMap(repositorioCategorias::buscarPorId));
    for (String talla : valoresDeTalla) {
      if (ajuste.tallaDelProveedor(talla, escala).isEmpty()) {
        throw new TallaDelProveedorDesconocidaException(talla, ajuste);
      }
    }
  }

  private static List<String> valoresDeTalla(Tallas tallas) {
    return switch (tallas.tipo()) {
      case LISTA -> tallas.valores();
      // La talla única es una talla: la ficha y la tarjeta la dicen, y el carrito la muestra.
      case UNICA -> List.of(TALLA_UNICA);
      case DESCONOCIDA -> List.of();
    };
  }

  /**
   * Las tallas que hay de un tono ({@link TallasPorTono#tallasPara}): «Talla ML(cocoa)» deja al
   * cocoa sin SM aunque la blusa se apruebe en SM y ML (Violeta, 10 de octubre de 2026). El tono se
   * busca por el nombre con que se aprueba y por el color que la lectura de fotos vio en las fotos
   * de ese tono, que es el del proveedor.
   */
  private static List<String> tallasDelTono(
      BorradorProducto borrador,
      AprobarBorradorComando comando,
      List<String> tonoPorFoto,
      String tono,
      List<String> ejesDeTalla) {
    List<String> nombres = new ArrayList<>();
    nombres.add(tono);
    for (int i = 0; i < comando.fotos().size(); i++) {
      String sugerido = borrador.tonosSugeridos().get(comando.fotos().get(i).mensajeId());
      if (tono.equals(tonoPorFoto.get(i)) && sugerido != null && !nombres.contains(sugerido)) {
        nombres.add(sugerido);
      }
    }
    return borrador.tallasPorTono().tallasPara(nombres, ejesDeTalla);
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
      List<String> tonoPorFoto,
      List<List<String>> muestraPorFoto,
      boolean conHuellaVisual) {
    List<String> subidas = new ArrayList<>();
    try {
      return publicarFotos(
          producto,
          comando,
          fotos,
          variantePorTono,
          tonoPorFoto,
          muestraPorFoto,
          conHuellaVisual,
          subidas);
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
      List<String> tonoPorFoto,
      List<List<String>> muestraPorFoto,
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
      // Por el tono **numerado** de esta foto, no por el que trae el comando: dos prendas del
      // mismo color son dos variantes distintas, y buscar por el nombre sin numerar las colgaría
      // de la misma. Las fotos de una misma prenda sí comparten valor, y con él la variante.
      //
      // <b>La principal también cuelga de su tono, y hasta el 7 de octubre de 2026 no.</b> Se le
      // forzaba el nulo porque el índice único de PRINCIPAL solo cubría las de variante nula, y
      // con eso se descartaba el color que quien revisa le había marcado a esa foto. Ahora ese
      // color decide algo: una principal que vale para todos los tonos encabeza la tarjeta del
      // catálogo y se queda fuera de la galería de la ficha; una con color, además la abre.
      // `V87` ensanchó el índice a todas las PRINCIPAL, así que la invariante no se afloja.
      String tono = tonoPorFoto.get(orden);
      UUID varianteId = tono == null ? null : variantePorTono.get(tono);
      // Además de su tono, las prendas sin foto propia que se ven en ella y, en la principal, si
      // entra en la ficha (ADR-0082); con tono ya entra, y el dominio no guarda la casilla.
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
              varianteId,
              principal && comando.principalEnLaFicha(),
              muestraPorFoto.get(orden).stream().map(variantePorTono::get).toList());
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
    // Las sumadas de otras publicaciones: se buscan por id, porque su lote es otro.
    for (MensajeProveedor agregada : repositorioMensajes.buscarPorIds(borrador.fotosAgregadas())) {
      agregada.referenciaArchivo().ifPresent(r -> archivos.put(agregada.id(), r));
    }
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
    return principal
        ? ClavesDePrincipal.nueva(productoId, extension)
        : ClavesDeGaleria.nueva(productoId, extension);
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
