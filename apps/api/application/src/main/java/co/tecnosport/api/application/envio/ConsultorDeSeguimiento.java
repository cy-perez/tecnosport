package co.tecnosport.api.application.envio;

import java.util.List;

/**
 * Le pregunta a la transportadora qué sabe de una guía. Es la red de seguridad del webhook: los
 * webhooks se pierden, y un paquete entregado hace cinco días con el pedido todavía en {@code
 * DESPACHADO} es un retracto que empieza a correr sin que el sistema lo sepa (adr/0022).
 *
 * <p>Devuelve los eventos que la transportadora conoce, en el mismo formato en que los entrega el
 * webhook, para que los apliquen exactamente igual. Lista vacía significa "no sé nada nuevo", que
 * incluye el caso en que la consulta falló: un proveedor caído no puede hacer fallar el lote
 * entero.
 *
 * <p><strong>Todavía no se puede implementar.</strong> {@code ADR-0022} anota la ruta {@code GET
 * /shipments/tracking/{guia}/{transportadora}} y no se ha podido comprobar: hace falta una guía
 * emitida, y la cuenta de sandbox no tiene créditos (docs/13-skydropx-capacidades.md, sección 6).
 */
public interface ConsultorDeSeguimiento {

  List<AplicarEventoDeEnvioComando> consultar(String transportadora, String guia);
}
