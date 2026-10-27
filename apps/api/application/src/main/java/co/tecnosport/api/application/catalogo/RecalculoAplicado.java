package co.tecnosport.api.application.catalogo;

import java.util.List;
import java.util.Objects;

/**
 * Lo que hizo el recálculo con la lista revisada: las que grabó y las que dejó como estaban porque
 * cambiaron entre la revisión y la aplicación —otro precio de contado, otro publicado u otra
 * tarifa—. Las omitidas hay que volver a revisarlas.
 */
public record RecalculoAplicado(List<PrecioRevisado> recalculadas, List<PrecioRevisado> omitidas) {

  public RecalculoAplicado {
    recalculadas = List.copyOf(Objects.requireNonNull(recalculadas));
    omitidas = List.copyOf(Objects.requireNonNull(omitidas));
  }
}
