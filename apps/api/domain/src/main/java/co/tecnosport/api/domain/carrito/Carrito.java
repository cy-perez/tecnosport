package co.tecnosport.api.domain.carrito;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Vive 30 días (docs/02-modelo-datos.md), anónimo o de un usuario. No reserva inventario: eso
 * ocurre al iniciar el pago (docs/00-producto.md), fuera de este agregado.
 */
public final class Carrito {

  private final UUID id;
  private final UUID usuarioId;
  private final List<LineaCarrito> lineas;
  private final Instant creadoEn;

  public Carrito(UUID id, UUID usuarioId, List<LineaCarrito> lineas, Instant creadoEn) {
    this.id = Objects.requireNonNull(id, "El id del carrito no puede ser nulo.");
    this.usuarioId = usuarioId;
    this.lineas = new ArrayList<>(Objects.requireNonNullElse(lineas, List.of()));
    this.creadoEn = Objects.requireNonNull(creadoEn, "La fecha de creación no puede ser nula.");
  }

  /** {@code usuarioId} nulo: carrito anónimo. */
  public static Carrito crear(UUID usuarioId, Instant ahora) {
    return new Carrito(GeneradorIdentificador.nuevo(), usuarioId, List.of(), ahora);
  }

  public UUID id() {
    return id;
  }

  public Optional<UUID> usuarioId() {
    return Optional.ofNullable(usuarioId);
  }

  public List<LineaCarrito> lineas() {
    return List.copyOf(lineas);
  }

  public Instant creadoEn() {
    return creadoEn;
  }

  /** Si ya hay una línea con esa variante, suma la cantidad en vez de duplicar la línea. */
  public void agregarLinea(UUID varianteId, int cantidad) {
    Objects.requireNonNull(varianteId, "El id de la variante no puede ser nulo.");
    if (cantidad <= 0) {
      throw new ExcepcionDeDominio("La cantidad a agregar debe ser mayor que cero.");
    }
    for (int i = 0; i < lineas.size(); i++) {
      LineaCarrito existente = lineas.get(i);
      if (existente.varianteId().equals(varianteId)) {
        lineas.set(i, existente.conCantidad(existente.cantidad() + cantidad));
        return;
      }
    }
    lineas.add(new LineaCarrito(GeneradorIdentificador.nuevo(), varianteId, cantidad));
  }

  /** Vaciar una línea es {@link #eliminarLinea}, no poner la cantidad en cero aquí. */
  public void actualizarCantidad(UUID idLinea, int cantidad) {
    if (cantidad <= 0) {
      throw new ExcepcionDeDominio(
          "La cantidad debe ser mayor que cero; usa eliminarLinea para quitarla.");
    }
    int indice = indiceDeLinea(idLinea);
    lineas.set(indice, lineas.get(indice).conCantidad(cantidad));
  }

  public void eliminarLinea(UUID idLinea) {
    if (!lineas.removeIf(linea -> linea.id().equals(idLinea))) {
      throw new LineaCarritoNoEncontradaException(idLinea);
    }
  }

  private int indiceDeLinea(UUID idLinea) {
    for (int i = 0; i < lineas.size(); i++) {
      if (lineas.get(i).id().equals(idLinea)) {
        return i;
      }
    }
    throw new LineaCarritoNoEncontradaException(idLinea);
  }
}
