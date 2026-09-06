import { Injectable } from '@angular/core';
import { LecturaDeOrientacion } from '../domain/nivel-360';
import { SensorOrientacion } from '../domain/sensor-orientacion.puerto';

/**
 * `DeviceOrientationEvent` real. En Safari de iOS el constructor expone `requestPermission()` y
 * hay que llamarlo desde un gesto del usuario; en el resto de navegadores esa función no existe y
 * basta con suscribirse. Esa diferencia se queda aquí.
 */
interface ConstructorConPermiso {
  requestPermission?: () => Promise<'granted' | 'denied' | 'default'>;
}

@Injectable()
export class SensorOrientacionNavegador implements SensorOrientacion {
  disponible(): boolean {
    return typeof window !== 'undefined' && typeof window.DeviceOrientationEvent !== 'undefined';
  }

  async pedirPermiso(): Promise<boolean> {
    if (!this.disponible()) {
      return false;
    }

    const constructor = window.DeviceOrientationEvent as unknown as ConstructorConPermiso;
    if (typeof constructor.requestPermission !== 'function') {
      // Sin puerta de permiso: el sensor se escucha directo.
      return true;
    }

    try {
      return (await constructor.requestPermission()) === 'granted';
    } catch {
      // iOS rechaza si la llamada no viene de un gesto, y también fuera de HTTPS.
      return false;
    }
  }

  escuchar(alLeer: (lectura: LecturaDeOrientacion) => void): () => void {
    const escucha = (evento: DeviceOrientationEvent) =>
      alLeer({ beta: evento.beta, gamma: evento.gamma });
    window.addEventListener('deviceorientation', escucha);
    return () => window.removeEventListener('deviceorientation', escucha);
  }
}
