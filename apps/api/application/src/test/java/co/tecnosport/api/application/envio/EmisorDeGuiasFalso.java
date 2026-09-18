package co.tecnosport.api.application.envio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class EmisorDeGuiasFalso implements EmisorDeGuias {

  private final List<SolicitudDeEmision> solicitudes = new ArrayList<>();
  private final Map<String, List<LecturaDeEnvioEmitido>> lecturas = new LinkedHashMap<>();
  private ResultadoEmision respuesta =
      new ResultadoEmision.Rechazada(
          ResultadoEmision.Motivo.PROVEEDOR_NO_DISPONIBLE, "sin configurar en la prueba");

  /** Los envíos que se pidió cancelar, en orden: es lo que afirman las pruebas de cancelación. */
  private final List<String> cancelados = new ArrayList<>();

  private ResultadoCancelacion respuestaDeCancelacion = new ResultadoCancelacion.Cancelada();

  void responde(ResultadoEmision respuesta) {
    this.respuesta = respuesta;
  }

  /**
   * Varias lecturas para el mismo envío se consumen en orden, que es lo que permite probar la
   * espera: primero "sigue en curso" y en la vuelta siguiente la guía. La última se repite.
   */
  void paraElEnvio(String id, LecturaDeEnvioEmitido... enOrden) {
    lecturas.put(id, new ArrayList<>(List.of(enOrden)));
  }

  @Override
  public ResultadoEmision emitir(SolicitudDeEmision solicitud) {
    solicitudes.add(solicitud);
    return respuesta;
  }

  void alCancelarResponde(ResultadoCancelacion respuesta) {
    this.respuestaDeCancelacion = respuesta;
  }

  @Override
  public ResultadoCancelacion cancelar(String idEnvioEnPlataforma) {
    cancelados.add(idEnvioEnPlataforma);
    return respuestaDeCancelacion;
  }

  List<String> cancelados() {
    return List.copyOf(cancelados);
  }

  @Override
  public LecturaDeEnvioEmitido consultar(String idEnvioEnPlataforma) {
    List<LecturaDeEnvioEmitido> pendientes = lecturas.get(idEnvioEnPlataforma);
    if (pendientes == null || pendientes.isEmpty()) {
      return new LecturaDeEnvioEmitido.NoSeSabe();
    }
    return pendientes.size() == 1 ? pendientes.get(0) : pendientes.removeFirst();
  }

  List<SolicitudDeEmision> solicitudes() {
    return List.copyOf(solicitudes);
  }
}
