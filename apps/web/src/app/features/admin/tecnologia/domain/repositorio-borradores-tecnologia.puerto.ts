import { InjectionToken } from '@angular/core';
import {
  AprobarBorradorTecnologia,
  BorradorTecnologia,
  EleccionDeConfiguracion,
  EstadoBorradorTecnologia,
} from './borrador-tecnologia.model';

/**
 * Lo que el panel hace con un borrador de tecnología. Aprobar devuelve el id del producto: el
 * nuevo, en borrador hasta que tenga fotos, o el que ya existía y recibió las variantes.
 */
export interface RepositorioBorradoresTecnologia {
  listar(estado: EstadoBorradorTecnologia): Promise<BorradorTecnologia[]>;
  obtener(id: string): Promise<BorradorTecnologia>;
  elegir(id: string, elecciones: readonly EleccionDeConfiguracion[]): Promise<BorradorTecnologia>;
  aprobar(id: string, aprobacion: AprobarBorradorTecnologia): Promise<string>;
  rechazar(id: string, motivo: string): Promise<BorradorTecnologia>;
}

export const REPOSITORIO_BORRADORES_TECNOLOGIA =
  new InjectionToken<RepositorioBorradoresTecnologia>('RepositorioBorradoresTecnologia');
