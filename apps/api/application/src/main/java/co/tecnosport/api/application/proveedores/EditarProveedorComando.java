package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.AjustesDeIngesta;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @param factorDeMargen nulo para usar el de la línea
 * @param tiposExcluidos los que no se le compran; vacío para comprarle todo
 * @param otrosRemitentes los nombres de sus otras cuentas en la exportación; vacío si no tiene
 * @param armaSets si la prenda que anuncia con su acompañante se publica como set
 * @param ajustesDeIngesta cómo se ingiere su chat; nulo es como el de todos
 * @param local si surte desde Medellín y lo suyo se puede recoger en el punto (ADR-0093)
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
    boolean armaSets,
    AjustesDeIngesta ajustesDeIngesta,
    boolean local,
    List<String> otrosRemitentes) {

  public EditarProveedorComando {
    tiposExcluidos = tiposExcluidos == null ? Set.of() : Set.copyOf(tiposExcluidos);
    ajustesDeIngesta = ajustesDeIngesta == null ? AjustesDeIngesta.POR_OMISION : ajustesDeIngesta;
    otrosRemitentes = otrosRemitentes == null ? List.of() : List.copyOf(otrosRemitentes);
  }

  /** Sin otras cuentas: publica solo desde la de su nombre en la exportación. */
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
      Set<TipoProductoProveedor> tiposExcluidos,
      boolean armaSets,
      AjustesDeIngesta ajustesDeIngesta,
      boolean local) {
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
        armaSets,
        ajustesDeIngesta,
        local,
        List.of());
  }

  /** Sin marcarlo local: lo suyo no se recoge en el punto. */
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
      Set<TipoProductoProveedor> tiposExcluidos,
      boolean armaSets,
      AjustesDeIngesta ajustesDeIngesta) {
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
        armaSets,
        ajustesDeIngesta,
        false);
  }

  /** Sin ajustes de ingesta: su chat se ingiere como el de todos. */
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
      Set<TipoProductoProveedor> tiposExcluidos,
      boolean armaSets) {
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
        armaSets,
        AjustesDeIngesta.POR_OMISION);
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
