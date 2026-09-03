package co.tecnosport.api.bootstrap.envio;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code CONTRAENTREGA_HABILITADA}/{@code CONTRAENTREGA_MONTO_MAXIMO}/{@code
 * CONTRAENTREGA_CATEGORIAS_EXCLUIDAS} de docs/07-infra-gcp.md. Sin valor por defecto para {@code
 * montoMaximo} a propósito (regla dura #9: nada de datos de negocio inventados) — la aplicación no
 * arranca si falta.
 */
@ConfigurationProperties(prefix = "tecnosport.contraentrega")
public record PropiedadesContraentrega(
    boolean habilitada, long montoMaximo, List<String> categoriasExcluidas) {

  public PropiedadesContraentrega {
    if (montoMaximo <= 0) {
      throw new IllegalStateException(
          "tecnosport.contraentrega.monto-maximo debe ser mayor que cero.");
    }
    categoriasExcluidas =
        categoriasExcluidas == null ? List.of() : List.copyOf(categoriasExcluidas);
  }
}
