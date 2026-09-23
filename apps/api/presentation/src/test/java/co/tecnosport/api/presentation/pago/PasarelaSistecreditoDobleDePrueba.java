package co.tecnosport.api.presentation.pago;

import co.tecnosport.api.application.pago.PasarelaSistecredito;
import co.tecnosport.api.application.pago.SolicitudTransaccionSistecredito;
import co.tecnosport.api.application.pago.TransaccionSistecredito;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que la pasarela contesta cuando el caso de uso va a contrastar la notificación. Aquí lo que
 * importa es exactamente eso: la notificación no está firmada, así que lo único que la autentica es
 * esta consulta, y una prueba del endpoint que no la controle no está probando nada.
 */
final class PasarelaSistecreditoDobleDePrueba implements PasarelaSistecredito {

  private final Map<String, TransaccionSistecredito> transacciones = new HashMap<>();

  void limpiar() {
    transacciones.clear();
  }

  void responder(TransaccionSistecredito transaccion) {
    transacciones.put(transaccion.id(), transaccion);
  }

  @Override
  public TransaccionSistecredito crear(SolicitudTransaccionSistecredito solicitud) {
    throw new UnsupportedOperationException(
        "Estas pruebas son del endpoint de confirmación, que nunca crea una transacción.");
  }

  @Override
  public Optional<TransaccionSistecredito> consultar(String idTransaccion) {
    return Optional.ofNullable(transacciones.get(idTransaccion));
  }
}
