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
 * <p>Se pregunta con el <strong>código</strong> de la transportadora y no con su nombre. No es un
 * detalle del adaptador: la plataforma responde 404 con el nombre visible, y hay guías —las que
 * teclea una persona en el panel— de las que no conocemos el código. Quien llame tiene que saber
 * que esas no se pueden consultar, y por eso el código no viaja como un dato más del envío: lo
 * declara {@code GuiaEnvio.conciliable()}.
 */
public interface ConsultorDeSeguimiento {

  List<AplicarEventoDeEnvioComando> consultar(String codigoTransportadora, String guia);
}
