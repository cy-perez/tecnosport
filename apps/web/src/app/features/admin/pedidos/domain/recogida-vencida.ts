import { PedidoAdmin } from './pedido-admin.model';

/**
 * Si una recogida lista ya pasó su plazo: la misma regla que `Pedido.recogidaVencida` del servidor
 * —en el instante exacto del vencimiento ya cuenta como vencida— y sin otra fuente que el
 * `recogidaVenceEn` que el servidor manda. `ahora` llega de quien llama, en milisegundos, para que
 * la regla no dependa del reloj y se pueda probar.
 *
 * Es para avisar y para ofrecer el motivo «no recogido»: quien cancela de verdad, y lo comprueba,
 * es el servidor.
 */
export function recogidaVencida(
  pedido: Pick<PedidoAdmin, 'recogidaVenceEn'>,
  ahora: number,
): boolean {
  return pedido.recogidaVenceEn !== null && Date.parse(pedido.recogidaVenceEn) <= ahora;
}
