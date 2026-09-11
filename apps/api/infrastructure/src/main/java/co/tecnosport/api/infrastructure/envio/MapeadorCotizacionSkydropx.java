package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * La frontera entre lo que está verificado y lo que no.
 *
 * <p>De la API de Skydropx está confirmado el <em>protocolo</em>: la cotización se crea con {@code
 * POST /api/v1/quotations}, se sondea con {@code GET /api/v1/quotations/{id}} hasta que {@code
 * is_completed} sea verdadero, y las tarifas valen 24 horas. Eso es lo que implementa {@link
 * SkydropxClient} y se puede probar entero.
 *
 * <p>El <em>mapeo</em> se confirmó el 11 de septiembre de 2026 pidiendo cotizaciones reales al
 * sandbox, y está en {@link MapeadorCotizacionSkydropxV1}. Mientras no se confirmó vivió detrás de
 * esta interfaz fallando cerrado, y la interfaz se queda: separa lo que se puede probar contra un
 * servidor de prueba —el protocolo— de lo que solo se puede saber preguntándole al proveedor.
 *
 * <p>La regla dura #9 no es paranoia en este repo: {@code WompiClientTest} deja escrito que un
 * vector de firma "de ejemplo" de la documentación resultó fabricado por la herramienta que resumió
 * la página, y al verificar Skydropx pasó lo mismo con un cuerpo de {@code /pickups}. De hecho la
 * sospecha era buena: la documentación sugería kilos con dudas, y lo eran — pero {@code
 * postal_code} resultó ser el código DANE, que no lo decía ninguna fuente.
 */
interface MapeadorCotizacionSkydropx {

  /** El cuerpo JSON de {@code POST /api/v1/quotations}. */
  String cuerpoDeCotizacion(CotizacionEnvio cotizacion, OrigenDespacho origen);

  /** El identificador de la cotización recién creada, para sondearla. */
  Optional<String> idDeCotizacion(JsonNode respuestaDeCreacion);

  /**
   * Las tarifas, si la cotización ya terminó. {@link Optional#empty()} significa "todavía no", que
   * es distinto de "terminó y no hay ninguna" — eso último es una lista vacía, y sí es una
   * respuesta final.
   */
  Optional<List<TarifaEnvio>> tarifasSiCompleto(JsonNode respuestaDeSondeo, Instant ahora);
}
