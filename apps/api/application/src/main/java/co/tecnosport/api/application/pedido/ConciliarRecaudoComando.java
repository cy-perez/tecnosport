package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.ModalidadRecaudo;
import java.util.UUID;

/**
 * {@code modalidadRecaudo} es por dónde entró el dinero, no por dónde se pidió que entrara: la
 * plataforma no acepta esa instrucción en el envío y la elección se hace al retirar el saldo. Ver
 * {@code adr/0043}.
 */
public record ConciliarRecaudoComando(
    UUID pedidoId, ModalidadRecaudo modalidadRecaudo, Dinero comisionRecaudo, String actor) {}
