package co.tecnosport.api.application.pago;

import co.tecnosport.api.domain.pago.Pago;
import co.tecnosport.api.domain.pago.ReferenciaPago;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioPagos {

  Optional<Pago> buscarPorReferencia(ReferenciaPago referencia);

  /**
   * Lo mismo que {@link #buscarPorReferencia}, bloqueando la fila hasta que termine la transacción:
   * el webhook y la conciliación podían aplicar el mismo pago a la vez, con ids de evento distintos
   * que el índice único de {@code evento_pago} no separa. Exige una transacción abierta.
   */
  default Optional<Pago> buscarPorReferenciaParaModificar(ReferenciaPago referencia) {
    return buscarPorReferencia(referencia);
  }

  /** Todos los intentos de pago de un pedido, para numerar el siguiente tras un reintento. */
  List<Pago> buscarPorPedidoId(UUID pedidoId);

  /**
   * El pago que registró ese id de transacción de la pasarela, si hay exactamente uno.
   *
   * <p>Existe para que la notificación de Sistecrédito —pública y anónima— pueda decidir si hay
   * algo local que tocar <b>sin preguntarle antes al tercero</b>. La columna no lleva {@code
   * unique}, así que si dos pagos comparten el id la respuesta es vacía: con dos candidatos no se
   * puede elegir, y elegir mal movería dinero del pedido equivocado.
   */
  Optional<Pago> buscarPorIdTransaccionPasarela(String idTransaccionPasarela);

  /**
   * {@code PENDIENTE}, con {@code idTransaccionPasarela} registrado, creados antes de {@code
   * creadosAntesDe} — el universo de la conciliación programada (docs/11-pagos-y-envios.md). Un
   * pago sin ese id no aparece aquí: no hay cómo consultarlo en la API de Wompi.
   */
  List<Pago> buscarPendientesParaConciliar(Instant creadosAntesDe);

  Optional<Pago> buscarPorId(UUID id);

  /**
   * Los pagos aprobados que entraron sin un pedido que los esperara, del más viejo al más nuevo.
   * Incluye los que ya tienen su reintegro: filtrarlos es cosa de quien pregunta, que sabe dónde se
   * registra eso.
   */
  List<Pago> buscarSinPedidoQueLosEspere();

  /**
   * {@code PENDIENTE}, <b>sin</b> {@code idTransaccionPasarela}, creados antes de {@code
   * creadosAntesDe} y que nadie ha marcado como revisados, del más viejo al más nuevo. Es el
   * complemento de {@link #buscarPendientesParaConciliar}: lo que la conciliación no puede
   * consultar, porque la API de Wompi busca por su id y no por la referencia propia.
   */
  List<Pago> buscarPendientesSinTransaccionSinRevisar(Instant creadosAntesDe);

  /**
   * Deja constancia de que alguien buscó este pago a mano y ya no hace falta enseñarlo. Devuelve
   * {@code false} si ya estaba marcado: la primera marca se conserva.
   */
  boolean marcarRevisadoSinTransaccion(UUID pagoId, String actor, Instant ahora);

  void guardar(Pago pago);
}
