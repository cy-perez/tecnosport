package co.tecnosport.api.application.catalogo;

/**
 * El slug de una categoría es único en todo el catálogo ({@code ux_categoria_slug} de {@code V1}),
 * y no solo entre hermanas: el filtro de la vitrina viaja por slug ({@code ?categoria=ropa-dama}),
 * así que dos ramas con el mismo slug serían dos ramas que el filtro no puede distinguir.
 *
 * <p>Vive en {@code application} y no en el dominio por lo mismo que {@link
 * MarcaYaExisteException}: la categoría que se está creando no puede ver el resto de la tabla.
 */
public final class CategoriaSlugYaExisteException extends RuntimeException {

  public CategoriaSlugYaExisteException(String slug) {
    super("Ya existe una categoría con el slug '" + slug + "'.");
  }
}
