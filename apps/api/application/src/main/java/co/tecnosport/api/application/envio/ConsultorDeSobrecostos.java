package co.tecnosport.api.application.envio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Qué cobros extra le aplicó la transportadora a la cuenta desde una fecha.
 *
 * <p>Existe porque <strong>el sobrecosto no llega por ningún aviso</strong>: la plataforma describe
 * un evento de {@code extra_charges}, pero el panel de esta cuenta no lo ofrece entre los once que
 * se pueden suscribir ({@code docs/13-skydropx-capacidades.md} §6.9), así que hay que preguntar. Y
 * hay que preguntar porque el dinero se va solo: el cargo se descuenta del crédito y el flete que
 * el pedido registra sigue siendo el de la tarifa.
 *
 * <p><strong>Vacío es "no se pudo preguntar", no "no hay cobros".</strong> Misma distinción que
 * {@link ConsultorDeSaldo} y por el mismo motivo: una lista vacía es una buena noticia y un
 * proveedor caído no lo es, y confundirlos convierte cada rato de indisponibilidad en silencio
 * tranquilizador. El puerto no concluye.
 *
 * <p>{@code desde} acota la pregunta por la fecha de detección, que es lo que el endpoint sabe
 * filtrar. No es una optimización: el orden en que devuelve los cobros no está documentado, así que
 * pedir "los últimos" por página sería apoyarse en algo que nadie prometió.
 */
public interface ConsultorDeSobrecostos {

  /** Los cobros detectados desde {@code desde}, o vacío si la plataforma no contestó. */
  Optional<List<SobrecostoDeEnvio>> desde(Instant desde);
}
