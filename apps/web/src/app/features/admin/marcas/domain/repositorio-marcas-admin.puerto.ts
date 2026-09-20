import { InjectionToken } from '@angular/core';
import { Marca } from '../../../catalogo/domain/producto.model';

/**
 * Lo que el panel puede hacer con las marcas: verlas todas y crear una.
 *
 * Separado de `RepositorioMarcas` (el de la vitrina) a propósito: aquel lo implementa también el
 * adaptador público, y la tienda nunca debe poder crear marcas. Un puerto que su implementación
 * pública no puede cumplir se acaba cumpliendo con un método que lanza.
 */
export interface RepositorioMarcasAdmin {
  listarTodas(): Promise<Marca[]>;
  crear(nombre: string): Promise<ResultadoCrearMarca>;
}

/**
 * El nombre repetido no es un fallo: es una respuesta. Se devuelve como resultado y no como
 * excepción por lo mismo que `SIN_COBERTURA` en el checkout — la pantalla tiene que decir algo
 * concreto, y para eso no puede andar leyendo códigos HTTP.
 */
export type ResultadoCrearMarca =
  | { readonly tipo: 'CREADA'; readonly marca: Marca }
  | { readonly tipo: 'YA_EXISTE' };

export const REPOSITORIO_MARCAS_ADMIN = new InjectionToken<RepositorioMarcasAdmin>(
  'RepositorioMarcasAdmin',
);
