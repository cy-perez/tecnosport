package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ColorDePaleta;
import java.util.List;
import java.util.Objects;

/** La paleta con que el panel marca el tono de cada foto de un borrador. */
public final class ListarPaletaDeColores {

  private final RepositorioPaletaDeColores repositorio;

  public ListarPaletaDeColores(RepositorioPaletaDeColores repositorio) {
    this.repositorio = Objects.requireNonNull(repositorio);
  }

  public List<ColorDePaleta> ejecutar() {
    return repositorio.listarTodos();
  }
}
