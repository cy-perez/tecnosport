import { Injectable } from '@angular/core';
import { AlmacenCarritoId } from '../domain/almacen-carrito-id.puerto';

const CLAVE = 'ts-carrito-id';

/**
 * `typeof window` y no `isPlatformBrowser` porque el almacén se construye por DI en un contexto
 * donde no siempre hay inyector disponible (lo usan las pruebas directamente), y el guardia tiene
 * que valer igual: en SSR no hay `localStorage` y leerlo revienta el render.
 */
@Injectable()
export class CarritoIdLocalStorageAlmacen implements AlmacenCarritoId {
  leer(): string | null {
    if (typeof window === 'undefined') {
      return null;
    }
    return window.localStorage.getItem(CLAVE);
  }

  guardar(id: string): void {
    if (typeof window === 'undefined') {
      return;
    }
    window.localStorage.setItem(CLAVE, id);
  }

  borrar(): void {
    if (typeof window === 'undefined') {
      return;
    }
    window.localStorage.removeItem(CLAVE);
  }
}
