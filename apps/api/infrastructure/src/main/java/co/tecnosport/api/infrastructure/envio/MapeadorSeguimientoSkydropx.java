package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.AplicarEventoDeEnvioComando;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * La misma frontera que {@link MapeadorCotizacionSkydropx}, y por el mismo motivo: separa el
 * protocolo —que se puede probar contra un servidor de prueba— del mapeo, que solo se sabe
 * preguntándole al proveedor.
 *
 * <p>El de aquí se midió el 16 de septiembre de 2026 leyendo el rastreo de la guía {@code
 * 873837506712} con {@code tools/sonda-rastreo.mjs}. Leer no cuesta saldo, así que a diferencia del
 * de la cotización este se puede volver a comprobar cuando se quiera.
 */
interface MapeadorSeguimientoSkydropx {

  /**
   * Los eventos que la plataforma conoce de una guía, <strong>del más viejo al más nuevo</strong> —
   * que es el orden contrario al que llegan.
   *
   * <p>La guía no viene dentro de la respuesta: se consulta por ella, así que la pone quien
   * pregunta.
   */
  List<AplicarEventoDeEnvioComando> eventos(JsonNode respuesta, String guia);
}
