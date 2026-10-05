import { Injectable } from '@angular/core';
import { AlmacenCorreoDePedido } from '../domain/almacen-correo-de-pedido.puerto';

const PREFIJO = 'ts-correo-pedido:';

/**
 * `sessionStorage` y no `localStorage`: el correo solo hace falta para volver de la pasarela en la
 * misma pestaña, y no tiene por qué sobrevivir a ella.
 *
 * Cada acceso va en `try`: en Safari con las cookies bloqueadas, en el navegador de Instagram o en
 * modo privado el almacenamiento lanza en vez de devolver vacío, y perder el recordatorio no puede
 * romper el pago —la pantalla de estado le pide el correo al comprador—.
 */
@Injectable()
export class CorreoDePedidoSessionStorage implements AlmacenCorreoDePedido {
  recordar(pedidoId: string, correo: string): void {
    try {
      if (typeof window !== 'undefined') {
        window.sessionStorage.setItem(PREFIJO + pedidoId, correo);
      }
    } catch {
      // Sin almacenamiento disponible: la pantalla de estado lo pedirá.
    }
  }

  correoDe(pedidoId: string): string | null {
    try {
      return typeof window === 'undefined'
        ? null
        : window.sessionStorage.getItem(PREFIJO + pedidoId);
    } catch {
      return null;
    }
  }
}
