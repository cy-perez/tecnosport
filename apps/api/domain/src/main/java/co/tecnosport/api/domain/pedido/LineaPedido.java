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
 *
 * <p>{@code detalleVariante} congela lo que la variante era —«Negro · M»— desde el 3 de octubre de
 * 2026, cuando la vitrina empezó a ofrecer tallas: con el SKU solo, quien despacha no sabía qué
 * talla empacar sin ir a buscarla. Nulo en las líneas anteriores y en un producto sin variaciones.
 *
 * <p>{@code precioPublicado} congela el precio anunciado de la variante al comprar, y {@code
 * precioUnitario} el que se cobra, que depende del medio de pago (ADR-0095): el publicado con
 * pasarela, el de contado sin ella. La diferencia es el descuento por pagar sin pasarela, y queda
 * guardada para poder auditarla. En las líneas anteriores al ADR-0095 los dos son el mismo.
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
    UUID idReserva,
    String detalleVariante,
    Dinero precioPublicado) {

  /** Sin descuento: el precio que se cobra es el publicado. Las líneas de antes del ADR-0095. */
  public LineaPedido(
      UUID id,
      UUID varianteId,
      Sku sku,
      String nombre,
      int cantidad,
      Dinero precioUnitario,
      BigDecimal tasaIva,
      String imagenUrl,
      UUID idReserva,
      String detalleVariante) {
    this(
        id,
        varianteId,
        sku,
        nombre,
        cantidad,
        precioUnitario,
        tasaIva,
        imagenUrl,
        idReserva,
        detalleVariante,
        precioUnitario);
  }

  /** Sin detalle de variante: las líneas de antes y un producto que no varía. */
  public LineaPedido(
      UUID id,
      UUID varianteId,
      Sku sku,
      String nombre,
      int cantidad,
      Dinero precioUnitario,
      BigDecimal tasaIva,
      String imagenUrl,
      UUID idReserva) {
    this(
        id, varianteId, sku, nombre, cantidad, precioUnitario, tasaIva, imagenUrl, idReserva, null);
  }

  public LineaPedido {
    detalleVariante =
        detalleVariante == null || detalleVariante.isBlank() ? null : detalleVariante.strip();
    Objects.requireNonNull(id, "El id de la línea no puede ser nulo.");
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    Objects.requireNonNull(sku, "El SKU no puede ser nulo.");
    Objects.requireNonNull(precioUnitario, "El precio unitario no puede ser nulo.");
    Objects.requireNonNull(precioPublicado, "El precio publicado no puede ser nulo.");
    if (precioPublicado.valor().compareTo(precioUnitario.valor()) < 0) {
      throw new ExcepcionDeDominio(
          "El precio que se cobra ("
              + precioUnitario.valor().toPlainString()
              + ") no puede superar el publicado ("
              + precioPublicado.valor().toPlainString()
              + "): el publicado es el máximo que alguien paga.");
    }
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

  /**
   * El nombre con lo que se eligió: «Bodi herraje (Negro · Única)». Lo que leen el correo y el
   * panel.
   */
  public String descripcion() {
    return detalleVariante == null ? nombre : nombre + " (" + detalleVariante + ")";
  }

  /** Lo que se cobra por la línea: el precio unitario por la cantidad. */
  public Dinero subtotal() {
    return Dinero.deCop(precioUnitario.valor().multiply(BigDecimal.valueOf(cantidad)));
  }

  /** Lo que la línea habría costado al precio anunciado. */
  public Dinero subtotalPublicado() {
    return Dinero.deCop(precioPublicado.valor().multiply(BigDecimal.valueOf(cantidad)));
  }

  /**
   * El descuento por pagar sin pasarela (ADR-0095): publicado menos cobrado, por la cantidad. Cero
   * con Wompi, con Sistecrédito y en las líneas anteriores.
   */
  public Dinero descuento() {
    return Dinero.deCop(subtotalPublicado().valor().subtract(subtotal().valor()));
  }
}
