package co.tecnosport.api.presentation.pedido;

import co.tecnosport.api.application.envio.EmisorDeGuias;
import co.tecnosport.api.application.envio.LecturaDeEnvioEmitido;
import co.tecnosport.api.application.envio.ResultadoEmision;
import co.tecnosport.api.application.envio.SolicitudDeEmision;
import java.util.List;

/**
 * Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md.
 *
 * <p>Acepta por omisión: lo que estas pruebas comprueban es el cableado HTTP —qué código responde
 * el endpoint y con qué cuerpo—, no lo que hace la plataforma. Eso se prueba en {@code
 * EmitirGuiaDePedidoTest} con dobles, y el mapeo contra respuestas reales en {@code
 * MapeadorEmisionSkydropxV2Test}.
 */
public class EmisorDeGuiasDobleDePrueba implements EmisorDeGuias {

  private ResultadoEmision respuesta = new ResultadoEmision.Aceptada(List.of("177d1939"));

  public void responde(ResultadoEmision respuesta) {
    this.respuesta = respuesta;
  }

  @Override
  public ResultadoEmision emitir(SolicitudDeEmision solicitud) {
    return respuesta;
  }

  @Override
  public LecturaDeEnvioEmitido consultar(String idEnvioEnPlataforma) {
    return new LecturaDeEnvioEmitido.Sigue();
  }
}
