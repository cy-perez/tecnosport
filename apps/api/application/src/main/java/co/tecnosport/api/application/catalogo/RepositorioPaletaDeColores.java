package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.ColorDePaleta;
import java.util.List;

/** Los colores con que se marca el tono de cada foto, en su orden. */
public interface RepositorioPaletaDeColores {

  List<ColorDePaleta> listarTodos();
}
