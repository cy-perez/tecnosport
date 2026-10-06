package co.tecnosport.api.domain.proveedores;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Una foto que quien revisa subió al borrador desde el panel, porque la ingesta no trajo ninguna o
 * las que trajo no sirven.
 *
 * <p>No es un {@link MensajeProveedor}: el mensaje es lo que el proveedor mandó, tal como llegó, y
 * esta no la mandó nadie. Tampoco es de la publicación: un mensaje con varios productos da varios
 * borradores sobre la misma publicación, y la foto que alguien sube para uno no es de los otros. Es
 * del borrador, y se va con él.
 *
 * @param referenciaArchivo la key en el bucket privado del proveedor
 */
public record FotoSubida(UUID id, String referenciaArchivo, Instant subidaEn) {

  public FotoSubida {
    Objects.requireNonNull(id, "La foto subida tiene id.");
    Objects.requireNonNull(subidaEn, "La foto subida tiene fecha.");
    if (referenciaArchivo == null || referenciaArchivo.isBlank()) {
      throw new IllegalArgumentException("La foto subida nombra su archivo.");
    }
  }
}
