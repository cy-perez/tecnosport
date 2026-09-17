package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.LecturaDeEnvioEmitido;
import co.tecnosport.api.application.envio.SolicitudDeEmision;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * La frontera entre el protocolo de la emisión y su mapeo, igual que {@link
 * MapeadorCotizacionSkydropx}: el protocolo se prueba entero contra un servidor local, y el mapeo
 * se confirmó contra la cuenta real.
 *
 * <p><strong>Las dos versiones de la API a la vez, y no por descuido.</strong> Crear va por {@code
 * POST /api/v2/shipments}, que siempre devuelve un arreglo de envíos —lo que hace falta porque en
 * Colombia ninguna transportadora admite multipaquete y todo pedido de dos bultos es multienvío—.
 * Releer va por {@code GET /api/v1/shipments/{id}}, porque <strong>{@code GET
 * /api/v2/shipments/{id}} no existe</strong>: responde 404 con el HTML del sitio, medido el 16 de
 * septiembre de 2026 (docs/13-skydropx-capacidades.md §6.10).
 */
interface MapeadorEmisionSkydropx {

  /** El cuerpo JSON de {@code POST /api/v2/shipments}. */
  String cuerpoDeEmision(SolicitudDeEmision solicitud, OrigenDespacho origen);

  /**
   * Los identificadores de los envíos creados. Uno por bulto: la respuesta trae {@code data} como
   * arreglo y cada elemento es un envío con su propia guía y su propio cobro.
   */
  List<String> enviosCreados(JsonNode respuestaDeCreacion);

  /** Cómo quedó un envío, releído. */
  LecturaDeEnvioEmitido lectura(JsonNode respuestaDeLectura);
}
