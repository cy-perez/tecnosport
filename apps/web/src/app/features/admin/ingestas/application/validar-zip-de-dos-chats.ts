import { inject } from '@angular/core';
import { LECTOR_DE_ZIP } from '../domain/lector-de-zip.puerto';
import {
  EstructuraDeDosChats,
  esLaEstructuraAcordada,
  estructuraDeDosChats,
} from '../domain/zip-de-dos-chats';

export interface ValidacionDeZip {
  readonly valida: boolean;
  /** La que se esperaba, para decirla en el mensaje cuando no lo es. */
  readonly estructura: EstructuraDeDosChats;
}

/**
 * Valida que el zip de un proveedor que sube sus dos chats juntos tenga la estructura acordada
 * (`zip-de-dos-chats.ts`). Un archivo que ni siquiera se deja leer como zip tampoco la tiene.
 */
export function usarValidarZipDeDosChats(): (
  archivo: File,
  nombreDelProveedor: string,
) => Promise<ValidacionDeZip> {
  const lector = inject(LECTOR_DE_ZIP);
  return async (archivo, nombreDelProveedor) => {
    const estructura = estructuraDeDosChats(nombreDelProveedor);
    if (archivo.name !== estructura.zip) {
      return { valida: false, estructura };
    }
    try {
      const entradas = await lector.nombresDeEntradas(archivo);
      return { valida: esLaEstructuraAcordada(estructura, archivo.name, entradas), estructura };
    } catch {
      return { valida: false, estructura };
    }
  };
}
