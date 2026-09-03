import {
  borrarCarritoIdAlmacenado,
  guardarCarritoIdAlmacenado,
  leerCarritoIdAlmacenado,
} from './carrito-id.almacen';

describe('carrito-id.almacen', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('devuelve null si no hay nada guardado', () => {
    expect(leerCarritoIdAlmacenado()).toBeNull();
  });

  it('guarda y vuelve a leer el mismo id', () => {
    guardarCarritoIdAlmacenado('carrito-1');

    expect(leerCarritoIdAlmacenado()).toBe('carrito-1');
  });

  it('borrar deja de encontrar el id', () => {
    guardarCarritoIdAlmacenado('carrito-1');

    borrarCarritoIdAlmacenado();

    expect(leerCarritoIdAlmacenado()).toBeNull();
  });
});
