package co.tecnosport.api.bootstrap.proveedores;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code tecnosport.proveedores.*}: la ingesta por WhatsApp.
 *
 * <p>{@code bucket} es el privado de los originales del proveedor, distinto del de imágenes y
 * también uno por ambiente (ADR-0058). Los topes de la exportación son parámetros técnicos y no
 * datos de negocio: el primero es lo que cabe abrir en memoria en una instancia de Cloud Run y el
 * segundo es la barandilla contra un zip que infle. La cola es corta a propósito: un lote tarda
 * minutos y el panel lo usa una persona.
 */
@ConfigurationProperties(prefix = "tecnosport.proveedores")
public record PropiedadesProveedores(
    String bucket,
    long minutosUrlFirmada,
    long exportacionMaximaBytes,
    long descomprimidoMaximoBytes,
    int colaDeIngestas) {

  public PropiedadesProveedores {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException("tecnosport.proveedores.bucket no puede estar vacío.");
    }
    if (minutosUrlFirmada <= 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.minutos-url-firmada debe ser mayor que cero.");
    }
    if (exportacionMaximaBytes <= 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.exportacion-maxima-bytes debe ser mayor que cero.");
    }
    if (descomprimidoMaximoBytes < exportacionMaximaBytes) {
      throw new IllegalStateException(
          "tecnosport.proveedores.descomprimido-maximo-bytes no puede ser menor que el tope del"
              + " zip: un zip nunca infla a menos de lo que pesa.");
    }
    if (colaDeIngestas <= 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.cola-de-ingestas debe ser mayor que cero.");
    }
  }
}
