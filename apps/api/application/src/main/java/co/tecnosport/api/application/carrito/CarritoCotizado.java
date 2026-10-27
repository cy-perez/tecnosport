package co.tecnosport.api.application.carrito;

import co.tecnosport.api.domain.compartido.Dinero;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * El carrito con los precios de hoy, sacados del catálogo y no de lo que el navegador recuerda.
 *
 * <p>{@code precioUnitario} y {@code subtotal} de una línea son nulos cuando la variante ya no se
 * vende: no hay precio que mostrar, y el pedido la rechazaría igual. El {@code subtotal} del
 * carrito suma solo las que sí.
 *
 * <p>Dos precios desde el ADR-0095: {@code precioUnitario} y {@code subtotal} son el publicado, el
 * que se paga con pasarela y el máximo que alguien paga; {@code precioContado} y {@code
 * subtotalContado}, el que se paga por transferencia, en el punto o contra entrega. Cuál se cobra
 * lo decide {@code CrearPedido} con el medio de pago.
 */
public record CarritoCotizado(List<LineaCotizada> lineas, Dinero subtotal, Dinero subtotalContado) {

  public CarritoCotizado {
    lineas = List.copyOf(Objects.requireNonNull(lineas));
    Objects.requireNonNull(subtotal);
    Objects.requireNonNull(subtotalContado);
  }

  public record LineaCotizada(
      UUID lineaId,
      UUID varianteId,
      int cantidad,
      Dinero precioUnitario,
      Dinero subtotal,
      Dinero precioContado,
      Dinero subtotalContado) {

    public boolean disponible() {
      return precioUnitario != null;
    }
  }
}
