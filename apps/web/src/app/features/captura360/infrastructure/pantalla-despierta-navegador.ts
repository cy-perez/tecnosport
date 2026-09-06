import { Injectable } from '@angular/core';
import { PantallaDespierta } from '../domain/pantalla-despierta.puerto';

/** Wake Lock real, y un no-op cuando el navegador no lo tiene o lo niega. */
@Injectable()
export class PantallaDespiertaNavegador implements PantallaDespierta {
  async mantener(): Promise<() => void> {
    // `Navigator.wakeLock` está tipado, pero no todos los navegadores lo traen de verdad.
    if (typeof navigator === 'undefined' || navigator.wakeLock === undefined) {
      return () => undefined;
    }

    try {
      const bloqueo = await navigator.wakeLock.request('screen');
      return () => void bloqueo.release().catch(() => undefined);
    } catch {
      // Que no se pueda mantener la pantalla encendida no interrumpe una captura.
      return () => undefined;
    }
  }
}
