package co.tecnosport.api.presentation.envio.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * La opción más económica arriba —la que se cobra si el comprador no elige— y, desde ADR-0073,
 * {@code opciones}: una por transportadora, de la más económica a la más cara, para que elija. No
 * viaja el identificador del proveedor — si el {@code rate_id} llegara al navegador, alguien podría
 * devolverlo alterado al crear el pedido.
 *
 * <p>{@code diasEstimados} en cero significa <strong>sin estimado</strong>, no "llega hoy": hay
 * tarifas que no declaran plazo y no se inventa uno. Quien lo pinte tiene que distinguir los dos
 * casos.
 *
 * <p>{@code venceEn} está para que el checkout sepa que lo que muestra caduca. Lo que se cobra lo
 * fija {@code POST /api/v1/pedidos}, que vuelve a cotizar.
 *
 * <p><strong>No dice nada de contraentrega, y es a propósito.</strong> Llevó un {@code
 * admiteContraentrega} que era estructuralmente falso siempre: esta cotización se pide sin recaudo
 * —el comprador todavía no ha elegido cómo paga— y la cobertura de recaudo solo se sabe pidiéndola
 * con recaudo. Un booleano que no puede ser cierto es peor que no tenerlo, porque el próximo que lo
 * lea creerá que significa algo. Quien necesite saberlo pregunta a {@code
 * /pedidos/metodos-de-pago-disponibles}, que es donde docs/03-api.md dice que se resuelve.
 */
public record CotizacionEnvioRespuesta(
    DineroRespuesta costoEnvio,
    String transportadora,
    int diasEstimados,
    Instant venceEn,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OpcionEnvioRespuesta> opciones) {}
