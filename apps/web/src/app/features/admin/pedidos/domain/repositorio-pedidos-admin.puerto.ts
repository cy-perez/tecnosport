import { InjectionToken } from '@angular/core';
import { MedioReintegro } from '../../retractos/domain/retracto.model';
import {
  FiltroPedidosAdmin,
  MotivoCancelacion,
  PedidoAdmin,
  PedidosPaginadosAdmin,
} from './pedido-admin.model';

export interface RepositorioPedidosAdmin {
  listar(filtro: FiltroPedidosAdmin): Promise<PedidosPaginadosAdmin>;

  conciliarTransferencia(pedidoId: string): Promise<PedidoAdmin>;

  verificarContraentrega(pedidoId: string, motivo: string): Promise<PedidoAdmin>;

  despachar(pedidoId: string, transportadora: string, guia: string, costoEnvio: number): Promise<PedidoAdmin>;

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

export const REPOSITORIO_PEDIDOS_ADMIN = new InjectionToken<RepositorioPedidosAdmin>('RepositorioPedidosAdmin');
