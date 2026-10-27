import { Injectable } from '@angular/core';
import { AlmacenLineasSinMarcar } from '../domain/almacen-lineas-sin-marcar.puerto';

const CLAVE = 'ts-carrito-sin-marcar';

/**
 * `localStorage`, como el id del carrito: la selección es de ese carrito y vive lo que él. Cada
 * acceso va en `try`: sin almacenamiento lo único que se pierde es recordar las casillas, y
 * entonces todo vuelve a marcado, que es pagar el carrito entero.
 */
@Injectable()
export class LineasSinMarcarLocalStorageAlmacen implements AlmacenLineasSinMarcar {
  leer(): readonly string[] {
    if (typeof window === 'undefined') {
      return [];
    }
    try {
      const crudo = window.localStorage.getItem(CLAVE);
      const leido: unknown = crudo ? JSON.parse(crudo) : [];
      return Array.isArray(leido) ? leido.filter((id) => typeof id === 'string') : [];
    } catch {
      return [];
    }
  }

  guardar(lineaIds: readonly string[]): void {
    if (typeof window === 'undefined') {
      return;
    }
    try {
      if (lineaIds.length === 0) {
        window.localStorage.removeItem(CLAVE);
      } else {
        window.localStorage.setItem(CLAVE, JSON.stringify(lineaIds));
      }
    } catch {
      // Sin almacenamiento o con la cuota llena: la selección vale mientras dure la pestaña.
    }
  }
}
