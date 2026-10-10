package co.tecnosport.api.bootstrap.proveedores;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code tecnosport.proveedores.lectura-fotos.*}: el lector de fotos de la ingesta (10 de octubre
 * de 2026). Habla con la misma API, con la misma clave y los mismos reintentos que el extractor
 * ({@link PropiedadesExtraccion}); aquí va lo que cambia: una petición con fotos tarda más y
 * devuelve más. Sin clave, o con {@code habilitada} en falso, no se leen fotos y la ingesta reparte
 * como antes.
 */
@ConfigurationProperties(prefix = "tecnosport.proveedores.lectura-fotos")
public record PropiedadesLecturaFotos(
    boolean habilitada, String modelo, int maxTokens, Duration timeout) {

  public PropiedadesLecturaFotos {
    if (modelo == null || modelo.isBlank()) {
      throw new IllegalStateException("Falta tecnosport.proveedores.lectura-fotos.modelo.");
    }
    if (maxTokens <= 0) {
      throw new IllegalStateException(
          "tecnosport.proveedores.lectura-fotos.max-tokens debe ser mayor que cero.");
    }
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalStateException(
          "tecnosport.proveedores.lectura-fotos.timeout debe ser una duración positiva.");
    }
  }
}
