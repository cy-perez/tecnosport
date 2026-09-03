package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.Sku;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Congelada al crear el pedido (docs/02-modelo-datos.md): copia nombre, SKU, precio unitario, tasa
 * de IVA e imagen del catálogo en ese instante. Si el precio sube después, esta línea no cambia —
 * un pedido nunca relee el catálogo actual para reconstruir su total. {@code imagenUrl} puede ser
 * nula: no todo producto tiene imagen principal capturada como URL absoluta en el momento de
 * congelar.
 *
 * <p>{@code idReserva} referencia el movimiento {@code RESERVA} que {@code Inventario.reservar}
 * creó al confirmar el pedido — sin guardar ese id aquí no hay forma segura de saber cuál reserva
 * liberar cuando el pedido se rechaza en la entrega o el pago falla: dos pedidos distintos pueden
 * tener reservas pendientes de la misma variante al mismo tiempo, así que no se puede adivinar por
 * variante y cantidad.
 */
public record LineaPedido(
    UUID id,
    UUID varianteId,
    Sku sku,
    String nombre,
    int cantidad,
    Dinero precioUnitario,
    BigDecimal tasaIva,
    String imagenUrl,
    UUID idReserva) {

  public LineaPedido {
    Objects.requireNonNull(id, "El id de la línea no puede ser nulo.");
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    Objects.requireNonNull(sku, "El SKU no puede ser nulo.");
    Objects.requireNonNull(precioUnitario, "El precio unitario no puede ser nulo.");
    Objects.requireNonNull(tasaIva, "La tasa de IVA no puede ser nula.");
    Objects.requireNonNull(idReserva, "El id de la reserva no puede ser nulo.");
    if (nombre == null || nombre.isBlank()) {
      throw new ExcepcionDeDominio("El nombre congelado en la línea no puede estar vacío.");
    }
    if (cantidad <= 0) {
      throw new ExcepcionDeDominio("La cantidad de una línea de pedido debe ser mayor que cero.");
    }
    if (tasaIva.signum() < 0) {
      throw new ExcepcionDeDominio("La tasa de IVA no puede ser negativa.");
    }
  }

  public Dinero subtotal() {
    return Dinero.deCop(precioUnitario.valor().multiply(BigDecimal.valueOf(cantidad)));
  }
}
