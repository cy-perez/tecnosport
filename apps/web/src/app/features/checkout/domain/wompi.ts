import { IntentoDePago } from './intento-pago.model';

const URL_BASE_WEB_CHECKOUT = 'https://checkout.wompi.co/p/';

/**
 * Construye la URL del Web Checkout hospedado de Wompi. Parámetros
 * verificados contra la documentación oficial de Wompi (docs.wompi.co,
 * "Widget & Checkout Web"), no de memoria (regla dura #9 — un vector de
 * ejemplo fabricado ya causó un problema real en este proyecto,
 * `docs/09-plan-de-arranque.md`):
 *
 * - `amount-in-cents` es el monto multiplicado por 100 **incluso en COP**,
 *   que no se fracciona en la práctica: Wompi lo expresa así igual
 *   (ejemplo textual de su documentación: "10000 = $100 COP").
 * - `signature:integrity` lleva el nombre con dos puntos literal — así lo
 *   documenta Wompi para el `<input name="signature:integrity">` del
 *   formulario de ejemplo.
 * - `redirect-url` es opcional para Wompi, pero sin ella no hay cómo volver
 *   a la pantalla de retorno con el id de transacción en la URL
 *   (`docs/09-plan-de-arranque.md`).
 */
export function urlWebCheckoutWompi(intento: IntentoDePago, urlRetorno: string): string {
  const parametros = new URLSearchParams({
    'public-key': intento.llavePublica,
    currency: intento.monto.moneda,
    'amount-in-cents': String(Math.round(intento.monto.valor * 100)),
    reference: intento.referencia,
    'signature:integrity': intento.firmaIntegridad,
    'redirect-url': urlRetorno,
  });
  return `${URL_BASE_WEB_CHECKOUT}?${parametros.toString()}`;
}
