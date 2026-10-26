package co.tecnosport.api.bootstrap.proveedores;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code tecnosport.proveedores.lectura-fotos.*}: el lector de fotos de la ingesta (10 de octubre
 * de 2026). Habla con la misma API, con la misma clave y los mismos reintentos que el extractor
 * ({@link PropiedadesExtraccion}); aquí va lo que cambia: una petición con fotos tarda más y
 * devuelve más. Sin clave, o con {@code habilitada} en falso, no se leen fotos y la ingesta reparte
 * como antes.
 *
 * <p>{@code calzado} es el lector de las fotos de un proveedor de calzado (ADR-0090): otro modelo,
 * que reconoce la marca y el modelo de un tenis por la forma, con su propio tope de salida porque
 * razona antes de responder. El timeout es el mismo: con los reintentos tiene que caber en el tramo
 * (ADR-0080).
 */
@ConfigurationProperties(prefix = "tecnosport.proveedores.lectura-fotos")
public record PropiedadesLecturaFotos(
    boolean habilitada, String modelo, int maxTokens, Duration timeout, Calzado calzado) {

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
    if (calzado == null) {
      throw new IllegalStateException("Falta tecnosport.proveedores.lectura-fotos.calzado.");
    }
  }

  /**
   * @param esfuerzo el {@code output_config.effort} del modelo; en blanco para uno que no razona,
   *     como Haiku 4.5, que entonces lee con temperatura 0
   */
  public record Calzado(String modelo, int maxTokens, String esfuerzo) {
    public Calzado {
      if (modelo == null || modelo.isBlank()) {
        throw new IllegalStateException(
            "Falta tecnosport.proveedores.lectura-fotos.calzado.modelo.");
      }
      if (maxTokens <= 0) {
        throw new IllegalStateException(
            "tecnosport.proveedores.lectura-fotos.calzado.max-tokens debe ser mayor que cero.");
      }
    }
  }
}
