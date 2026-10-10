package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.Categoria;
import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Hashtag;
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
    exigirPrefijoSiLoEscribieron(comando, slug, nuevoPadre);

    Categoria editada =
        conHashtagsDe(
            comando,
            actual
                .renombrada(comando.nombre(), slug)
                .movidaBajo(nuevoPadre, lineaDe(comando, nuevoPadre, actual)));

    repositorioCategorias.guardar(editada);
    return editada;
  }

  /**
   * Nulo es "no las toques"; la lista vacía es "bórralas todas". La diferencia importa porque el
   * panel puede guardar un cambio de nombre sin mandar el campo, y perder las etiquetas en ese
   * guardado sería un daño silencioso -- nadie mira los hashtags de una categoría al renombrarla.
   *
   * <p>La validación de la forma de cada etiqueta la hace {@link Hashtag}, no este caso de uso: un
   * guion o un espacio partirían la etiqueta en dos al publicarla, y ese error tiene que salir con
   * su explicación desde el sitio que conoce la regla.
   */
  private static Categoria conHashtagsDe(EditarCategoriaComando comando, Categoria categoria) {
    if (comando.hashtags() == null) {
      return categoria;
    }
    return categoria.conHashtags(comando.hashtags().stream().map(Hashtag::new).toList());
  }

  private static Slug slugDe(EditarCategoriaComando comando, Categoria actual) {
    return loEscribieron(comando) ? new Slug(comando.slug().trim()) : actual.slug();
  }

  private static boolean loEscribieron(EditarCategoriaComando comando) {
    return comando.slug() != null && !comando.slug().isBlank();
  }

  /**
   * Un slug <b>escrito</b> para una hija empieza por el de su rama, igual que al crear ({@link
   * SlugDeHijaSinPrefijoException}). Se comprueba después de validar el padre porque el prefijo que
   * manda es el del padre <b>nuevo</b>: quien mueve una categoría y le escribe el slug a la vez
   * tiene que escribir el de su destino.
   *
   * <p><b>Y solo cuando lo escriben</b>, que es la parte deliberada. El slug vacío significa
   * "déjalo como está", así que mover "Faldas" a Caballero le conserva {@code ropa-dama-faldas}: un
   * slug que ya miente sobre su rama, y a propósito, porque está en enlaces que la gente compartió
   * y que los buscadores indexaron. Esta regla vigila lo que alguien escribe hoy, no lo que la
   * historia dejó — hacer fallar el movimiento obligaría a romper esas URL para poder mover una
   * categoría de sitio.
   */
  private static void exigirPrefijoSiLoEscribieron(
      EditarCategoriaComando comando, Slug slug, Optional<Categoria> nuevoPadre) {
    if (!loEscribieron(comando)) {
      return;
    }
    nuevoPadre
        .filter(padre -> !padre.esPrefijoDe(slug))
        .ifPresent(
            padre -> {
              throw new SlugDeHijaSinPrefijoException(slug.valor(), padre.slug().valor());
            });
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
