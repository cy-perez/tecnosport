import { InjectionToken } from '@angular/core';
import { IntentoDePago } from './intento-pago.model';

export interface RepositorioPagos {
  crearIntento(pedidoId: string): Promise<IntentoDePago>;
  /** El id de transacción llega en la URL de retorno del Web Checkout
   * (`docs/03-api.md`) — sin registrarlo, la conciliación programada no
   * tiene cómo consultar este pago si nunca llega el webhook. */
  registrarIdTransaccion(referencia: string, idTransaccionWompi: string): Promise<void>;
}

export const REPOSITORIO_PAGOS = new InjectionToken<RepositorioPagos>('RepositorioPagos');
