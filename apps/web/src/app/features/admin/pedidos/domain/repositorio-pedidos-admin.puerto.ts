import { InjectionToken } from '@angular/core';
import { MedioReintegro } from '../../retractos/domain/retracto.model';
import {
  EmisionDeGuiaAdmin,
  FiltroPedidosAdmin,
  MotivoCancelacion,
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

  rechazarEnEntrega(pedidoId: string, motivo: string): Promise<PedidoAdmin>;

  conciliarRecaudo(pedidoId: string, comisionRecaudo: number): Promise<PedidoAdmin>;

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
