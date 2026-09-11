package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.CotizacionEnvio;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * El mapeo que todavía no se puede escribir. Cada método falla con el mismo motivo, y el mensaje
 * dice qué falta para poder borrar esta clase.
 *
 * <p>No es un marcador de posición vacío: es la implementación correcta mientras el dato no exista.
 * Escribir un cuerpo con nombres de campo adivinados compilaría igual, pasaría las pruebas contra
 * un servidor falso que use esos mismos nombres inventados, y fallaría el día del primer despacho
 * real — o peor, cotizaría con el peso en la unidad equivocada y cobraría un flete que no cubre
 * nada.
 *
 * <p>Lo que falta está listado en docs/13-skydropx-capacidades.md, sección 6. Son seis datos y se
 * consiguen en una sesión en el panel de Skydropx, Conexiones &gt; API.
 */
final class MapeadorCotizacionPendiente implements MapeadorCotizacionSkydropx {

  private static final String MOTIVO =
      "El mapeo de la cotización con Skydropx no está confirmado contra la cuenta real: faltan los"
          + " campos de dirección para Colombia, la unidad de peso de parcels y la forma de la"
          + " respuesta de creación. Ver docs/13-skydropx-capacidades.md, sección 6.";

  @Override
  public String cuerpoDeCotizacion(CotizacionEnvio cotizacion, OrigenDespacho origen) {
    throw new MapeoSinConfirmarException(MOTIVO);
  }

  @Override
  public Optional<String> idDeCotizacion(JsonNode respuestaDeCreacion) {
    throw new MapeoSinConfirmarException(MOTIVO);
  }

  @Override
  public Optional<List<TarifaEnvio>> tarifasSiCompleto(JsonNode respuestaDeSondeo, Instant ahora) {
    throw new MapeoSinConfirmarException(MOTIVO);
  }
}
