package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.proveedores.Proveedor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProveedorRespuesta(
    UUID id,
    String nombre,
    String linea,
    String telefonoWhatsApp,
    String nombreEnExportacion,
    boolean activo,
    boolean publicacionAutomatica,
    BigDecimal factorDeMargen,
    String ordenDePublicacion,
    boolean dosChatsEnUnZip,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> tiposExcluidos,
    boolean armaSets,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AjustesDeIngestaDto ajustesDeIngesta) {

  public static ProveedorRespuesta de(Proveedor p) {
    return new ProveedorRespuesta(
        p.id(),
        p.nombre(),
        p.linea().name(),
        p.telefonoWhatsApp(),
        p.nombreEnExportacion(),
        p.activo(),
        p.publicacionAutomatica(),
        p.factorDeMargen().orElse(null),
        p.ordenDePublicacion().name(),
        p.subeDosChatsEnUnZip(),
        // En el orden del enum, que es el del formulario: un Set.of no tiene orden estable.
        p.tiposExcluidos().stream().sorted().map(Enum::name).toList(),
        p.armaSets(),
        AjustesDeIngestaDto.de(p.ajustesDeIngesta()));
  }
}
