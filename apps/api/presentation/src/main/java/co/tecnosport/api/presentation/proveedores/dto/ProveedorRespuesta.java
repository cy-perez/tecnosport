package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.proveedores.Proveedor;
import java.math.BigDecimal;
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
    boolean dosChatsEnUnZip) {

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
        p.subeDosChatsEnUnZip());
  }
}
