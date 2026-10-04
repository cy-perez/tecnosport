package co.tecnosport.api.domain.catalogo;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que deja {@link Producto#usarImagenDeGaleriaComoPrincipal}: la foto que salió de la galería,
 * la principal nueva y, si había una principal antes, esa misma convertida en foto de la galería.
 */
public record IntercambioDePrincipal(
    UUID imagenDeGaleriaQuitada, ImagenProducto nuevaPrincipal, ImagenProducto anteriorEnGaleria) {

  public IntercambioDePrincipal {
    Objects.requireNonNull(imagenDeGaleriaQuitada, "La imagen quitada no puede ser nula.");
    Objects.requireNonNull(nuevaPrincipal, "La principal nueva no puede ser nula.");
  }

  public Optional<ImagenProducto> anteriorEnLaGaleria() {
    return Optional.ofNullable(anteriorEnGaleria);
  }
}
