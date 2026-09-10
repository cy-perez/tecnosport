import { InjectionToken } from '@angular/core';
import { CrearPedidoComando, MetodosDePagoDisponiblesComando } from './pedido.comandos';
import { MetodoPago, Pedido, Seguimiento } from './pedido.model';

export interface RepositorioPedidos {
  crear(comando: CrearPedidoComando): Promise<Pedido>;
  metodosDePagoDisponibles(comando: MetodosDePagoDisponiblesComando): Promise<MetodoPago[]>;
  reintentarPago(pedidoId: string): Promise<Pedido>;
  /** `GET /pedidos/{id}/seguimiento`, sin sesión (`docs/03-api.md`): el
   * correo hace de token. `null` si el id no existe o el correo no coincide
   * — el servidor no distingue los dos casos, para no filtrar si el id
   * existe a quien no conoce el correo real. */
  consultarSeguimiento(pedidoId: string, correo: string): Promise<Seguimiento | null>;
}

export const REPOSITORIO_PEDIDOS = new InjectionToken<RepositorioPedidos>('RepositorioPedidos');
