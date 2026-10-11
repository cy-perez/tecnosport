package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AgregarVarianteComando;
import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.CategoriaNoEncontradaException;
import co.tecnosport.api.application.catalogo.CategoriaNoEsHojaException;
import co.tecnosport.api.application.catalogo.MarcaNoEncontradaException;
import co.tecnosport.api.application.catalogo.ObjetoDeImagenNoEncontradoException;
import co.tecnosport.api.application.catalogo.ProductoNoEncontradoPorIdException;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.ValorAtributoComando;
import co.tecnosport.api.application.catalogo.VarianteCreada;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.AtributoDeCatalogoNoDefinidoException;
import co.tecnosport.api.application.proveedores.BorradorNoEditableException;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.ImagenProducto;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.catalogo.TipoImagen;
import co.tecnosport.api.domain.catalogo.VarianteDeImagen;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import co.tecnosport.api.domain.compartido.HashContenido;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
import co.tecnosport.api.domain.proveedores.FotoDeBorradorTecnologia;
import co.tecnosport.api.domain.proveedores.VarianteDeProveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Convierte un borrador de tecnología en catálogo: una variante por cada color elegido de cada
 * configuración, con sus atributos —RAM, almacenamiento, SIM y color—, el precio que fijó quien
 * revisa y la existencia de la lista.
 *
 * <p>Un borrador de un modelo nuevo crea el producto; uno de configuraciones nuevas las añade al
 * producto que ya existe. El producto nuevo <b>sale publicado</b> (ADR-0079): las fotos llegaron
 * con el borrador, quien aprueba ya las vio con los colores y el precio, y aprobar las copia del
 * bucket privado del proveedor al público —la primera como principal, las demás a la galería—. Las
 * variantes salen por {@link AgregarVariante}, para que el SKU, los atributos y el libro de
 * inventario sigan las mismas reglas que en el panel.
 *
 * <p>Si algo falla después de subir una foto al bucket público, se borra lo subido: la transacción
 * deshace las filas, pero no los archivos.
 *
 * <p>Quien llama abre la transacción.
 */
public final class AprobarBorradorTecnologia {

  static final String ATRIBUTO_COLOR = "color";
  static final String ATRIBUTO_RAM = "ram";
  static final String ATRIBUTO_ALMACENAMIENTO = "almacenamiento";
  static final String ATRIBUTO_SIM = "sim";

  private final RepositorioBorradoresTecnologia repositorioBorradores;
  private final RepositorioVariantesDeProveedor variantesDeProveedor;
  private final RepositorioProductos repositorioProductos;
  private final RepositorioProductosDeProveedor productosDeProveedor;
  private final RepositorioMarcas repositorioMarcas;
  private final RepositorioCategorias repositorioCategorias;
  private final RepositorioAtributos repositorioAtributos;
  private final AgregarVariante agregarVariante;
  private final AlmacenDeArchivosDeProveedor almacenPrivado;
  private final AlmacenDeImagenes almacenDeImagenes;
  private final int existenciaPorVariante;

