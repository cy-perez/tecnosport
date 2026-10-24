import { InjectionToken } from '@angular/core';
import {
  AprobarBorradorTecnologia,
  BorradorTecnologia,
  EleccionDeConfiguracion,
  EstadoBorradorTecnologia,
  FotoNuevaDeBorrador,
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
  /** La deja al final: no cambia la principal. */
  subirFoto(id: string, foto: FotoNuevaDeBorrador): Promise<BorradorTecnologia>;
  quitarFoto(id: string, fotoId: string): Promise<void>;
  /** Cada foto del borrador una vez; la primera queda de principal. */
  reordenarFotos(id: string, fotoIds: readonly string[]): Promise<BorradorTecnologia>;
  /** `null` vuelve a la ficha que preparó la skill. */
  editarFicha(id: string, descripcion: string | null): Promise<BorradorTecnologia>;
}

export const REPOSITORIO_BORRADORES_TECNOLOGIA =
  new InjectionToken<RepositorioBorradoresTecnologia>('RepositorioBorradoresTecnologia');
