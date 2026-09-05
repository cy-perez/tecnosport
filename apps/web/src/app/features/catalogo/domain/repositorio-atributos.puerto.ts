import { InjectionToken } from '@angular/core';
import { Atributo } from './producto.model';

export interface RepositorioAtributos {
  listarTodas(): Promise<Atributo[]>;
}

export const REPOSITORIO_ATRIBUTOS = new InjectionToken<RepositorioAtributos>('RepositorioAtributos');
