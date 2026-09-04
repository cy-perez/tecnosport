import { Dinero } from './pedido.model';

/**
 * Lo que el frontend necesita para construir la URL del Web Checkout de
 * Wompi (`IntentoDePago.java`, `docs/11-pagos-y-envios.md`). `ambiente` es
 * informativo (config del backend, no cambia la URL de Wompi: sandbox o
 * producción lo decide la llave pública, no un host distinto).
 */
export interface IntentoDePago {
  readonly referencia: string;
  readonly monto: Dinero;
  readonly firmaIntegridad: string;
  readonly llavePublica: string;
  readonly ambiente: string;
}
