import { InjectionToken } from '@angular/core';
import { OrdenDeDifusion, PublicacionEnRed, RedSocial } from './difusion.model';

/**
 * Los rechazos de una difusión son **respuestas**, no fallos, por lo mismo que en el árbol de
 * categorías: cada uno tiene una salida concreta que la pantalla tiene que poder nombrar —"a este
 * producto le falta la imagen", "espera a que termine la anterior"— y para eso no puede andar
 * leyendo códigos HTTP.
 */
export type ResultadoDifusion =
  | { readonly tipo: 'OK'; readonly publicaciones: readonly PublicacionEnRed[] }
  | { readonly tipo: 'NO_DIFUNDIBLE' }
  | { readonly tipo: 'YA_EN_MARCHA' };

export interface RepositorioDifusion {
  difundir(orden: OrdenDeDifusion): Promise<ResultadoDifusion>;
  historial(productoId: string): Promise<PublicacionEnRed[]>;
  /**
   * El pie propuesto por el servidor.
   *
   * Lo arma el servidor y no el navegador porque lleva el precio, y el precio lo decide el
   * servidor (regla dura #7). Armarlo aquí sería el cliente eligiendo qué se anuncia.
   */
  proponerPie(productoId: string, red: RedSocial): Promise<string>;
}

export const REPOSITORIO_DIFUSION = new InjectionToken<RepositorioDifusion>('RepositorioDifusion');
