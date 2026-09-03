package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.Objects;

/**
 * El id de transacción de Wompi llega en la URL de retorno del Web Checkout
 * (docs/11-pagos-y-envios.md) cuando el cliente vuelve al sitio. Sin él, la conciliación programada
 * no tiene cómo consultar este pago en la API de Wompi, que busca por su id, no por la referencia
 * propia.
 */
public final class RegistrarIdTransaccionWompi {

  private final RepositorioPagos repositorioPagos;

  public RegistrarIdTransaccionWompi(RepositorioPagos repositorioPagos) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
  }

  public void ejecutar(RegistrarIdTransaccionWompiComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pago pago =
        repositorioPagos
            .buscarPorReferencia(new ReferenciaPago(comando.referencia()))
            .orElseThrow(() -> new PagoNoEncontradoException(comando.referencia()));
    pago.registrarIdTransaccionWompi(comando.idTransaccionWompi());
    repositorioPagos.guardar(pago);
  }
}
