package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.Contacto;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.MetodoPago;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.util.List;
import java.util.UUID;

/**
 * {@code lineas} solo trae {@code varianteId} y {@code cantidad}: precio, SKU, nombre e imagen los
 * decide el servidor con el catálogo real, nunca lo que traiga el cliente (docs/00-producto.md).
 *
 * <p>{@code autorizaDatos} y {@code direccionIp} son para la constancia de tratamiento de datos
 * (Ley 1581 de 2012): el checkout recoge nombre, dirección, teléfono y correo, así que aquí también
 * hace falta autorización expresa, no solo en el registro. La versión del texto la fija el
 * servidor.
 *
 * <p>{@code contacto} es a quién se entrega y a qué número se le avisa: lo exige la guía de la
 * transportadora y el mensajero de contraentrega, y también el retiro en punto, donde alguien
 * reclama el paquete con un nombre. Va aparte del correo porque el correo identifica al comprador y
 * el contacto a quien recibe, que no siempre son la misma persona.
 *
 * <p>{@code transportadora} es la que eligió el comprador en el checkout (ADR-0073), por nombre, o
 * nulo para la más económica. El costo no viaja: lo fija la cotización de este caso de uso.
 */
public record CrearPedidoComando(
    UUID usuarioId,
    String correo,
    Contacto contacto,
    List<LineaComando> lineas,
    TipoEntrega tipoEntrega,
    Direccion direccion,
    MetodoPago metodoPago,
    boolean autorizaDatos,
    String direccionIp,
    String transportadora) {

  /** Sin transportadora elegida: el pedido congela la más económica, como antes de ADR-0073. */
  public CrearPedidoComando(
      UUID usuarioId,
      String correo,
      Contacto contacto,
      List<LineaComando> lineas,
      TipoEntrega tipoEntrega,
      Direccion direccion,
      MetodoPago metodoPago,
      boolean autorizaDatos,
      String direccionIp) {
    this(
        usuarioId,
        correo,
        contacto,
        lineas,
        tipoEntrega,
        direccion,
        metodoPago,
        autorizaDatos,
        direccionIp,
        null);
  }

  public record LineaComando(UUID varianteId, int cantidad) {}
}
