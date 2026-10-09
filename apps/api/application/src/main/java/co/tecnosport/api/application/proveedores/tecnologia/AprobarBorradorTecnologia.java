package co.tecnosport.api.application.proveedores.tecnologia;

import co.tecnosport.api.application.catalogo.AgregarVariante;
import co.tecnosport.api.application.catalogo.AgregarVarianteComando;
import co.tecnosport.api.application.catalogo.CategoriaNoEncontradaException;
import co.tecnosport.api.application.catalogo.CategoriaNoEsHojaException;
import co.tecnosport.api.application.catalogo.MarcaNoEncontradaException;
import co.tecnosport.api.application.catalogo.ProductoNoEncontradoPorIdException;
import co.tecnosport.api.application.catalogo.RepositorioAtributos;
import co.tecnosport.api.application.catalogo.RepositorioCategorias;
import co.tecnosport.api.application.catalogo.RepositorioMarcas;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.catalogo.ValorAtributoComando;
import co.tecnosport.api.application.catalogo.VarianteCreada;
import co.tecnosport.api.application.proveedores.AtributoDeCatalogoNoDefinidoException;
import co.tecnosport.api.application.proveedores.BorradorNoEditableException;
import co.tecnosport.api.application.proveedores.BorradorNoEncontradoException;
import co.tecnosport.api.application.proveedores.RepositorioProductosDeProveedor;
import co.tecnosport.api.domain.catalogo.Atributo;
import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.Marca;
import co.tecnosport.api.domain.catalogo.Producto;
import co.tecnosport.api.domain.catalogo.TipoAtributo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Slug;
import co.tecnosport.api.domain.proveedores.BorradorTecnologia;
import co.tecnosport.api.domain.proveedores.ConfiguracionTecnologia;
import co.tecnosport.api.domain.proveedores.EstadoBorrador;
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
 * producto que ya existe. El producto nuevo <b>queda en borrador</b>: las fotos del modelo las sube
 * después {@code tools/importar-lista-tecnologia.mjs} desde la carpeta de fichas, y sin imagen
 * principal no se publica. Las variantes salen por {@link AgregarVariante}, para que el SKU, los
 * atributos y el libro de inventario sigan las mismas reglas que en el panel.
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
      int existenciaPorVariante) {
    this.repositorioBorradores = Objects.requireNonNull(repositorioBorradores);
    this.variantesDeProveedor = Objects.requireNonNull(variantesDeProveedor);
    this.repositorioProductos = Objects.requireNonNull(repositorioProductos);
    this.productosDeProveedor = Objects.requireNonNull(productosDeProveedor);
    this.repositorioMarcas = Objects.requireNonNull(repositorioMarcas);
    this.repositorioCategorias = Objects.requireNonNull(repositorioCategorias);
    this.repositorioAtributos = Objects.requireNonNull(repositorioAtributos);
    this.agregarVariante = Objects.requireNonNull(agregarVariante);
    if (existenciaPorVariante < 1) {
      throw new IllegalArgumentException("La existencia por variante es por lo menos 1.");
    }
    this.existenciaPorVariante = existenciaPorVariante;
  }

  /**
   * @param marcaId y {@code categoriaId}: los del catálogo, que elige quien aprueba con las que
   *     sugiere la skill a la vista. Solo cuentan para un modelo nuevo; un producto que ya existe
   *     conserva las suyas
   */
  public Producto ejecutar(UUID borradorId, UUID marcaId, UUID categoriaId) {
    BorradorTecnologia borrador =
        repositorioBorradores
            .buscarPorIdParaActualizar(borradorId)
            .orElseThrow(() -> new BorradorNoEncontradoException(borradorId));
    if (borrador.estado() != EstadoBorrador.EN_REVISION) {
      throw new BorradorNoEditableException(borrador.estado());
    }
    // Antes de tocar el catálogo: lo que impide aprobar se sabe sin crear nada.
    borrador.exigirAprobable();
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

    borrador.aprobar(producto.id());
    repositorioBorradores.actualizar(borrador);
    return repositorioProductos.buscarPorId(producto.id()).orElseThrow();
  }

  private Producto crearProducto(BorradorTecnologia borrador, UUID marcaId, UUID categoriaId) {
    Objects.requireNonNull(marcaId, "Un modelo nuevo necesita la marca del catálogo.");
    Objects.requireNonNull(categoriaId, "Un modelo nuevo necesita la categoría del catálogo.");
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
