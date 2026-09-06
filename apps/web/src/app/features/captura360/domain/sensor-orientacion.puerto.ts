import { InjectionToken } from '@angular/core';
import { LecturaDeOrientacion } from './nivel-360';

/**
 * El sensor de orientación, detrás de un puerto por lo mismo que la cámara y por una razón más:
 * en Safari de iOS el permiso se pide con una llamada explícita desde un gesto del usuario, y en
 * el resto de navegadores no hay tal llamada. Esa diferencia se resuelve una sola vez, aquí
 * abajo, y no se filtra a la pantalla.
 *
 * **Sin sensor no se bloquea nada** (`docs/10-captura-360.md`): el asistente sigue funcionando
 * con la guía visual sola.
 */
export interface SensorOrientacion {
  disponible(): boolean;

  /** `false` si el permiso se niega o el navegador no expone el sensor. Llamar desde un gesto. */
  pedirPermiso(): Promise<boolean>;

  /** Devuelve la función que corta la escucha. */
  escuchar(alLeer: (lectura: LecturaDeOrientacion) => void): () => void;
}

export const SENSOR_ORIENTACION = new InjectionToken<SensorOrientacion>('SensorOrientacion');
