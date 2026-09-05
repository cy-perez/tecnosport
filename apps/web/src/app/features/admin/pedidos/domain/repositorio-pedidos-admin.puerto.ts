import { InjectionToken } from '@angular/core';
import { FiltroPedidosAdmin, PedidoAdmin, PedidosPaginadosAdmin } from './pedido-admin.model';

export interface RepositorioPedidosAdmin {
  listar(filtro: FiltroPedidosAdmin): Promise<PedidosPaginadosAdmin>;

  conciliarTransferencia(pedidoId: string): Promise<PedidoAdmin>;

  verificarContraentrega(pedidoId: string, motivo: string): Promise<PedidoAdmin>;

  despachar(pedidoId: string, transportadora: string, guia: string, costoEnvio: number): Promise<PedidoAdmin>;

  marcarEntregado(pedidoId: string): Promise<PedidoAdmin>;

  rechazarEnEntrega(pedidoId: string, motivo: string): Promise<PedidoAdmin>;

  conciliarRecaudo(pedidoId: string, comisionRecaudo: number): Promise<PedidoAdmin>;
}

export const REPOSITORIO_PEDIDOS_ADMIN = new InjectionToken<RepositorioPedidosAdmin>('RepositorioPedidosAdmin');
