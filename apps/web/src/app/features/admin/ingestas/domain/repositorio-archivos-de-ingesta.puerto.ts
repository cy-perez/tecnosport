import { InjectionToken } from '@angular/core';
import { ArchivosDeIngestaPaginados } from './archivo-de-ingesta.model';

/** El historial de zips y su limpieza. */
export interface RepositorioArchivosDeIngesta {
  listar(pagina: number): Promise<ArchivosDeIngestaPaginados>;
  /**
   * Borra el zip del almacenamiento y deja la ingesta entera. El servidor responde 409 si un lote
   * que lo lee sigue abierto; repetirlo no falla.
   */
  borrar(id: string): Promise<void>;
}

export const REPOSITORIO_ARCHIVOS_DE_INGESTA = new InjectionToken<RepositorioArchivosDeIngesta>(
  'RepositorioArchivosDeIngesta',
);
