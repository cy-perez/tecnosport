package co.tecnosport.api.presentation.proveedores.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Alta y edición de un proveedor. {@code activo} y {@code publicacionAutomatica} son envoltorios
 * porque en el alta no vienen —el proveedor nace activo y sin publicación automática— y un {@code
 * boolean} ausente no cae en {@code false}: revienta la deserialización (apps/api/CLAUDE.md).
 *
 * @param factorDeMargen nulo para usar el de la línea
 */
public record ProveedorPeticion(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nombre,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String linea,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String telefonoWhatsApp,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nombreEnExportacion,
    Boolean activo,
    Boolean publicacionAutomatica,
    BigDecimal factorDeMargen) {

  public ProveedorPeticion {
    Objects.requireNonNull(nombre, "El nombre es obligatorio.");
    Objects.requireNonNull(linea, "La línea es obligatoria.");
    Objects.requireNonNull(telefonoWhatsApp, "El teléfono de WhatsApp es obligatorio.");
    Objects.requireNonNull(nombreEnExportacion, "El nombre en la exportación es obligatorio.");
  }
}
