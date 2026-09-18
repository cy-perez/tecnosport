package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.envio.EmisorDeGuias;
import co.tecnosport.api.application.envio.LecturaDeEnvioEmitido;
import co.tecnosport.api.application.envio.ResultadoCancelacion;
import co.tecnosport.api.application.envio.ResultadoEmision;
import co.tecnosport.api.application.envio.SolicitudDeEmision;
import java.util.ArrayList;
import java.util.List;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. La cancelación de un pedido
 * solo le pide anular, así que emitir y consultar revientan a propósito: si una prueba de
 * cancelación los alcanza, es que el caso de uso hizo algo que no le toca.
 */
final class EmisorDeGuiasFalso implements EmisorDeGuias {

  private final List<String> cancelados = new ArrayList<>();
  private ResultadoCancelacion respuesta = new ResultadoCancelacion.Cancelada();

  void alCancelarResponde(ResultadoCancelacion respuesta) {
    this.respuesta = respuesta;
  }

  List<String> cancelados() {
    return List.copyOf(cancelados);
  }

  @Override
  public ResultadoEmision emitir(SolicitudDeEmision solicitud) {
    throw new AssertionError("Cancelar un pedido no emite guías.");
  }

  @Override
  public LecturaDeEnvioEmitido consultar(String idEnvioEnPlataforma) {
    throw new AssertionError("Cancelar un pedido no relee envíos.");
  }

  @Override
  public ResultadoCancelacion cancelar(String idEnvioEnPlataforma) {
    cancelados.add(idEnvioEnPlataforma);
    return respuesta;
  }
}
