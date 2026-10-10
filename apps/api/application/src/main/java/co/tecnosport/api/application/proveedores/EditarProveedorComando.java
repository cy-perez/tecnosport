package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param factorDeMargen nulo para usar el de la línea
 */
public record EditarProveedorComando(
    UUID id,
    String nombre,
    LineaCatalogo linea,
    String telefonoWhatsApp,
    String nombreEnExportacion,
    boolean activo,
    boolean publicacionAutomatica,
    BigDecimal factorDeMargen,
    OrdenDePublicacion ordenDePublicacion,
    boolean dosChatsEnUnZip) {

  /** Con un solo chat, que es lo de casi todos. */
  public EditarProveedorComando(
      UUID id,
      String nombre,
      LineaCatalogo linea,
      String telefonoWhatsApp,
      String nombreEnExportacion,
      boolean activo,
      boolean publicacionAutomatica,
      BigDecimal factorDeMargen,
      OrdenDePublicacion ordenDePublicacion) {
    this(
        id,
        nombre,
        linea,
        telefonoWhatsApp,
        nombreEnExportacion,
        activo,
        publicacionAutomatica,
        factorDeMargen,
        ordenDePublicacion,
        false);
  }
}
