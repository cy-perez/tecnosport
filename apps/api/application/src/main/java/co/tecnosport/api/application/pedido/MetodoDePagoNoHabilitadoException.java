package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.MetodoPago;

/**
 * El método existe en el código pero el negocio no lo ofrece hoy — la cuenta de la pasarela no lo
 * tiene activado ({@code tecnosport.wompi.metodos.habilitados}). No es "no disponible para este
 * pedido", que es lo que dice {@link ContraentregaNoDisponibleException}: es no disponible para
 * ninguno, y por eso no menciona destino ni monto.
 */
public final class MetodoDePagoNoHabilitadoException extends RuntimeException {

  public MetodoDePagoNoHabilitadoException(MetodoPago metodoPago) {
    super("El método de pago " + metodoPago + " no está habilitado.");
  }
}
