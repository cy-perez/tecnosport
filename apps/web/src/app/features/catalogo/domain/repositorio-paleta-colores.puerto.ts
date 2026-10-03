import { InjectionToken } from '@angular/core';
import { ColorDePaleta } from './producto.model';

export interface RepositorioPaletaColores {
  listarTodos(): Promise<ColorDePaleta[]>;
}

export const REPOSITORIO_PALETA_COLORES = new InjectionToken<RepositorioPaletaColores>(
  'RepositorioPaletaColores',
);
