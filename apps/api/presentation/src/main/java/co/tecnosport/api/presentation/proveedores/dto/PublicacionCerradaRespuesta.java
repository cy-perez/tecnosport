package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.PublicacionCerrada;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Una publicación del lote que no dejó borrador. {@code codigoMotivo} es el de {@code
 * MotivoDePublicacion}, que el panel traduce (regla dura 4); {@code datoMotivo}, el valor que el
 * texto interpola, si hay uno.
 *
 * @param estado {@code DESCARTADA} o {@code ERROR}
 * @param extracto el comienzo del anuncio; nulo si eran solo fotos
 */
public record PublicacionCerradaRespuesta(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant fecha,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"DESCARTADA", "ERROR"})
        String estado,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String codigoMotivo,
    String datoMotivo,
    String extracto,
    int fotos) {

  public static PublicacionCerradaRespuesta de(PublicacionCerrada p) {
    return new PublicacionCerradaRespuesta(
        p.id(),
        p.fecha(),
        p.estado().name(),
        p.cierre().motivo().name(),
        p.cierre().dato(),
        p.extracto(),
        p.fotos());
  }
}
