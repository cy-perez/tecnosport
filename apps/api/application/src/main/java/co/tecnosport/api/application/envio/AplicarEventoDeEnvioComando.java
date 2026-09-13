package co.tecnosport.api.application.envio;

import co.tecnosport.api.domain.envio.EstadoEnvio;
import java.time.Instant;

/**
 * Un movimiento del paquete, ya traducido al vocabulario del dominio. Llega por dos caminos —el
 * webhook y la conciliación programada— y los dos lo aplican igual (adr/0022).
 *
 * <p>{@code idExterno} es lo que hace idempotente el webhook, y por eso es obligatorio: sin él, un
 * reintento se registraría dos veces y aplicaría dos veces sus efectos.
 */
public record AplicarEventoDeEnvioComando(
    String guia,
    EstadoEnvio estado,
    String descripcion,
    Instant ocurrioEn,
    String idExterno,
    String actor) {}
