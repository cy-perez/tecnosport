package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.domain.pedido.TipoEntrega;
import java.util.List;
import java.util.UUID;

/**
 * {@code lineas} solo trae {@code varianteId} y {@code cantidad}, igual que {@code
 * CrearPedidoComando}: precio y categoría los resuelve este caso de uso contra el catálogo real.
 */
public record MetodosDePagoDisponiblesComando(
    List<LineaComando> lineas, String correo, TipoEntrega tipoEntrega, Direccion direccion) {

  public record LineaComando(UUID varianteId, int cantidad) {}
}
