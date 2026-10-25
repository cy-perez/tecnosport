import { InjectionToken } from '@angular/core';
import { EnlaceDeCompra } from './enlace-de-compra.model';

export interface RepositorioEnlacesDeCompra {
  /** `null` si ya no sirve: vencido, usado, anulado o inventado. La API no dice cuál. */
  consultar(token: string): Promise<EnlaceDeCompra | null>;
}

export const REPOSITORIO_ENLACES_DE_COMPRA = new InjectionToken<RepositorioEnlacesDeCompra>(
  'RepositorioEnlacesDeCompra',
);
