import { CarritoIdLocalStorageAlmacen } from './carrito-id.almacen';

describe('CarritoIdLocalStorageAlmacen', () => {
  let almacen: CarritoIdLocalStorageAlmacen;

  beforeEach(() => {
    window.localStorage.clear();
    almacen = new CarritoIdLocalStorageAlmacen();
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
