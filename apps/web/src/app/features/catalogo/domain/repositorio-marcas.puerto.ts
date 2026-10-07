import { InjectionToken } from '@angular/core';
import { Marca, MarcaDeVitrina } from './producto.model';

/**
 * Las marcas que se pueden ofrecer en un desplegable, sin más.
 *
 * Lo cumplen los dos adaptadores —el público y el del panel— y por eso no sabe de líneas: el panel
 * las lista todas, incluida la que no tiene ni un producto, que es justamente la que hace falta
 * para cargarle el primero. Preguntarle a esa en qué líneas vende no tiene respuesta.
 */
export interface RepositorioMarcas {
  listarTodas(): Promise<Marca[]>;
}

export const REPOSITORIO_MARCAS = new InjectionToken<RepositorioMarcas>('RepositorioMarcas');

/**
 * Las del filtro de la vitrina: solo las que tienen algo publicado, y cada una con las líneas en
 * las que lo tiene.
 *
 * <b>Un puerto aparte y no un método más en `RepositorioMarcas`</b>, por lo mismo que en el
 * backend `MarcaDeVitrinaRespuesta` no es un campo de `MarcaRespuesta`: el adaptador del panel
 * tendría que cumplirlo devolviendo una lista de líneas vacía en cada marca, y vacía no
 * significaría "no vende nada" sino "nadie se lo preguntó". Un puerto que una de sus
 * implementaciones solo puede cumplir mintiendo acaba con un método que lanza.
 */
export interface RepositorioMarcasDeVitrina {
  listarDeVitrina(): Promise<MarcaDeVitrina[]>;
}

export const REPOSITORIO_MARCAS_DE_VITRINA = new InjectionToken<RepositorioMarcasDeVitrina>(
  'RepositorioMarcasDeVitrina',
);
