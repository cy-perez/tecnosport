import { InjectionToken } from '@angular/core';
import { Categoria } from './producto.model';

export interface RepositorioCategorias {
  listarTodas(): Promise<Categoria[]>;
}

export const REPOSITORIO_CATEGORIAS = new InjectionToken<RepositorioCategorias>('RepositorioCategorias');
