import { Injectable } from '@angular/core';
import { AlmacenCarritoId } from '../domain/almacen-carrito-id.puerto';

const CLAVE = 'ts-carrito-id';

/**
 * `typeof window` y no `isPlatformBrowser` porque el almacén se construye por DI en un contexto
 * donde no siempre hay inyector disponible (lo usan las pruebas directamente), y el guardia tiene
 * que valer igual: en SSR no hay `localStorage` y leerlo revienta el render.
 *
 * <p>Y cada acceso en `try`: en Safari con las cookies bloqueadas, en el navegador de Instagram o
 * con la cuota llena, `localStorage` lanza en vez de devolver vacío. Sin esto, "Agregar al carrito"
 * no hacía nada y no decía nada. Sin almacenamiento el carrito funciona igual mientras dure la
 * pestaña; solo no sobrevive a un refresco.
 */
@Injectable()
export class CarritoIdLocalStorageAlmacen implements AlmacenCarritoId {
  leer(): string | null {
    try {
      return typeof window === 'undefined' ? null : window.localStorage.getItem(CLAVE);
    } catch {
      return null;
    }
  }

  guardar(id: string): void {
    try {
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(CLAVE, id);
      }
    } catch {
      // Sin almacenamiento: el carrito vive en memoria mientras dure la pestaña.
    }
  }

  borrar(): void {
    try {
      if (typeof window !== 'undefined') {
        window.localStorage.removeItem(CLAVE);
      }
    } catch {
      // Nada que borrar donde no se pudo escribir.
    }
  }
}
