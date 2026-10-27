import { LineasSinMarcarLocalStorageAlmacen } from './lineas-sin-marcar.almacen';

describe('LineasSinMarcarLocalStorageAlmacen', () => {
  let almacen: LineasSinMarcarLocalStorageAlmacen;

  beforeEach(() => {
    window.localStorage.clear();
    almacen = new LineasSinMarcarLocalStorageAlmacen();
  });

  it('sin nada guardado no hay ninguna desmarcada', () => {
    expect(almacen.leer()).toEqual([]);
  });

  it('guarda y vuelve a leer las desmarcadas', () => {
    almacen.guardar(['linea-1', 'linea-2']);

    expect(new LineasSinMarcarLocalStorageAlmacen().leer()).toEqual(['linea-1', 'linea-2']);
  });

  it('guardar ninguna borra la clave', () => {
    almacen.guardar(['linea-1']);
    almacen.guardar([]);

    expect(window.localStorage.getItem('ts-carrito-sin-marcar')).toBeNull();
  });

  it('un valor corrupto o con otra forma se lee como ninguna', () => {
    window.localStorage.setItem('ts-carrito-sin-marcar', '{no es json');
    expect(almacen.leer()).toEqual([]);

    window.localStorage.setItem('ts-carrito-sin-marcar', JSON.stringify({ linea: 1 }));
    expect(almacen.leer()).toEqual([]);

    window.localStorage.setItem('ts-carrito-sin-marcar', JSON.stringify(['linea-1', 7]));
    expect(almacen.leer()).toEqual(['linea-1']);
  });
});