  public AprobarBorradorTecnologia(
      RepositorioBorradoresTecnologia repositorioBorradores,
      RepositorioVariantesDeProveedor variantesDeProveedor,
      RepositorioProductos repositorioProductos,
      RepositorioProductosDeProveedor productosDeProveedor,
      RepositorioMarcas repositorioMarcas,
      RepositorioCategorias repositorioCategorias,
      RepositorioAtributos repositorioAtributos,
      AgregarVariante agregarVariante,
      AlmacenDeArchivosDeProveedor almacenPrivado,
      AlmacenDeImagenes almacenDeImagenes,
      int existenciaPorVariante) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.variantesDeProveedor = Objects.requireNonNull(variantesDeProveedor);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
    this.repositorioMarcas = Objects.requireNonNull(repositorioMarcas);
    this.repositorioCategorias = Objects.requireNonNull(repositorioCategorias);
    this.repositorioAtributos = Objects.requireNonNull(repositorioAtributos);
    this.agregarVariante = Objects.requireNonNull(agregarVariante);
    this.almacenPrivado = Objects.requireNonNull(almacenPrivado);
    this.almacenDeImagenes = Objects.requireNonNull(almacenDeImagenes);
    if (existenciaPorVariante < 1) {
      throw new IllegalArgumentException("La existencia por variante es por lo menos 1.");
    }
    this.existenciaPorVariante = existenciaPorVariante;
  }

  /** Devuelve el producto que nació, o el que ya existía con sus variantes nuevas. */
  public Producto ejecutar(AprobarBorradorTecnologiaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    UUID borradorId = comando.borradorId();
    UUID marcaId = comando.marcaId();
    UUID categoriaId = comando.categoriaId();
    BorradorTecnologia borrador =
        repositorioBorradores
            .buscarPorIdParaActualizar(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    // Antes de tocar el catálogo: lo que impide aprobar se sabe sin crear nada.
    borrador.exigirAprobable();
    // Un 422 que lo dice y no un 500: el cuerpo los trae opcionales a propósito, porque un
    // borrador de configuraciones nuevas no los usa.
    if (borrador.productoId().isEmpty() && (marcaId == null || categoriaId == null)) {
      throw new ExcepcionDeDominio(
          "Un modelo nuevo necesita la marca y la categoría del catálogo para aprobarse.");
    }
    Atributos atributos = atributos();

    Producto producto =
        borrador.productoId().isPresent()
            ? repositorioProductos
                .buscarPorId(borrador.productoId().get())
                .orElseThrow(
                    () -> new ProductoNoEncontradoPorIdException(borrador.productoId().get()))
            : crearProducto(borrador, marcaId, categoriaId);

    for (ConfiguracionTecnologia c : borrador.configuracionesQueSeVenden()) {
      for (String color : c.coloresElegidos()) {
        VarianteCreada creada =
            agregarVariante.ejecutar(
                new AgregarVarianteComando(
                    producto.id(),
                    c.skuDeVariante(color),
                    c.precioVenta().valor().longValueExact(),
                    BigDecimal.ZERO,
                    null,
                    existenciaPorVariante,
                    null,
                    null,
                    null,
                    null,
                    atributos.de(c, color)));
        variantesDeProveedor.guardar(
            new VarianteDeProveedor(
                creada.variante().id(),
                producto.id(),
                borrador.proveedorId(),
                c.sku(),
                color,
                c.costoProveedor(),
                borrador.vistoEn()));
      }
    }

    if (borrador.productoId().isEmpty()) {
      // Releído: agregarVariante dejó las variantes guardadas y el agregado en memoria no las ve.
      Producto conVariantes = repositorioProductos.buscarPorId(producto.id()).orElseThrow();
      publicarFotos(conVariantes, borrador);
      conVariantes.publicar();
      repositorioProductos.actualizar(conVariantes);
    }

    borrador.aprobar(producto.id());
    repositorioBorradores.actualizar(borrador);
    return repositorioProductos.buscarPorId(producto.id()).orElseThrow();
  }

  private void publicarFotos(Producto producto, BorradorTecnologia borrador) {
    List<String> subidas = new ArrayList<>();
    try {
      int orden = 0;
      for (FotoDeBorradorTecnologia foto : borrador.fotos()) {
        boolean principal = orden == 0;
        List<VarianteDeImagen> variantes = new ArrayList<>();
        for (FotoDeBorradorTecnologia.Variante v : foto.variantes()) {
          String key = copiar(producto.id(), principal, v.referencia(), subidas);
          variantes.add(
              new VarianteDeImagen(v.ancho(), almacenDeImagenes.urlPublica(key), v.bytes()));
        }
        String vistaPrevia =
            foto.referenciaVistaPrevia() == null
                ? null
                : almacenDeImagenes.urlPublica(
                    copiar(producto.id(), principal, foto.referenciaVistaPrevia(), subidas));
        ImagenProducto imagen =
            ImagenProducto.crear(
                principal ? TipoImagen.PRINCIPAL : TipoImagen.GALERIA,
                principal ? 0 : producto.siguienteOrdenDeGaleria(),
                variantes,
                vistaPrevia,
                foto.alto(),
                new HashContenido(foto.hash()),
                foto.altEs(),
                foto.altEn());
        if (principal) {
          producto.asignarImagenPrincipal(imagen);
          repositorioProductos.guardarImagenPrincipal(producto.id(), imagen);
        } else {
          producto.agregarImagenGaleria(imagen);
          repositorioProductos.guardarImagenDeGaleria(producto.id(), imagen);
        }
        orden++;
      }
    } catch (RuntimeException e) {
      subidas.forEach(almacenDeImagenes::eliminar);
      throw e;
    }
  }

  /**
   * Copia un archivo del bucket privado al público con la clave del producto —{@code
   * productos/{id}/principal-} o {@code galeria-}—, que es la que limpian el reemplazo de la
   * principal y el borrado de una imagen de galería.
   */
  private String copiar(
      UUID productoId, boolean principal, String referencia, List<String> subidas) {
    byte[] bytes =
        almacenPrivado
            .leer(referencia)
            .orElseThrow(() -> new ObjetoDeImagenNoEncontradoException(referencia));
    String key =
        "productos/"
            + productoId
            + "/"
            + (principal ? "principal-" : "galeria-")
            + GeneradorIdentificador.nuevo()
            + "."
            + ClavesDeFotosDeTecnologia.extensionDe(referencia);
    almacenDeImagenes.subir(key, ClavesDeFotosDeTecnologia.contentTypeDe(referencia), bytes);
    subidas.add(key);
    return key;
  }

  private Producto crearProducto(BorradorTecnologia borrador, UUID marcaId, UUID categoriaId) {
    Marca marca =
        repositorioMarcas
            .buscarPorId(marcaId)
            .orElseThrow(() -> new MarcaNoEncontradaException(marcaId));
    Categoria categoria =
        repositorioCategorias
            .buscarPorId(categoriaId)
            .orElseThrow(() -> new CategoriaNoEncontradaException(categoriaId));
    if (!repositorioCategorias.hijasDe(categoria.id()).isEmpty()) {
      throw new CategoriaNoEsHojaException(categoria.nombre());
    }
    // La importación ya habría colgado el borrador de este producto; llegar aquí con uno es una
    // carrera, y el índice único de la huella la cortaría con un error sin traducir.
    productosDeProveedor
        .buscarPorHuella(borrador.proveedorId(), borrador.huella())
        .ifPresent(
            existente -> {
              throw new ExcepcionDeDominio(
                  borrador.modelo().titulo()
                      + " ya está en el catálogo: vuelve a importar la lista.");
            });
    // Visto en la fecha de la lista, no al aprobar, y al revés que en las prendas: la existencia
    // sale de la lista, y lo que dice una lista vieja no se sabe si sigue siendo cierto. Con la
    // hora de la aprobación, además, la lista de ese mismo día —fechada a medianoche— quedaba más
    // vieja que el producto y no lo podía ni agotar ni renovar.
    Instant visto = borrador.vistoEn();
    Producto producto =
        Producto.crearDeProveedor(
            borrador.modelo().titulo(),
            slugDisponible(Slug.generarDesde(borrador.modelo().titulo())),
            borrador.modelo().descripcion(),
            marca,
            categoria,
            borrador.proveedorId(),
            borrador.costoMinimoDeLoQueSeVende(),
            borrador.huella(),
            visto);
    repositorioProductos.guardar(producto);
    return producto;
  }

  private Atributos atributos() {
    List<Atributo> todos = repositorioAtributos.listarTodas();
    return new Atributos(
        atributo(todos, ATRIBUTO_COLOR, TipoAtributo.COLOR),
        atributo(todos, ATRIBUTO_RAM, null),
        atributo(todos, ATRIBUTO_ALMACENAMIENTO, null),
        atributo(todos, ATRIBUTO_SIM, null));
  }

  private static Atributo atributo(List<Atributo> todos, String nombre, TipoAtributo tipo) {
    return todos.stream()
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

  /** Los cuatro atributos de una variante de tecnología; los que la lista no dice no se ponen. */
  private record Atributos(Atributo color, Atributo ram, Atributo almacenamiento, Atributo sim) {
    List<ValorAtributoComando> de(ConfiguracionTecnologia c, String valorColor) {
      List<ValorAtributoComando> valores = new ArrayList<>();
      valores.add(new ValorAtributoComando(color.id(), valorColor, null));
      if (c.ram() != null) {
        valores.add(new ValorAtributoComando(ram.id(), c.ram(), null));
      }
      if (c.almacenamiento() != null) {
        valores.add(new ValorAtributoComando(almacenamiento.id(), c.almacenamiento(), null));
      }
      if (c.sim() != null) {
        valores.add(new ValorAtributoComando(sim.id(), c.sim(), null));
      }
      return valores;
    }
  }
}
