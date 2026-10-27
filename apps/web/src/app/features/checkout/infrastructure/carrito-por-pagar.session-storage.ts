import { Injectable } from '@angular/core';
import { AlmacenCarritoPorPagar } from '../domain/almacen-carrito-por-pagar.puerto';

const PREFIJO = 'ts-carrito-por-pagar:';
/** Aparte del id y no en un JSON con él: así lo que ya está anotado se sigue leyendo igual. */
const PREFIJO_LINEAS = 'ts-carrito-por-pagar-lineas:';

/**
 * `sessionStorage`, por lo mismo que el correo del pedido (`CorreoDePedidoSessionStorage`): solo
 * hace falta para volver de la pasarela en la misma pestaña. Cada acceso va en `try` por el mismo
 * motivo: sin almacenamiento, lo único que se pierde es vaciar el carrito al volver pagado, y eso
 * el comprador lo puede hacer a mano; lo que no puede pasar es que se rompa el pago.
 */
@Injectable()
export class CarritoPorPagarSessionStorage implements AlmacenCarritoPorPagar {
  anotar(pedidoId: string, carritoId: string, lineaIds: readonly string[]): void {
    try {
      if (typeof window !== 'undefined') {
        window.sessionStorage.setItem(PREFIJO + pedidoId, carritoId);
        window.sessionStorage.setItem(PREFIJO_LINEAS + pedidoId, JSON.stringify(lineaIds));
      }
    } catch {
      // Sin almacenamiento: el carrito se queda lleno al volver, que es el mal menor.
    }
  }

  carritoDe(pedidoId: string): string | null {
    try {
      return typeof window === 'undefined'
        ? null
        : window.sessionStorage.getItem(PREFIJO + pedidoId);
    } catch {
      return null;
    }
  }

  lineasDe(pedidoId: string): readonly string[] | null {
    try {
      const crudo =
        typeof window === 'undefined'
          ? null
          : window.sessionStorage.getItem(PREFIJO_LINEAS + pedidoId);
      const leido: unknown = crudo === null ? null : JSON.parse(crudo);
      return Array.isArray(leido) && leido.every((id) => typeof id === 'string') ? leido : null;
    } catch {
      return null;
    }
  }

  olvidar(pedidoId: string): void {
    try {
      if (typeof window !== 'undefined') {
        window.sessionStorage.removeItem(PREFIJO + pedidoId);
        window.sessionStorage.removeItem(PREFIJO_LINEAS + pedidoId);
      }
    } catch {
      // Nada que olvidar si nunca se pudo guardar.
    }
  }
}
