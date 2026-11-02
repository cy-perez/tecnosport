import { InjectionToken } from '@angular/core';
import { TallasDisponibles } from './tallas.model';

/** Las tallas de ropa y de calzado que tienen algo que comprar detrás. */
export interface RepositorioTallas {
  listarDisponibles(): Promise<TallasDisponibles>;
}

export const REPOSITORIO_TALLAS = new InjectionToken<RepositorioTallas>('RepositorioTallas');
