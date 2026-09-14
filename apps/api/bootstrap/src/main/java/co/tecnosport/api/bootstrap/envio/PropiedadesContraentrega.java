package co.tecnosport.api.bootstrap.envio;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code CONTRAENTREGA_HABILITADA}/{@code CONTRAENTREGA_MONTO_MINIMO}/{@code
 * CONTRAENTREGA_MONTO_MAXIMO}/{@code CONTRAENTREGA_CATEGORIAS_EXCLUIDAS} de docs/07-infra-gcp.md.
 * Sin valor por defecto para los dos montos a propósito (regla dura #9: nada de datos de negocio
 * inventados) — la aplicación no arranca si falta alguno.
 *
 * <p>El rango es el que reporta la ayuda pública de Skydropx, no una preferencia del negocio.
 * {@code montoMaximo} estuvo en 100.000 durante toda la fase, con una nota en el {@code
 * application.yml} diciendo textualmente que era un marcador de desarrollo y no un límite decidido
 * — una cifra provisional con la etiqueta puesta es mejor que una provisional disfrazada de real,
 * pero solo hasta que llega la de verdad.
 */
@ConfigurationProperties(prefix = "tecnosport.contraentrega")
public record PropiedadesContraentrega(
    boolean habilitada, long montoMinimo, long montoMaximo, List<String> categoriasExcluidas) {

  public PropiedadesContraentrega {
    if (montoMinimo <= 0) {
      throw new IllegalStateException(
          "tecnosport.contraentrega.monto-minimo debe ser mayor que cero.");
    }
    if (montoMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.contraentrega.monto-maximo debe ser mayor que cero.");
    }
    // El rango invertido lo rechaza también CriteriosContraentrega, pero decirlo aquí nombra la
    // propiedad concreta que hay que corregir en el despliegue.
    if (montoMinimo > montoMaximo) {
      throw new IllegalStateException(
          "tecnosport.contraentrega.monto-minimo ("
              + montoMinimo
              + ") no puede superar a monto-maximo ("
              + montoMaximo
              + ").");
    }
    categoriasExcluidas =
        categoriasExcluidas == null ? List.of() : List.copyOf(categoriasExcluidas);
  }
}
