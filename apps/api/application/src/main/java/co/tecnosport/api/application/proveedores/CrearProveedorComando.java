package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.util.Set;

/**
 * @param factorDeMargen nulo para usar el de la línea
 * @param tiposExcluidos los que no se le compran; vacío para comprarle todo
 */
public record CrearProveedorComando(
    String nombre,
    LineaCatalogo linea,
    String telefonoWhatsApp,
    String nombreEnExportacion,
    BigDecimal factorDeMargen,
    OrdenDePublicacion ordenDePublicacion,
    boolean dosChatsEnUnZip,
    Set<TipoProductoProveedor> tiposExcluidos) {

  public CrearProveedorComando {
    tiposExcluidos = tiposExcluidos == null ? Set.of() : Set.copyOf(tiposExcluidos);
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
