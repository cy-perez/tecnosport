import { Dinero, TipoDocumento } from './pedido.model';

/**
 * Lo que el frontend necesita para mandar al comprador a pagar con Sistecrédito (`adr/0048`).
 *
 * A diferencia de `IntentoDePago`, aquí no hay llave pública ni firma: la URL la arma la pasarela
 * y llega hecha, así que el navegador no compone nada y no hay nada que pueda alterar por el
 * camino. Es de un solo uso y la transacción vive unos 15 minutos.
 */
export interface IntentoSistecredito {
  readonly referencia: string;
  readonly monto: Dinero;
  readonly urlRedireccion: string;
}

/**
 * Quién pide el crédito. No se guarda en ninguna parte —ni aquí ni en el backend—: viaja del
 * checkout a la pasarela y ahí termina, así que reintentar un pago vuelve a pedirlo.
 */
export interface DocumentoComprador {
  readonly tipoDocumento: TipoDocumento;
  readonly documento: string;
}
