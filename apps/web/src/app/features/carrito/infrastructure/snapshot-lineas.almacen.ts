import { SnapshotLinea } from '../domain/snapshot-linea.model';

const CLAVE = 'ts-carrito-snapshots';

function leerTodos(): Record<string, SnapshotLinea> {
  if (typeof window === 'undefined') {
    return {};
  }
  const crudo = window.localStorage.getItem(CLAVE);
  if (!crudo) {
    return {};
  }
  try {
    return JSON.parse(crudo) as Record<string, SnapshotLinea>;
  } catch {
    return {};
  }
}

export function leerSnapshot(varianteId: string): SnapshotLinea | null {
  return leerTodos()[varianteId] ?? null;
}

export function guardarSnapshot(snapshot: SnapshotLinea): void {
  if (typeof window === 'undefined') {
    return;
  }
  const todos = leerTodos();
  todos[snapshot.varianteId] = snapshot;
  window.localStorage.setItem(CLAVE, JSON.stringify(todos));
}
