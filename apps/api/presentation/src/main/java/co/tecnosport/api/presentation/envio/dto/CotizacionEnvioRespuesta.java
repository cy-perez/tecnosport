package co.tecnosport.api.presentation.envio.dto;

import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import java.time.Instant;

/**
 * Una sola opción, la más económica: el servidor elige (adr/0021). No viaja la lista de tarifas ni
 * el identificador del proveedor — si el {@code rate_id} llegara al navegador, alguien podría
 * devolverlo alterado al crear el pedido.
 *
 * <p>{@code diasEstimados} en cero significa <strong>sin estimado</strong>, no "llega hoy": hay
 * tarifas que no declaran plazo y no se inventa uno. Quien lo pinte tiene que distinguir los dos
 * casos.
 *
 * <p>{@code venceEn} está para que el checkout sepa que lo que muestra caduca. Lo que se cobra lo
 * fija {@code POST /api/v1/pedidos}, que vuelve a cotizar.
 */
public record CotizacionEnvioRespuesta(
    DineroRespuesta costoEnvio,
    String transportadora,
    int diasEstimados,
    Instant venceEn,
    boolean admiteContraentrega) {}
