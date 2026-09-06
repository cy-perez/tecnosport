import { InjectionToken } from '@angular/core';

/**
 * Wake Lock mientras dura la sesión de captura: la pantalla no se apaga entre tomas
 * (`docs/10-captura-360.md`). Que no se pueda es normal —el navegador puede no soportarlo, o
 * negarlo— y no es motivo para interrumpir nada.
 */
export interface PantallaDespierta {
  /** Devuelve la función que suelta el bloqueo. Nunca rechaza: si no se pudo, no pasa nada. */
  mantener(): Promise<() => void>;
}

export const PANTALLA_DESPIERTA = new InjectionToken<PantallaDespierta>('PantallaDespierta');
