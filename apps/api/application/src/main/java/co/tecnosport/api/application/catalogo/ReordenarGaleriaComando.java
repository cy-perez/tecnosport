package co.tecnosport.api.application.catalogo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * El orden que se quiere para la galería de un producto: sus imágenes, todas, en el orden en que
 * tienen que quedar.
 *
 * <p>La lista se copia al construir el comando. Sin eso, quien lo arma podría seguir cambiándola
 * mientras el caso de uso la recorre, y el orden grabado no sería el que se pidió.
 */
public record ReordenarGaleriaComando(UUID productoId, List<UUID> imagenIds) {

  public ReordenarGaleriaComando {
    Objects.requireNonNull(productoId, "El id del producto no puede ser nulo.");
    Objects.requireNonNull(imagenIds, "La lista de imágenes no puede ser nula.");
    imagenIds = List.copyOf(imagenIds);
  }
}
