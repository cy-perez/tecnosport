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
 * <p>Lo que <strong>no</strong> está confirmado es el <em>mapeo</em>: los campos exactos de {@code
 * address_from}/{@code address_to} para Colombia, la unidad de peso de {@code parcels} —nuestro
 * dominio guarda gramos y el único ejemplo encontrado parece usar kilos—, y la forma de la
 * respuesta de la creación. Ver docs/13-skydropx-capacidades.md, sección 6.
 *
 * <p>Por eso vive detrás de esta interfaz y no incrustado en el cliente. La regla dura #9 no es
 * paranoia en este repo: {@code WompiClientTest} deja escrito que un vector de firma "de ejemplo"
 * de la documentación resultó fabricado por la herramienta que resumió la página, y al verificar
 * Skydropx pasó lo mismo con un cuerpo de {@code /pickups}. Un peso en la unidad equivocada es el
 * flete mil veces mal cobrado.
 *
 * <p>La implementación de producción es {@link MapeadorCotizacionPendiente}, que falla cerrado
 * hasta que alguien entre al panel con las credenciales. El día que se confirmen, esto es una clase
 * y sus pruebas — no una reescritura.
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
