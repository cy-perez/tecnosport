package co.tecnosport.api.bootstrap.pago;

import co.tecnosport.api.application.pago.PasarelaSistecredito;
import co.tecnosport.api.application.pago.SistecreditoNoRespondeException;
import co.tecnosport.api.application.pago.SolicitudTransaccionSistecredito;
import co.tecnosport.api.application.pago.TransaccionSistecredito;
import java.util.Optional;

/**
 * El puerto cuando el método está apagado. Existe para que el contexto levante sin credenciales de
 * Sistecrédito —que es el estado normal de cualquier despliegue que no lo ofrezca— sin sembrar un
 * {@code @ConditionalOnProperty} que dejaría el bean ausente y rompería el arranque de todo lo que
 * lo inyecta.
 *
 * <p>Revienta en vez de devolver algo vacío, y es a propósito: si alguien llega hasta aquí con el
 * método apagado, hay un agujero en las comprobaciones de {@code MetodosDePagoDisponibles} y {@code
 * CrearPedido}, y eso tiene que verse en los registros, no disimularse como una caída del
 * proveedor.
 */
final class SistecreditoApagado implements PasarelaSistecredito {

  @Override
  public TransaccionSistecredito crear(SolicitudTransaccionSistecredito solicitud) {
    throw new SistecreditoNoRespondeException(
        "Se intentó crear una transacción de Sistecrédito con el método apagado"
            + " (tecnosport.sistecredito.habilitado=false).");
  }

  @Override
  public Optional<TransaccionSistecredito> consultar(String idTransaccion) {
    return Optional.empty();
  }
}
