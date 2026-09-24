import { isPlatformServer } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';

/**
 * Lo que toda precarga de un resolver de ruta necesita para **no poder
 * bloquear la navegación**. Son dos piezas y hacen falta las dos.
 *
 * El problema: `prefetchQuery` y `prefetchInfiniteQuery` prometen no lanzar, y
 * por eso los resolvers los esperan sin `catch`. Lo que **no** prometen es
 * terminar. Si TanStack pausa el fetch, la promesa se queda pendiente para
 * siempre y con ella el `resolve` de la ruta: la navegación se detiene en
 * `ResolveStart` —sin `ResolveEnd`, sin `NavigationCancel`, sin error— y como
 * el `urlUpdateStrategy` por omisión es `deferred`, la URL ni siquiera cambia.
 * La aplicación se queda congelada en la página anterior, muda.
 *
 * Cuándo pausa, según `query-core/retryer`:
 *
 * ```
 * canStart    = canFetch(networkMode) && canRun()
 * canContinue = focusManager.isFocused() && (networkMode === 'always' || onlineManager.isOnline()) && canRun()
 * start: if (canStart()) run(); else pause().then(run);
 * ```
 *
 * `pause()` solo despierta con un evento de foco o de red, así que hay dos
 * caminos a una promesa eterna: **sin conexión** el primer intento ni se lanza,
 * y **con la pestaña en segundo plano** el reintento se queda esperando foco.
 * Ojo con el segundo: `networkMode: 'always'` no lo evita —la comprobación de
 * foco va aparte—, solo `retry: false`, porque sin reintentos no se llega ahí.
 */
export const PRECARGA_NO_BLOQUEANTE = {
  networkMode: 'always',
  retry: false,
} as const;

/**
 * Cuánto puede esperar una navegación del navegador a que la precarga caliente
 * la caché. Con la API sana esto no se alcanza nunca —una respuesta local tarda
 * decenas de milisegundos—; existe para que un backend caído, lento o
 * inalcanzable cueste una pantalla de carga y no una aplicación congelada.
 */
const ESPERA_MAXIMA_EN_EL_NAVEGADOR_MS = 2000;

/**
 * Acota lo que un resolver espera por una precarga. **Es la pieza que de verdad
 * garantiza el arreglo**, y no se puede sustituir por `PRECARGA_NO_BLOQUEANTE`.
 *
 * El motivo está en `query-core/query.ts`:
 *
 * ```
 * async fetch(options, fetchOptions) {
 *   if (this.state.fetchStatus !== 'idle' && this.#retryer?.status() !== 'rejected') {
 *     ...
 *     else if (this.#retryer) { return this.#retryer.promise }   // <- el fetch en vuelo
 *   }
 *   if (options) this.setOptions(options)                        // <- solo si no lo había
 * ```
 *
 * Si la consulta ya tiene un fetch en vuelo, `prefetchQuery` devuelve **ese**
 * y descarta las opciones que le acaban de pasar. Y ese fetch en vuelo suele
 * ser el del componente que está en pantalla, que sí usa los valores por
 * omisión y sí se pausa. Medido: con la API caída, entrar a la rejilla y
 * cambiar de idioma dejaba las tres consultas en `paused` **antes** del clic,
 * así que la precarga del resolver se enganchaba a una promesa que no iba a
 * resolverse nunca — con `PRECARGA_NO_BLOQUEANTE` puesto y todo.
 *
 * En el servidor se espera lo que haga falta: ahí la precarga es el motivo de
 * que el HTML salga con datos y no con esqueletos (`ADR-0011`), no hay
 * navegación que congelar, y un arranque en frío del backend puede tardar más
 * que cualquier límite razonable.
 */
export function sinBloquearLaNavegacion<T>(precarga: Promise<T>): Promise<T | undefined> {
  if (isPlatformServer(inject(PLATFORM_ID))) {
    return precarga;
  }

  return Promise.race([
    precarga,
    new Promise<undefined>((resolver) =>
      setTimeout(() => resolver(undefined), ESPERA_MAXIMA_EN_EL_NAVEGADOR_MS),
    ),
  ]);
}
