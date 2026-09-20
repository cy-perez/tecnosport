import { InjectionToken } from '@angular/core';
import { IntentoDePago } from './intento-pago.model';
import { DocumentoComprador, IntentoSistecredito } from './intento-sistecredito.model';

export interface RepositorioPagos {
  crearIntento(pedidoId: string): Promise<IntentoDePago>;
  /**
   * Otro endpoint y otro flujo (`adr/0048`): aquí el servidor habla con la pasarela y devuelve la
   * URL hecha, en vez de firmar unos datos para que el navegador arme la suya. Por eso lleva el
   * documento de quien pide el crédito, que es lo único que el cliente aporta además del pedido.
   */
  crearIntentoSistecredito(
    pedidoId: string,
    documento: DocumentoComprador,
    idioma: string,
  ): Promise<IntentoSistecredito>;
  /** El id de transacción llega en la URL de retorno del Web Checkout
   * (`docs/03-api.md`) — sin registrarlo, la conciliación programada no
   * tiene cómo consultar este pago si nunca llega el webhook. */
  registrarIdTransaccion(referencia: string, idTransaccionWompi: string): Promise<void>;
}

export const REPOSITORIO_PAGOS = new InjectionToken<RepositorioPagos>('RepositorioPagos');
