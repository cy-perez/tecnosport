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
 */
public record CarritoCotizado(List<LineaCotizada> lineas, Dinero subtotal) {

  public CarritoCotizado {
    lineas = List.copyOf(Objects.requireNonNull(lineas));
    Objects.requireNonNull(subtotal);
  }

  public record LineaCotizada(
      UUID lineaId, UUID varianteId, int cantidad, Dinero precioUnitario, Dinero subtotal) {

    public boolean disponible() {
      return precioUnitario != null;
    }
  }
}
