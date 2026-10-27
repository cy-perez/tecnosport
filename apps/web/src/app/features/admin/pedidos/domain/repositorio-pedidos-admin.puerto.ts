import { InjectionToken } from '@angular/core';
import { MedioReintegro } from '../../retractos/domain/retracto.model';
import {
  EmisionDeGuiaAdmin,
  FiltroPedidosAdmin,
  MedioDePagoEnPunto,
  ModalidadRecaudo,
  MotivoCancelacion,
  AlertaPagosSinTransaccion,
  AlertaPedidosDetenidos,
  PagoSinPedidoAdmin,
  PedidoAdmin,
  PedidosPaginadosAdmin,
} from './pedido-admin.model';

export interface RepositorioPedidosAdmin {
  listar(filtro: FiltroPedidosAdmin): Promise<PedidosPaginadosAdmin>;

  conciliarTransferencia(pedidoId: string): Promise<PedidoAdmin>;

  verificarContraentrega(pedidoId: string, motivo: string): Promise<PedidoAdmin>;

  despachar(pedidoId: string, guias: readonly GuiaDespachada[]): Promise<PedidoAdmin>;

  /** Le pide las guías a la transportadora. **No despacha**: devuelve la emisión en curso y el
   * pedido sigue en preparación hasta que la plataforma entregue los números, que tarda de
   * segundos a minutos. Por eso no devuelve un `PedidoAdmin`: no hay nada nuevo que contar de él
   * todavía. */
  emitirGuia(pedidoId: string): Promise<EmisionDeGuiaAdmin>;

  marcarEntregado(pedidoId: string): Promise<PedidoAdmin>;

  marcarListoParaRecoger(pedidoId: string): Promise<PedidoAdmin>;

  /** `medioDePago` solo en un pedido `PAGO_EN_PUNTO`; el número lo compara el servidor. */
  entregarEnPunto(entrada: {
    pedidoId: string;
    numeroPedido: string;
    medioDePago: MedioDePagoEnPunto | null;
  }): Promise<PedidoAdmin>;

  /** Un pedido pagado cuya reserva venció sin existencia: lo lleva a preparación si ya la hay. */
  confirmarInventario(pedidoId: string): Promise<PedidoAdmin>;

  rechazarEnEntrega(pedidoId: string, motivo: string): Promise<PedidoAdmin>;

  /** La mercancía de un pedido rechazado volvió. Sin monto: lo calcula el servidor. `medio` solo
   * cuando el pedido había cobrado; el servidor rechaza si falta. */
  recibirDevolucion(entrada: {
    pedidoId: string;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<PedidoAdmin>;

  conciliarRecaudo(
    pedidoId: string,
    modalidadRecaudo: ModalidadRecaudo,
    comisionRecaudo: number,
  ): Promise<PedidoAdmin>;

  /** Pagos de Wompi pendientes sin id de transacción, pasado el umbral y sin revisar. */
  listarPagosSinTransaccion(): Promise<AlertaPagosSinTransaccion>;

  /** Alguien lo buscó en el portal de Wompi: deja de salir en la alerta. */
  marcarPagoSinTransaccionRevisado(pagoId: string): Promise<void>;

  /** Pedidos que llevan más del umbral en preparación. Solo se enseñan: nada vence. */
  listarPedidosDetenidosEnPreparacion(): Promise<AlertaPedidosDetenidos>;

  /** Pagos aprobados sin pedido que los esperara y todavía sin devolver. */
  listarPagosSinPedido(): Promise<PagoSinPedidoAdmin[]>;

  /** Sin monto: es el del pago, entero, y lo pone el servidor. */
  registrarReintegroDePagoSinPedido(entrada: {
    pagoId: string;
    medio: MedioReintegro;
    comprobante: string | null;
  }): Promise<void>;

  /**
   * Borra el pedido del todo, con su historial. Solo uno con el pago fallido o cancelado, que
   * nunca recibió un pago ni tuvo envío ni trámites; si no, el servidor responde 409.
   */
  eliminar(pedidoId: string): Promise<void>;

  /** `monto` y `medio` solo cuando el dinero ya habia entrado; el servidor rechaza si faltan. */
  cancelar(entrada: {
    pedidoId: string;
    motivo: MotivoCancelacion;
    monto: number | null;
    medio: MedioReintegro | null;
    comprobante: string | null;
  }): Promise<PedidoAdmin>;
}

export const REPOSITORIO_PEDIDOS_ADMIN = new InjectionToken<RepositorioPedidosAdmin>(
  'RepositorioPedidosAdmin',
);

/** Una guía a despachar: lo que el panel manda por cada paquete (`adr/0031`). */
export interface GuiaDespachada {
  readonly transportadora: string;
  readonly guia: string;
  readonly costoEnvio: number;
}
