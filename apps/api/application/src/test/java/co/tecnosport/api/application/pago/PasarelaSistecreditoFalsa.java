package co.tecnosport.api.application.pago;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Doble escrito a mano, como pide docs/06-testing.md: lo que hace falta comprobar de este puerto es
 * qué se le mandó y qué contestó, y eso son diez líneas.
 */
final class PasarelaSistecreditoFalsa implements PasarelaSistecredito {

  private final List<SolicitudTransaccionSistecredito> solicitudes = new ArrayList<>();
  private TransaccionSistecredito respuesta;
  private RuntimeException falla;
  private int consultas;

  /**
   * Cuántas veces se le preguntó a la pasarela: el endpoint público no puede gastar una por
   * petición anónima.
   */
  int consultas() {
    return consultas;
  }

  void responder(TransaccionSistecredito respuesta) {
    this.respuesta = respuesta;
    this.falla = null;
  }

  void fallar(RuntimeException falla) {
    this.falla = falla;
    this.respuesta = null;
  }

  List<SolicitudTransaccionSistecredito> solicitudes() {
    return List.copyOf(solicitudes);
  }

  SolicitudTransaccionSistecredito ultimaSolicitud() {
    return solicitudes.get(solicitudes.size() - 1);
  }

  @Override
  public TransaccionSistecredito crear(SolicitudTransaccionSistecredito solicitud) {
    solicitudes.add(solicitud);
    if (falla != null) {
      throw falla;
    }
    return respuesta;
  }

  @Override
  public Optional<TransaccionSistecredito> consultar(String idTransaccion) {
    consultas++;
    return Optional.ofNullable(respuesta);
  }
}
