package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * @param factorDeMargen nulo para usar el de la línea
 * @param tiposExcluidos los que no se le compran; vacío para comprarle todo
 * @param armaSets si la prenda que anuncia con su acompañante se publica como set
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
    boolean dosChatsEnUnZip,
    Set<TipoProductoProveedor> tiposExcluidos,
    boolean armaSets) {

  public EditarProveedorComando {
    tiposExcluidos = tiposExcluidos == null ? Set.of() : Set.copyOf(tiposExcluidos);
  }

  /** Sin sets: la prenda que anuncia con su acompañante se publica sola. */
  public EditarProveedorComando(
      UUID id,
      String nombre,
      LineaCatalogo linea,
      String telefonoWhatsApp,
      String nombreEnExportacion,
      boolean activo,
      boolean publicacionAutomatica,
      BigDecimal factorDeMargen,
      OrdenDePublicacion ordenDePublicacion,
      boolean dosChatsEnUnZip,
      Set<TipoProductoProveedor> tiposExcluidos) {
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
        dosChatsEnUnZip,
        tiposExcluidos,
        false);
  }

  /** Sin tipos excluidos: se le compra todo lo que anuncia. */
  public EditarProveedorComando(
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
        dosChatsEnUnZip,
        Set.of());
  }

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
