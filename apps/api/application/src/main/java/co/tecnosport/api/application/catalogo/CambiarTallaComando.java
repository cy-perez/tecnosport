package co.tecnosport.api.application.catalogo;

import java.util.Objects;
import java.util.UUID;

/**
 * @param modeloId una variante de la talla que se corrige —cualquiera de sus colores—
 * @param talla la talla como tiene que quedar
 */
public record CambiarTallaComando(UUID productoId, UUID modeloId, String talla) {

  public CambiarTallaComando {
    Objects.requireNonNull(productoId, "El id del producto no puede ser nulo.");
    Objects.requireNonNull(modeloId, "El id de la variante no puede ser nulo.");
  }
}
