const CLAVE = 'ts-carrito-id';

export function leerCarritoIdAlmacenado(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }
  return window.localStorage.getItem(CLAVE);
}

export function guardarCarritoIdAlmacenado(id: string): void {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.setItem(CLAVE, id);
}

export function borrarCarritoIdAlmacenado(): void {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.removeItem(CLAVE);
}
