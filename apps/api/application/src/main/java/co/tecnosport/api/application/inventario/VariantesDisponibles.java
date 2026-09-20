package co.tecnosport.api.application.inventario;

import java.util.Set;
import java.util.UUID;

/**
 * Qué variantes, de las que se preguntaron, se pueden comprar ahora mismo.
 *
 * <p>Un conjunto de las disponibles y no un mapa de saldos, porque la vitrina publica un booleano
 * (adr/0050): el nivel de inventario no es asunto de quien mira la página, y un número que sale al
 * render ya está viejo cuando alguien hace clic. La <b>ausencia</b> de una variante aquí significa
 * agotada, y cubre los dos casos que dan lo mismo de cara al comprador: la que tiene libro sin
 * saldo y la que no tiene libro.
 */
public record VariantesDisponibles(Set<UUID> ids) {

  public VariantesDisponibles {
    ids = Set.copyOf(ids);
  }

  public static VariantesDisponibles ninguna() {
    return new VariantesDisponibles(Set.of());
  }

  public boolean hay(UUID varianteId) {
    return ids.contains(varianteId);
  }
}
