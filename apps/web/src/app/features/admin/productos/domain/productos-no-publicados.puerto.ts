import { InjectionToken } from '@angular/core';
import { TandaDeProductosEliminados } from './producto-admin.model';

/**
 * El borrado en bloque de los productos que no están publicados. Aparte de
 * `RepositorioProductosAdmin` por lo mismo que en el servidor: es una sola pantalla, y aquel tiene
 * cinco dobles de prueba que tendrían que aprender dos métodos que no usan.
 */
export interface RepositorioProductosNoPublicados {
  /** Cuántos hay en borrador, contando los que se quedarían por tener ventas. */
  contar(): Promise<number>;
  /**
   * Una tanda: borra cada producto con sus variantes, su inventario y sus fotos, salvo los que ya
   * tienen ventas o existencias. Se le pasan el `siguiente` y el `hasta` de la tanda anterior, o
   * nulos para empezar: el tope deja fuera lo que se cree mientras corre el borrado.
   */
  eliminarTanda(desde: string | null, hasta: string | null): Promise<TandaDeProductosEliminados>;
}

export const REPOSITORIO_PRODUCTOS_NO_PUBLICADOS =
  new InjectionToken<RepositorioProductosNoPublicados>('RepositorioProductosNoPublicados');
