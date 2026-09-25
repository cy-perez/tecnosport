import { InjectionToken } from '@angular/core';
import { CrearPedidoComando, MetodosDePagoDisponiblesComando } from './pedido.comandos';
import { MetodoPago, Pedido, Seguimiento } from './pedido.model';

export interface RepositorioPedidos {
  crear(comando: CrearPedidoComando): Promise<Pedido>;
  metodosDePagoDisponibles(comando: MetodosDePagoDisponiblesComando): Promise<MetodoPago[]>;
  /**
   * El `correo` no es un dato más: es lo que autoriza el reintento, igual que en el seguimiento.
   * El servidor trata el que no coincide como si el pedido no existiera.
   */
  reintentarPago(pedidoId: string, correo: string): Promise<Pedido>;
  /** `GET /pedidos/{id}/seguimiento`, sin sesión (`docs/03-api.md`): el
   * correo hace de token. `null` si el id no existe o el correo no coincide
   * — el servidor no distingue los dos casos, para no filtrar si el id
   * existe a quien no conoce el correo real. */
  consultarSeguimiento(pedidoId: string, correo: string): Promise<Seguimiento | null>;
  /**
   * El mismo seguimiento, entrando por el **número legible** del pedido — `TS-2026-000123`, el que
   * lleva el comprobante—, que es el único identificador que el comprador tiene: el `id` es un
   * UUID y no aparece en nada que una persona lea.
   *
   * Es `POST` aunque no cree nada, y el correo viaja en el cuerpo: en un parámetro de consulta
   * acabaría en los registros de acceso, en el historial del navegador y en la cabecera `Referer`.
   *
   * `null` con la misma indiferencia que el hermano, y aquí importa más: el número es secuencial y
   * adivinable, así que lo único que protege el pedido es el correo. El servidor responde igual a
   * un número mal escrito, a uno que no existe y a un correo que no coincide.
   */
  consultarSeguimientoPorNumero(numeroPedido: string, correo: string): Promise<Seguimiento | null>;
}

export const REPOSITORIO_PEDIDOS = new InjectionToken<RepositorioPedidos>('RepositorioPedidos');
