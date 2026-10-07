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
import java.util.Collections;
import java.util.HashMap;
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

    // El tono que le toca a cada foto, por posición. Se calcula una vez y lo usan las dos mitades
    // —crear las variantes y publicar las fotos—, porque tienen que coincidir exactamente. Y antes
    // de crear nada: una prenda incoherente es un error del cuerpo y no debe dejar producto.
    List<String> tonoPorFoto = tonosPorPrenda(comando.fotos());

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
    repositorioProductos.guardar(producto);

    Map<String, UUID> variantePorTono = crearVariantes(producto, comando, borrador, tonoPorFoto);

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
            borrador.pHash().isEmpty());
    conVariantes.publicar();
    repositorioProductos.actualizar(conVariantes);

    borrador.aprobar(conVariantes.id(), pHashDeLaPrincipal);
    repositorioBorradores.actualizar(borrador);
    return conVariantes;
  }

  /**
   * El valor del atributo Color que le toca a cada foto, por posición; nulo donde la foto no lleva
   * tono. <b>Una variante es una prenda</b>, y una prenda puede tener varias fotos: las que traen
   * el mismo número de {@code prenda} cuelgan de la misma variante. <b>Un tono que se repite entre
   * prendas distintas se numera</b>: dos prendas «Negro» salen como «Negro 1» y «Negro 2»; el que
   * sale en una sola prenda se queda como está, tenga las fotos que tenga.
   *
   * <p>Hasta el 6 de octubre de 2026 los tonos se agrupaban con {@code distinct()}: dos fotos del
   * mismo color colapsaban en una variante. Bien cuando eran dos vistas de la misma prenda; mal
   * cuando eran dos prendas que el proveedor llamó igual — un jean con cuatro diseños negros
   * ofrecía un solo círculo. Del 6 al 7 de octubre cada foto con tono fue su propia variante, y
   * entonces se rompía lo contrario: dos ángulos de la misma prenda salían como «Rojo 1» y «Rojo
   * 2». <b>El color no dice cuál de los dos casos es, ni en un sentido ni en el otro</b>; lo dice
   * quien mira las fotos, y por eso la prenda llega explícita (ADR-0070).
   *
   * <p>Una foto con tono y sin prenda es una prenda ella sola: es lo que hacía el contrato antes de
   * tener el campo, y un cliente que no lo manda sigue obteniendo lo mismo.
   *
   * <p><b>Numerar es feo y es lo correcto</b>: la variante se identifica por el valor del atributo,
   * y ese valor es lo que el pedido congela ({@code LineaPedido.detalleVariante}) y lo que lee
   * quien empaca. Dos variantes con el mismo texto se leen igual en el carrito, en el correo y en
   * la guía, y nadie sabría cuál de las dos prendas meter en la caja.
   *
   * @throws PrendaIncoherenteException si una prenda no tiene color o sus fotos traen dos distintos
   */
  private static List<String> tonosPorPrenda(List<AprobarBorradorComando.FotoAprobada> fotos) {
    List<String> prendaPorFoto = new ArrayList<>(fotos.size());
    Map<String, String> tonoPorPrenda = new LinkedHashMap<>();
    Map<String, String> hexPorPrenda = new HashMap<>();
    for (int i = 0; i < fotos.size(); i++) {
      AprobarBorradorComando.FotoAprobada foto = fotos.get(i);
      String prenda;
      if (foto.prenda() != null) {
        if (foto.tono() == null) {
          throw new PrendaIncoherenteException(foto.prenda(), "no tiene color");
        }
        prenda = "prenda-" + foto.prenda();
      } else {
        // Sin prenda: con tono es una prenda sola; sin tono vale para todas.
        prenda = foto.tono() == null ? null : "foto-" + i;
      }
      prendaPorFoto.add(prenda);
      if (prenda == null) {
        continue;
      }
      String tonoPrevio = tonoPorPrenda.putIfAbsent(prenda, foto.tono());
      String hexPrevio =
          foto.colorHex() == null ? null : hexPorPrenda.putIfAbsent(prenda, foto.colorHex());
      if ((tonoPrevio != null && !tonoPrevio.equals(foto.tono()))
          || (hexPrevio != null && !hexPrevio.equalsIgnoreCase(foto.colorHex()))) {
        throw new PrendaIncoherenteException(foto.prenda(), "tiene fotos de dos colores");
      }
    }

    Map<String, Long> prendasPorTono =
        tonoPorPrenda.values().stream()
            .collect(Collectors.groupingBy(tono -> tono, Collectors.counting()));
    Map<String, Integer> vistos = new HashMap<>();
    Map<String, String> valorPorPrenda = new HashMap<>();
    tonoPorPrenda.forEach(
        (prenda, tono) -> {
          int cual = vistos.merge(tono, 1, Integer::sum);
          valorPorPrenda.put(prenda, prendasPorTono.get(tono) == 1 ? tono : tono + " " + cual);
        });

    List<String> porFoto = new ArrayList<>(fotos.size());
    for (String prenda : prendaPorFoto) {
      porFoto.add(prenda == null ? null : valorPorPrenda.get(prenda));
    }
    // `Collections.unmodifiableList` y no `List.copyOf`: la lista lleva nulos en las fotos sin
    // tono, y `List.copyOf` los rechaza.
    return Collections.unmodifiableList(porFoto);
  }

  /**
   * Una variante por prenda —con su tono ya numerado si se repite—; y por talla cuando el borrador
   * trae una lista. Sin tonos, una sola.
   */
  private Map<String, UUID> crearVariantes(
      Producto producto,
      AprobarBorradorComando comando,
      BorradorProducto borrador,
      List<String> tonoPorFoto) {
    List<String> tonos = tonoPorFoto.stream().filter(Objects::nonNull).distinct().toList();
    Map<String, String> hexPorTono = new LinkedHashMap<>();
    for (int i = 0; i < comando.fotos().size(); i++) {
      String tono = tonoPorFoto.get(i);
      String hex = comando.fotos().get(i).colorHex();
      if (tono != null && hex != null) {
        hexPorTono.putIfAbsent(tono, hex);
      }
    }
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
      List<String> tonoPorFoto,
      boolean conHuellaVisual) {
    List<String> subidas = new ArrayList<>();
    try {
      return publicarFotos(
          producto, comando, fotos, variantePorTono, tonoPorFoto, conHuellaVisual, subidas);
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
