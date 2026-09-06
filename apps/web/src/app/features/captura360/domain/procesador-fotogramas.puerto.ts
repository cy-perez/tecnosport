import { InjectionToken } from '@angular/core';
import { ColorRgb, DeteccionDeRecorte, EncuadreDelSet, Rectangulo } from './recorte-360';

/**
 * El acceso al `<canvas>`, detrás de un puerto: las decisiones de recorte y escala ya viven en
 * `recorte-360.ts`, funciones puras y probadas. Esto solo pone y saca píxeles.
 *
 * Se procesa **un fotograma a la vez** y se libera lo intermedio: trabajar sobre imágenes grandes
 * en canvas consume memoria, y un teléfono de gama media con ocho fotos de 1920 px abiertas a la
 * vez se queda sin ella (`docs/10-captura-360.md`).
 */
export interface ProcesadorDeFotogramas {
  /** Lee los píxeles de una toma y le pide a `recorte-360` el rectángulo del producto. */
  medir(toma: Blob): Promise<DeteccionDeRecorte>;

  /**
   * Dibuja la toma en el cuadro final de salida, con el encuadre común del set. El hueco que
   * quede cuando el cuadrado se sale de la toma se rellena con el fondo estimado, no se estira la
   * imagen: estirarla cambiaría la escala de ese fotograma.
   */
  renderizar(
    toma: Blob,
    rectangulo: Rectangulo,
    encuadre: EncuadreDelSet,
    fondo: ColorRgb,
  ): Promise<Blob>;
}

export const PROCESADOR_DE_FOTOGRAMAS = new InjectionToken<ProcesadorDeFotogramas>(
  'ProcesadorDeFotogramas',
);
