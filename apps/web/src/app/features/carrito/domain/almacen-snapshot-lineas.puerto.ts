import { InjectionToken } from '@angular/core';
import { SnapshotLinea } from './snapshot-linea.model';

/**
 * Dónde quedan las fotos de línea entre visitas. `GET /carritos/{id}` solo devuelve `varianteId` y
 * cantidad (docs/03-api.md), así que el nombre, la imagen y el precio con los que se pinta el
 * carrito salen de aquí — nunca son autoritativos, y el backend recalcula antes de cobrar
 * (regla dura #7).
 */
export interface AlmacenSnapshotLineas {
  /** `null` si esa variante no tiene foto guardada, o si corre en el servidor. */
  leer(varianteId: string): SnapshotLinea | null;
  guardar(snapshot: SnapshotLinea): void;
}

export const ALMACEN_SNAPSHOT_LINEAS = new InjectionToken<AlmacenSnapshotLineas>(
  'AlmacenSnapshotLineas',
);
