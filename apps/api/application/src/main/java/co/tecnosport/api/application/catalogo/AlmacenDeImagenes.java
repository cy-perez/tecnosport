package co.tecnosport.api.application.catalogo;

import java.util.Optional;

/**
 * Puerto de almacenamiento de imágenes. Implementación de producción: Cloud Storage con URL firmada
 * — las imágenes se suben directo desde el navegador, nunca pasan por el backend
 * (docs/07-infra-gcp.md).
 */
public interface AlmacenDeImagenes {

  UrlFirmada generarUrlDeSubida(String objectKey, String contentType);

  /** Vacío si el objeto no existe todavía — quien llama lo interpreta como "no se subió". */
  Optional<Long> tamanoBytes(String objectKey);

  String urlPublica(String objectKey);
}
