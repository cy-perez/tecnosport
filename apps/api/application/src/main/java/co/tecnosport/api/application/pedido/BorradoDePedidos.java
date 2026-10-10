package co.tecnosport.api.application.pedido;

import java.util.Set;
import java.util.UUID;

/**
 * Lo que la eliminación de un pedido necesita saber y hacer en la base, aparte de {@link
 * RepositorioPedidos}: aquel es el agregado y tiene una docena de dobles de prueba; esto mira
 * tablas de otros módulos —pagos, envíos, trámites legales— que el agregado no conoce.
 */
public interface BorradoDePedidos {

  /** Lo que cuelga de un pedido y hace que no se pueda borrar. */
  enum Compromiso {
    /** Un pago aprobado, o pendiente: la pasarela todavía puede aprobarlo. */
    PAGO,
    /** Un envío o una emisión de guía, aunque haya fallado. */
    ENVIO,
    /** Un retracto, un reintegro, una PQR, una garantía o una reversión del pago. */
    TRAMITE
  }

  /** Vacío si no hay ninguno. */
  Set<Compromiso> compromisosDe(UUID pedidoId);

  /**
   * Borra el pedido con sus líneas, su historial y sus intentos de pago fallidos (con los avisos de
   * la pasarela). Los movimientos de inventario se quedan: son la historia del stock.
   *
   * <p>Solo borra lo que sigue cumpliendo la regla —pagos rechazados o en error, pedido fallido o
   * cancelado—; si algo apareció mientras tanto, lanza {@link PedidoNoEliminableException} y la
   * transacción se revierte.
   */
  void eliminar(UUID pedidoId);
}
