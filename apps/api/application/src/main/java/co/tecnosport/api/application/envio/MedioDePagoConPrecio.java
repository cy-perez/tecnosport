package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.pedido.MetodoPago;
import java.util.Objects;

/**
 * Un medio de pago disponible para un carrito, con lo que el comprador pagaría de mercancía si lo
 * elige (ADR-0095): el publicado con pasarela, el de contado sin ella. Informativo, como toda la
 * consulta: el precio que se cobra lo fija {@code CrearPedido} con el catálogo de ese momento.
 *
 * @param subtotal la mercancía al precio que corresponde a este medio, sin flete
 * @param descuento lo que este medio ahorra frente al precio publicado; cero con pasarela
 */
public record MedioDePagoConPrecio(MetodoPago metodo, Dinero subtotal, Dinero descuento) {

  public MedioDePagoConPrecio {
    Objects.requireNonNull(metodo, "El método no puede ser nulo.");
    Objects.requireNonNull(subtotal, "El subtotal no puede ser nulo.");
    Objects.requireNonNull(descuento, "El descuento no puede ser nulo.");
  }
}
