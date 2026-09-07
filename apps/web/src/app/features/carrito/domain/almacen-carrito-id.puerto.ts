import { InjectionToken } from '@angular/core';

/**
 * Dónde queda el id del carrito entre visitas. El carrito es anónimo —no hay sesión ni cookie— así
 * que el id lo guarda el cliente; el servidor no tiene forma de saber cuál es "el de este
 * visitante" (ver `application/carrito.store.ts` y ADR-0011).
 *
 * Es un puerto y no una función suelta porque `application` no puede depender de `localStorage`:
 * el día que esto sea una app móvil, el almacenamiento cambia y el store no se entera.
 */
export interface AlmacenCarritoId {
  /** `null` cuando no hay ninguno guardado, o cuando corre en el servidor. */
  leer(): string | null;
  guardar(id: string): void;
  borrar(): void;
}

export const ALMACEN_CARRITO_ID = new InjectionToken<AlmacenCarritoId>('AlmacenCarritoId');
