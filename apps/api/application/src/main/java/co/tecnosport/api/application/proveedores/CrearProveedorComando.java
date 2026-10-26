package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.AjustesDeIngesta;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.util.Set;

/**
 * @param factorDeMargen nulo para usar el de la línea
 * @param tiposExcluidos los que no se le compran; vacío para comprarle todo
 * @param armaSets si la prenda que anuncia con su acompañante se publica como set
 * @param ajustesDeIngesta cómo se ingiere su chat; nulo es como el de todos
 */
public record CrearProveedorComando(
    String nombre,
    LineaCatalogo linea,
    String telefonoWhatsApp,
    String nombreEnExportacion,
    BigDecimal factorDeMargen,
    OrdenDePublicacion ordenDePublicacion,
    boolean dosChatsEnUnZip,
    Set<TipoProductoProveedor> tiposExcluidos,
    boolean armaSets,
    AjustesDeIngesta ajustesDeIngesta) {

  public CrearProveedorComando {
    tiposExcluidos = tiposExcluidos == null ? Set.of() : Set.copyOf(tiposExcluidos);
    ajustesDeIngesta = ajustesDeIngesta == null ? AjustesDeIngesta.POR_OMISION : ajustesDeIngesta;
  }

  /** Sin ajustes de ingesta: su chat se ingiere como el de todos. */
  public CrearProveedorComando(
      String nombre,
      LineaCatalogo linea,
      String telefonoWhatsApp,
      String nombreEnExportacion,
      BigDecimal factorDeMargen,
      OrdenDePublicacion ordenDePublicacion,
      boolean dosChatsEnUnZip,
      Set<TipoProductoProveedor> tiposExcluidos,
      boolean armaSets) {
    this(
        nombre,
        linea,
        telefonoWhatsApp,
        nombreEnExportacion,
        factorDeMargen,
        ordenDePublicacion,
        dosChatsEnUnZip,
        tiposExcluidos,
        armaSets,
        AjustesDeIngesta.POR_OMISION);
  }

  /** Sin sets: la prenda que anuncia con su acompañante se publica sola. */
  public CrearProveedorComando(
      String nombre,
      LineaCatalogo linea,
      String telefonoWhatsApp,
      String nombreEnExportacion,
      BigDecimal factorDeMargen,
      OrdenDePublicacion ordenDePublicacion,
      boolean dosChatsEnUnZip,
      Set<TipoProductoProveedor> tiposExcluidos) {
    this(
        nombre,
        linea,
        telefonoWhatsApp,
        nombreEnExportacion,
        factorDeMargen,
        ordenDePublicacion,
        dosChatsEnUnZip,
        tiposExcluidos,
        false);
  }

  /** Sin tipos excluidos: se le compra todo lo que anuncia. */
  public CrearProveedorComando(
      String nombre,
      LineaCatalogo linea,
      String telefonoWhatsApp,
      String nombreEnExportacion,
      BigDecimal factorDeMargen,
      OrdenDePublicacion ordenDePublicacion,
      boolean dosChatsEnUnZip) {
    this(
        nombre,
        linea,
        telefonoWhatsApp,
        nombreEnExportacion,
        factorDeMargen,
        ordenDePublicacion,
        dosChatsEnUnZip,
        Set.of());
  }

  /** Con un solo chat, que es lo de casi todos. */
  public CrearProveedorComando(
      String nombre,
      LineaCatalogo linea,
      String telefonoWhatsApp,
      String nombreEnExportacion,
      BigDecimal factorDeMargen,
      OrdenDePublicacion ordenDePublicacion) {
    this(
        nombre,
        linea,
        telefonoWhatsApp,
        nombreEnExportacion,
        factorDeMargen,
        ordenDePublicacion,
        false);
  }
}
