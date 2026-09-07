import { Provider } from '@angular/core';
import { SnapshotLinea } from '../app/features/carrito/domain/snapshot-linea.model';
import { ALMACEN_CARRITO_ID } from '../app/features/carrito/domain/almacen-carrito-id.puerto';
import { ALMACEN_SNAPSHOT_LINEAS } from '../app/features/carrito/domain/almacen-snapshot-lineas.puerto';
import { CarritoIdLocalStorageAlmacen } from '../app/features/carrito/infrastructure/carrito-id.almacen';
import { SnapshotLineasLocalStorageAlmacen } from '../app/features/carrito/infrastructure/snapshot-lineas.almacen';

/**
 * Los mismos adaptadores que provee `app.config.ts`, no dobles: las pruebas ya siembran y limpian
 * `window.localStorage` de verdad —jsdom lo trae— así que un doble cambiaría lo que se prueba sin
 * ganar nada. Existe para que `CarritoStore` se pueda construir en cualquier prueba que lo alcance:
 * lo inyectan el encabezado (visible siempre), la ficha, el carrito y tres pantallas del checkout.
 */
export function proveerAlmacenesCarrito(): Provider[] {
  return [
    { provide: ALMACEN_CARRITO_ID, useClass: CarritoIdLocalStorageAlmacen },
    { provide: ALMACEN_SNAPSHOT_LINEAS, useClass: SnapshotLineasLocalStorageAlmacen },
  ];
}

/**
 * Sembrar el escenario pasa por aquí y no por un import directo al adaptador, para que la salida de
 * `npm run capas` —que ahora corre en cada build— no traiga seis avisos fijos. Un guardián cuya
 * salida siempre tiene ruido es un guardián que se aprende a ignorar.
 */
export function sembrarCarritoId(id: string): void {
  new CarritoIdLocalStorageAlmacen().guardar(id);
}

export function sembrarSnapshotLinea(snapshot: SnapshotLinea): void {
  new SnapshotLineasLocalStorageAlmacen().guardar(snapshot);
}
