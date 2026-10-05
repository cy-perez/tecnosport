import { CarritoIdLocalStorageAlmacen } from './carrito-id.almacen';

describe('CarritoIdLocalStorageAlmacen', () => {
  let almacen: CarritoIdLocalStorageAlmacen;

  beforeEach(() => {
    window.localStorage.clear();
    almacen = new CarritoIdLocalStorageAlmacen();
  });

  /**
   * Safari con cookies bloqueadas, el navegador de Instagram o la cuota llena: el almacenamiento
   * lanza. "Agregar al carrito" no hacía nada; ahora el carrito sigue en memoria.
   */
  it('si el almacenamiento lanza, no lanza: lee nada y guarda en vano', () => {
    const leer = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('bloqueado', 'SecurityError');
    });
    const guardar = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('lleno', 'QuotaExceededError');
    });

    expect(() => almacen.guardar('carrito-1')).not.toThrow();
    expect(almacen.leer()).toBeNull();
    expect(() => almacen.borrar()).not.toThrow();

    leer.mockRestore();
    guardar.mockRestore();
  });

  it('devuelve null si no hay nada guardado', () => {
    expect(almacen.leer()).toBeNull();
  });

  it('guarda y vuelve a leer el mismo id', () => {
    almacen.guardar('carrito-1');

    expect(almacen.leer()).toBe('carrito-1');
  });

  it('borrar deja de encontrar el id', () => {
    almacen.guardar('carrito-1');

    almacen.borrar();

    expect(almacen.leer()).toBeNull();
  });
});
