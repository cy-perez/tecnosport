package co.tecnosport.api.domain.inventario;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.UUID;

/**
 * No hay saldo disponible para reservar lo que se pidió.
 *
 * <p><strong>El mensaje no dice cuánto queda ni de qué variante, y es a propósito.</strong> Decía
 * las dos cosas —{@code "Quedan 3 unidades disponibles de la variante <uuid>"}— y ese texto viaja
 * tal cual en el {@code detail} del 409, porque {@code ManejadorDeErrores} publica el mensaje de
 * toda excepción de dominio. O sea que cualquiera podía pedir un pedido con una cantidad absurda y
 * leer el inventario exacto, variante por variante, y sondearlo cuando quisiera para saber si otro
 * comprador acaba de reservar una unidad. {@code docs/02-modelo-datos.md} decidió expresamente que
 * el catálogo público publica <em>un booleano por variante, no un número</em>, justamente porque el
 * disponible depende del instante en que se mira.
 *
 * <p>Los dos datos siguen aquí, como componentes, para quien los necesite del lado de adentro —un
 * registro, el panel—. Lo que cambia es que ya no salen solos por el hecho de existir.
 */
public final class ExistenciaInsuficienteException extends ExcepcionDeDominio {

  private final UUID varianteId;
  private final int disponible;
  private final int pedida;

  public ExistenciaInsuficienteException(UUID varianteId, int disponible, int pedida) {
    super("No hay existencias suficientes para completar esta operación.");
    this.varianteId = varianteId;
    this.disponible = disponible;
    this.pedida = pedida;
  }

  public UUID varianteId() {
    return varianteId;
  }

  public int disponible() {
    return disponible;
  }

  public int pedida() {
    return pedida;
  }
}
