import { InjectionToken } from '@angular/core';
import { Marca } from './producto.model';

export interface RepositorioMarcas {
  listarTodas(): Promise<Marca[]>;
}

export const REPOSITORIO_MARCAS = new InjectionToken<RepositorioMarcas>('RepositorioMarcas');
