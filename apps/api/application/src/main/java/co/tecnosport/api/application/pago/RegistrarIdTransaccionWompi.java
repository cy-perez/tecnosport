package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.util.Objects;
import java.util.Optional;

/**
 * El id de transacción de Wompi llega en la URL de retorno del Web Checkout
 * (docs/11-pagos-y-envios.md) cuando el cliente vuelve al sitio. Sin él, la conciliación programada
 * no tiene cómo consultar este pago en la API de Wompi, que busca por su id, no por la referencia
 * propia.
 *
 * <p><b>Se verifica contra la pasarela antes de registrarlo</b>, desde el 4 de octubre de 2026. El
 * endpoint es público y anónimo —el navegador vuelve del checkout sin sesión—, la referencia es
 * predecible y el pago se queda con el primer id que recibe. Cualquiera podía recorrer referencias
 * estampando ids basura: el comprador real recibía un 422 al volver y, si además se perdía el
 * webhook, la conciliación consultaba la basura para siempre. Ahora solo entra un id cuya
 * transacción la pasarela reporta con la referencia de este pago.
 */
public final class RegistrarIdTransaccionWompi {

  private final RepositorioPagos repositorioPagos;
  private final PasarelaDePagos pasarelaDePagos;

  public RegistrarIdTransaccionWompi(
      RepositorioPagos repositorioPagos, PasarelaDePagos pasarelaDePagos) {
    this.repositorioPagos = Objects.requireNonNull(repositorioPagos);
    this.pasarelaDePagos = Objects.requireNonNull(pasarelaDePagos);
  }

  public void ejecutar(RegistrarIdTransaccionWompiComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Pago pago =
        repositorioPagos
            .buscarPorReferencia(new ReferenciaPago(comando.referencia()))
            .orElseThrow(() -> new PagoNoEncontradoException(comando.referencia()));
    if (pago.idTransaccionPasarela().filter(comando.idTransaccionPasarela()::equals).isPresent()) {
      return;
    }
    Optional<TransaccionDePasarela> transaccion =
        pasarelaDePagos.consultarTransaccion(comando.idTransaccionPasarela());
    if (transaccion.isEmpty() || !transaccion.get().referencia().equals(comando.referencia())) {
      throw new TransaccionDeOtroPagoException(comando.referencia());
    }
    pago.registrarIdTransaccionPasarela(comando.idTransaccionPasarela());
    repositorioPagos.guardar(pago);
  }
}
