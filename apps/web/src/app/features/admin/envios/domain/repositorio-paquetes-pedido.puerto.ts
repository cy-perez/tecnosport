import { InjectionToken } from '@angular/core';
import { PaquetesDePedido } from './paquetes-de-pedido.model';

export interface RepositorioPaquetesPedido {
  /** Los paquetes de un pedido a domicilio, armados igual que los armaría la emisión por API. */
  consultar(pedidoId: string): Promise<PaquetesDePedido>;
}

export const REPOSITORIO_PAQUETES_PEDIDO = new InjectionToken<RepositorioPaquetesPedido>(
  'RepositorioPaquetesPedido',
);
