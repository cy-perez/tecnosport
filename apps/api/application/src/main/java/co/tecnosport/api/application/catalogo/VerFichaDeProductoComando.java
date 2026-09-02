package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.compartido.Slug;
import java.util.Objects;

public record VerFichaDeProductoComando(Slug slug) {

  public VerFichaDeProductoComando {
    Objects.requireNonNull(slug, "El slug no puede ser nulo.");
  }
}
