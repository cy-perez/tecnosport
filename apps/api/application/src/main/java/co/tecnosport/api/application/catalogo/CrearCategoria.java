package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.Objects;
import java.util.Optional;

/**
 * Alta de una categoría desde el panel.
 *
 * <p>Hasta el 24 de septiembre de 2026 las categorías solo entraban por migración: {@code V38}
 * cargó las de tecnología y {@code V63} el árbol de ropa, calzado y bolsos. Ese razonamiento sigue
 * siendo el bueno para <b>lo que toda instalación necesita</b>, y por eso el árbol inicial es una
 * migración y no una siembra. Lo que no cubría es el caso de después: la subcategoría que pide el
 * proveedor del lunes. Obligar a escribir SQL y desplegar para poder cargar un producto convierte
 * un dato operativo en un cambio de esquema. Mismo argumento que {@link CrearMarca} y {@code
 * ADR-0047}.
 *
 * <p>Las tres reglas que defiende, y por qué ninguna vive en el agregado: las tres necesitan ver
 * <b>otra</b> fila —el padre, o la tabla entera— y un agregado que consulta al repositorio deja de
 * serlo.
 */
public final class CrearCategoria {

  private final RepositorioCategorias repositorioCategorias;

  public CrearCategoria(RepositorioCategorias repositorioCategorias) {
    this.repositorioCategorias =
        Objects.requireNonNull(
            repositorioCategorias, "El repositorio de categorías no puede ser nulo.");
  }

  public Categoria ejecutar(CrearCategoriaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Optional<Categoria> padre = padreDe(comando);
    Slug slug = slugDe(comando, padre);
    exigirSlugLibre(slug);

    Categoria categoria =
        padre
            .map(p -> Categoria.crearBajo(p, comando.nombre(), slug))
            .orElseGet(() -> Categoria.crear(comando.nombre(), slug, lineaObligatoria(comando)));

    repositorioCategorias.guardar(categoria);
    return categoria;
  }

  /**
   * El padre, ya validado. Las dos comprobaciones son las que hacen que el árbol siga siendo
   * recorrible: {@link ProfundidadDeCategoriaExcedidaException} y {@link
   * CategoriaConProductosException} explican cada una por qué.
   */
  private Optional<Categoria> padreDe(CrearCategoriaComando comando) {
    if (comando.padreId() == null) {
      return Optional.empty();
    }

    Categoria padre =
        repositorioCategorias
            .buscarPorId(comando.padreId())
            .orElseThrow(() -> new CategoriaNoEncontradaException(comando.padreId()));

    if (!padre.esRaiz()) {
      throw new ProfundidadDeCategoriaExcedidaException(padre.nombre());
    }
    if (repositorioCategorias.tieneProductos(padre.id())) {
      throw new CategoriaConProductosException(
          padre.nombre(), "no puede tener subcategorías debajo");
    }
    return Optional.of(padre);
  }

  /**
   * El slug que pidieron, o el derivado del nombre con el del padre delante.
   *
   * <p>El prefijo no es decoración: "Busos" y "Sudaderas" existen en Dama y en Caballero, y el slug
   * es único en toda la tabla porque el filtro de la vitrina viaja por él. Sin prefijo, la segunda
   * de las dos no se puede crear y el mensaje de error no diría por qué.
   *
   * <p>No se prueban sufijos numéricos como en {@link CrearProducto}: un {@code ropa-dama-busos-2}
   * silencioso es una categoría que nadie pidió con un nombre que nadie reconoce. Aquí choca y
   * quien administra decide.
   */
  private static Slug slugDe(CrearCategoriaComando comando, Optional<Categoria> padre) {
    if (comando.slug() != null && !comando.slug().isBlank()) {
      return new Slug(comando.slug().trim());
    }
    Slug delNombre = Slug.generarDesde(comando.nombre());
    return padre.map(p -> new Slug(p.slug().valor() + "-" + delNombre.valor())).orElse(delNombre);
  }

  private void exigirSlugLibre(Slug slug) {
    if (repositorioCategorias.buscarPorSlug(slug).isPresent()) {
      throw new CategoriaSlugYaExisteException(slug.valor());
    }
  }

  private static LineaCatalogo lineaObligatoria(CrearCategoriaComando comando) {
    if (comando.linea() == null) {
      throw new ExcepcionDeDominio(
          "Una categoría de primer nivel tiene que decir de qué línea cuelga.");
    }
    return comando.linea();
  }
}
