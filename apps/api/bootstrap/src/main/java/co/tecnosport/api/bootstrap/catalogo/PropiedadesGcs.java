package co.tecnosport.api.bootstrap.catalogo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code GCS_BUCKET_IMAGENES}, {@code GCS_URL_PUBLICA} y {@code GCS_MINUTOS_URL_FIRMADA} de
 * docs/07-infra-gcp.md. Las credenciales no aparecen aquí: las resuelve el propio SDK de Google
 * Cloud vía {@code GOOGLE_APPLICATION_CREDENTIALS} — nunca se leen a mano en este código.
 */
@ConfigurationProperties(prefix = "tecnosport.gcs")
public record PropiedadesGcs(String bucketImagenes, String urlPublica, long minutosUrlFirmada) {

  private static final String ORIGEN_GCS = "https://storage.googleapis.com/";

  public PropiedadesGcs {
    if (bucketImagenes == null || bucketImagenes.isBlank()) {
      throw new IllegalStateException("tecnosport.gcs.bucket-imagenes no puede estar vacío.");
    }
    if (urlPublica == null || urlPublica.isBlank()) {
      throw new IllegalStateException("tecnosport.gcs.url-publica no puede estar vacía.");
    }
    // Las dos se fijan por separado y nada las ataba: dev subía a su bucket y guardaba URLs del de
    // local. Un CDN delante cambia el origen y ahí no hay nada que comparar; servido por GCS, el
    // bucket de la URL es el bucket donde se sube, o la ficha queda con una imagen que no existe.
    if (urlPublica.startsWith(ORIGEN_GCS) && !urlPublica.equals(ORIGEN_GCS + bucketImagenes)) {
      throw new IllegalStateException(
          "tecnosport.gcs.url-publica ("
              + urlPublica
              + ") no sirve el bucket donde se suben las imágenes ("
              + bucketImagenes
              + ").");
    }
    if (minutosUrlFirmada <= 0) {
      throw new IllegalStateException(
          "tecnosport.gcs.minutos-url-firmada debe ser mayor que cero.");
    }
  }
}
