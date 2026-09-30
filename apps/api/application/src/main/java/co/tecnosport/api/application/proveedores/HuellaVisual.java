package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.PHash;
import java.util.Objects;
import java.util.UUID;

/** La huella visual con la que un producto existente entró al catálogo. */
public record HuellaVisual(UUID productoId, PHash pHash) {

  public HuellaVisual {
    Objects.requireNonNull(productoId);
    Objects.requireNonNull(pHash);
  }
}
