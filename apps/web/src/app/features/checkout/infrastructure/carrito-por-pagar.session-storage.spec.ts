import { CarritoPorPagarSessionStorage } from './carrito-por-pagar.session-storage';

describe('CarritoPorPagarSessionStorage', () => {
  let almacen: CarritoPorPagarSessionStorage;

  beforeEach(() => {
    window.sessionStorage.clear();
    almacen = new CarritoPorPagarSessionStorage();
  });

  it('recuerda el carrito y las líneas que fueron al pedido', () => {
    almacen.anotar('pedido-1', 'carrito-1', ['linea-1', 'linea-2']);

    expect(almacen.carritoDe('pedido-1')).toBe('carrito-1');
    expect(almacen.lineasDe('pedido-1')).toEqual(['linea-1', 'linea-2']);
  });

  /** Lo anotado antes de que existiera la selección: el carrito se vacía entero, como entonces. */
  it('sin líneas anotadas no sabe cuáles, en vez de decir que ninguna', () => {
    window.sessionStorage.setItem('ts-carrito-por-pagar:pedido-1', 'carrito-1');

    expect(almacen.carritoDe('pedido-1')).toBe('carrito-1');
    expect(almacen.lineasDe('pedido-1')).toBeNull();
  });

  it('unas líneas corruptas se leen como no sabidas', () => {
    window.sessionStorage.setItem('ts-carrito-por-pagar-lineas:pedido-1', '{no es json');
    expect(almacen.lineasDe('pedido-1')).toBeNull();

    window.sessionStorage.setItem('ts-carrito-por-pagar-lineas:pedido-1', '["linea-1", 3]');
    expect(almacen.lineasDe('pedido-1')).toBeNull();
  });

  it('olvidar borra las dos cosas', () => {
    almacen.anotar('pedido-1', 'carrito-1', ['linea-1']);

    almacen.olvidar('pedido-1');

    expect(almacen.carritoDe('pedido-1')).toBeNull();
    expect(almacen.lineasDe('pedido-1')).toBeNull();
  });
});
