package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.PHash;
import co.tecnosport.api.domain.proveedores.PrendaAcompanante;
import java.util.Objects;
import java.util.UUID;

/**
 * La huella visual con la que un producto existente entró al catálogo.
 *
 * @param acompananteDelSet si el producto es un set, la prenda con que se armó; nulo si es la
 *     prenda sola. La foto de un set es la de su prenda: un anuncio solo se compara con los
 *     productos de su misma clase, o el set renovaría la prenda sola con su costo, y la prenda sola
 *     al set con el suyo (12 de octubre de 2026)
 */
public record HuellaVisual(UUID productoId, PHash pHash, PrendaAcompanante acompananteDelSet) {

  public HuellaVisual {
    Objects.requireNonNull(productoId);
    Objects.requireNonNull(pHash);
  }

  /** La de una prenda sola: la forma de antes de los sets. */
  public HuellaVisual(UUID productoId, PHash pHash) {
    this(productoId, pHash, null);
  }
}
