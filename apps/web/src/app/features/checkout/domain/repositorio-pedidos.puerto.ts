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
   * Le pide al servidor que mande el enlace de seguimiento al correo del pedido, si el número y el
   * correo coinciden (ADR-0088). **No devuelve el pedido ni dice si existe**: el número es
   * secuencial, y devolverlo dejaba que quien conociera el correo de alguien recorriera números
   * hasta dar con un pedido suyo. El servidor responde lo mismo acierte o no.
   *
   * Es `POST` y el correo viaja en el cuerpo: en un parámetro de consulta acabaría en los registros
   * de acceso, en el historial del navegador y en la cabecera `Referer`.
   */
  solicitarEnlaceDeSeguimiento(numeroPedido: string, correo: string): Promise<void>;
}

export const REPOSITORIO_PEDIDOS = new InjectionToken<RepositorioPedidos>('RepositorioPedidos');
