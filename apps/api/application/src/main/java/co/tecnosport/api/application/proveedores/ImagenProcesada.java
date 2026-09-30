package co.tecnosport.api.application.proveedores;

import java.util.Objects;

/** Una foto lista para publicar: los bytes, su tipo y sus medidas reales. */
public record ImagenProcesada(byte[] bytes, String contentType, int ancho, int alto) {

  public ImagenProcesada {
    Objects.requireNonNull(bytes, "Los bytes no pueden ser nulos.");
    Objects.requireNonNull(contentType, "El tipo de contenido no puede ser nulo.");
    if (bytes.length == 0 || ancho <= 0 || alto <= 0) {
      throw new IllegalArgumentException("Una imagen procesada tiene bytes y medidas positivas.");
    }
  }
}
