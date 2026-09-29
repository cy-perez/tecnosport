package co.tecnosport.api.presentation.difusion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;

/**
 * En qué redes se difunde y, si alguien lo editó, con qué texto.
 *
 * <p>{@code redes} es obligatoria y la guarda es el {@code Objects.requireNonNull} de aquí abajo,
 * no una anotación: en esta capa <b>no hay Bean Validation</b> —no hay proveedor en el classpath— y
 * Jackson 3 deja en nulo un componente de tipo referencia que falte en vez de reventar. El
 * {@code @Schema} no valida nada; lo que hace es que el OpenAPI publicado diga lo que el servidor
 * de verdad exige, y con él el cliente TypeScript generado.
 *
 * <p>{@code pieDeFoto} ausente significa "arma el que toque", que es el camino normal.
 */
public record DifundirProductoPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> redes, String pieDeFoto) {

  public DifundirProductoPeticion {
    Objects.requireNonNull(redes, "Hay que decir en qué redes se difunde.");
    if (redes.isEmpty()) {
      throw new IllegalArgumentException("Hay que decir en qué redes se difunde.");
    }
  }
}
