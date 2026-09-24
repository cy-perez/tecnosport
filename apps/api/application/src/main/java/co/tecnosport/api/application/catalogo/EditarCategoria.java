package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Slug;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Renombrar una categoría y/o moverla de sitio.
 *
 * <p>El slug <b>no</b> se vuelve a derivar del nombre al renombrar, y es deliberado: el slug está
 * en URLs que la gente comparte y que los buscadores indexaron, así que cambiarlo solo porque
 * alguien corrigió una tilde rompería enlaces vivos sin avisar. Quien administra puede cambiarlo,
 * pero tiene que escribirlo.
 *
 * <p>Las cuatro reglas del movimiento —existe, no es su propia descendiente, el destino es raíz, el
 * destino no tiene productos— y la quinta, que es la que se olvida: <b>una rama con hojas no puede
 * pasar a ser hoja de otra</b>, porque quedaría un tercer nivel sin que nadie lo pidiera.
 */
public final class EditarCategoria {

  private final RepositorioCategorias repositorioCategorias;

  public EditarCategoria(RepositorioCategorias repositorioCategorias) {
    this.repositorioCategorias =
        Objects.requireNonNull(
            repositorioCategorias, "El repositorio de categorías no puede ser nulo.");
  }

  public Categoria ejecutar(EditarCategoriaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Categoria actual =
        repositorioCategorias
            .buscarPorId(comando.id())
            .orElseThrow(() -> new CategoriaNoEncontradaException(comando.id()));

    Slug slug = slugDe(comando, actual);
    exigirSlugLibre(slug, actual);

    Optional<Categoria> nuevoPadre = padreValidado(comando, actual);

    Categoria editada =
        actual
            .renombrada(comando.nombre(), slug)
            .movidaBajo(nuevoPadre, lineaDe(comando, nuevoPadre, actual));

    repositorioCategorias.guardar(editada);
    return editada;
  }

  private static Slug slugDe(EditarCategoriaComando comando, Categoria actual) {
    return comando.slug() == null || comando.slug().isBlank()
        ? actual.slug()
        : new Slug(comando.slug().trim());
  }

  /** Libre, o ya ocupado por ella misma — que es lo que pasa cuando solo se renombra. */
  private void exigirSlugLibre(Slug slug, Categoria actual) {
    repositorioCategorias
        .buscarPorSlug(slug)
        .filter(otra -> !otra.id().equals(actual.id()))
        .ifPresent(
            otra -> {
              throw new CategoriaSlugYaExisteException(slug.valor());
            });
  }

  private Optional<Categoria> padreValidado(EditarCategoriaComando comando, Categoria actual) {
    if (comando.padreId() == null) {
      return Optional.empty();
    }
    if (comando.padreId().equals(actual.id())) {
      throw new CicloDeCategoriasException(actual.nombre());
    }

    Categoria destino =
        repositorioCategorias
            .buscarPorId(comando.padreId())
            .orElseThrow(() -> new CategoriaNoEncontradaException(comando.padreId()));

    // Con el tope de dos niveles esto ya lo impide `esRaiz`, y se comprueba igual: el día que el
    // tope suba, colgar a una madre de su propia hija deja de ser imposible por accidente.
    if (destino.padreId().filter(actual.id()::equals).isPresent()) {
      throw new CicloDeCategoriasException(actual.nombre());
    }
    if (!destino.esRaiz()) {
      throw new ProfundidadDeCategoriaExcedidaException(destino.nombre());
    }
    if (repositorioCategorias.tieneProductos(destino.id())) {
      throw new CategoriaConProductosException(
          destino.nombre(), "no puede tener subcategorías debajo");
    }

    List<Categoria> hijas = repositorioCategorias.hijasDe(actual.id());
    if (!hijas.isEmpty()) {
      throw new ProfundidadDeCategoriaExcedidaException(actual.nombre());
    }
    return Optional.of(destino);
  }

  /**
   * La línea solo se elige cuando la categoría queda de primer nivel; con padre la hereda, y {@link
   * Categoria#movidaBajo} la ignora. Se conserva la actual si no la mandan, para que un formulario
   * que solo trae el nombre no la borre.
   */
  private static LineaCatalogo lineaDe(
      EditarCategoriaComando comando, Optional<Categoria> nuevoPadre, Categoria actual) {
    if (nuevoPadre.isPresent()) {
      return nuevoPadre.get().linea();
    }
    LineaCatalogo elegida = comando.linea() == null ? actual.linea() : comando.linea();
    if (elegida == null) {
      throw new ExcepcionDeDominio(
          "Una categoría de primer nivel tiene que decir de qué línea cuelga.");
    }
    return elegida;
  }
}
