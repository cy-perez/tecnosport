import { Injectable } from '@angular/core';
import { AlmacenSnapshotLineas } from '../domain/almacen-snapshot-lineas.puerto';
import { SnapshotLinea } from '../domain/snapshot-linea.model';

const CLAVE = 'ts-carrito-snapshots';

@Injectable()
export class SnapshotLineasLocalStorageAlmacen implements AlmacenSnapshotLineas {
  leer(varianteId: string): SnapshotLinea | null {
    return this.leerTodos()[varianteId] ?? null;
  }

  guardar(snapshot: SnapshotLinea): void {
    if (typeof window === 'undefined') {
      return;
    }
    const todos = this.leerTodos();
    todos[snapshot.varianteId] = snapshot;
    try {
      window.localStorage.setItem(CLAVE, JSON.stringify(todos));
    } catch {
      // Sin almacenamiento o con la cuota llena: la foto es solo para pintar nombre e imagen, y
      // perderla no puede impedir agregar al carrito, que es lo que pasaba.
    }
  }

  /** Un JSON corrupto no debe tumbar el carrito: se trata como "no hay nada guardado". */
  private leerTodos(): Record<string, SnapshotLinea> {
    if (typeof window === 'undefined') {
      return {};
    }
    try {
      const crudo = window.localStorage.getItem(CLAVE);
      return crudo ? (JSON.parse(crudo) as Record<string, SnapshotLinea>) : {};
    } catch {
      return {};
    }
  }
}
