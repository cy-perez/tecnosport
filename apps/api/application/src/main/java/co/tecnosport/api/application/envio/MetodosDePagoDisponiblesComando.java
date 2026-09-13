package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.util.List;
import java.util.UUID;

/**
 * {@code lineas} solo trae {@code varianteId} y {@code cantidad}, igual que {@code
 * CrearPedidoComando}: precio y categoría los resuelve este caso de uso contra el catálogo real.
 *
 * <p>{@code tarifaConRecaudoYaCotizada} existe para que un pedido contraentrega no cotice dos
 * veces. {@code CrearPedido} tiene que cotizar con recaudo de todos modos —la tarifa que congela
 * debe ser de una transportadora que cobre en la puerta— así que pasa la suya y este caso de uso no
 * vuelve a preguntar. Sin eso eran dos llamadas con el mismo cuerpo que podían discrepar: si la
 * segunda volvía sin tarifa, el comprador recibía un 409 después de que el sistema le acabara de
 * decir que sí había contraentrega.
 *
 * <p>No llega nunca desde el cliente: el DTO HTTP no tiene ese campo y quien lo rellena es otro
 * caso de uso del servidor, con una tarifa que él mismo acaba de pedirle al proveedor.
 */
public record MetodosDePagoDisponiblesComando(
    List<LineaComando> lineas,
    String correo,
    TipoEntrega tipoEntrega,
    Direccion direccion,
    TarifaEnvio tarifaConRecaudoYaCotizada) {

  /** Lo que manda el endpoint: no ha cotizado nada, así que el caso de uso lo hace por él. */
  public MetodosDePagoDisponiblesComando(
      List<LineaComando> lineas, String correo, TipoEntrega tipoEntrega, Direccion direccion) {
    this(lineas, correo, tipoEntrega, direccion, null);
  }

  public record LineaComando(UUID varianteId, int cantidad) {}
}
