package co.tecnosport.api.application.carrito;

import co.tecnosport.api.application.compartido.Reloj;
import java.time.Duration;
import java.util.Objects;

/**
 * Borra los carritos sin actividad en los últimos {@code retencion} días (docs/02-modelo-datos.md:
 * "vive 30 días"). Hasta la Fase 6 esa frase no la cumplía nadie y un carrito anónimo se quedaba en
 * la base indefinidamente.
 *
 * <p>Cuenta desde la última actividad, no desde la creación: ver {@code Carrito}.
 *
 * <p>No borra líneas a mano — {@code linea_carrito} tiene {@code on delete cascade} desde la
 * migración original, así que se van con su carrito. Y no toca pedidos: un pedido nunca referencia
 * al carrito del que salió (sus líneas se congelan al crearlo), así que borrar un carrito no puede
 * dejar un pedido huérfano.
 */
public final class PurgarCarritosVencidos {

  private final RepositorioCarrito repositorioCarrito;
  private final Reloj reloj;
  private final Duration retencion;

  public PurgarCarritosVencidos(
      RepositorioCarrito repositorioCarrito, Reloj reloj, Duration retencion) {
    this.repositorioCarrito =
        Objects.requireNonNull(repositorioCarrito, "El repositorio de carritos no puede ser nulo.");
    this.reloj = Objects.requireNonNull(reloj, "El reloj no puede ser nulo.");
    this.retencion = Objects.requireNonNull(retencion, "La retención no puede ser nula.");
    if (retencion.isZero() || retencion.isNegative()) {
      throw new IllegalArgumentException(
          "La retención de carritos debe ser mayor que cero: con cero se borraría el carrito que"
              + " alguien está usando en este momento.");
    }
  }

  public int ejecutar() {
    return repositorioCarrito.eliminarInactivosDesde(reloj.ahora().minus(retencion));
  }
}
