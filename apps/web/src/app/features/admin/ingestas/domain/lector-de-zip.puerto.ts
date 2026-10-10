import { InjectionToken } from '@angular/core';

/**
 * Lo que trae un zip, sin descomprimirlo: los nombres de sus archivos. Es lo único que hace falta
 * para validar la estructura de un zip de dos chats antes de subirlo.
 */
export interface LectorDeZip {
  /**
   * @returns los nombres de las entradas del zip, con su carpeta si la tienen
   * @throws si el archivo no es un zip que se pueda leer
   */
  nombresDeEntradas(archivo: Blob): Promise<string[]>;
}

export const LECTOR_DE_ZIP = new InjectionToken<LectorDeZip>('LectorDeZip');
