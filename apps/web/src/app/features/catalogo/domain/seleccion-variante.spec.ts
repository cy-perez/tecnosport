import { Producto, Variante } from './producto.model';
import { ejesDeAtributos, seleccionDeVariante, variantePorDefecto, varianteSeleccionada } from './seleccion-variante';

function variante(sku: string, existencia: number, atributos: Variante['atributos']): Variante {
  return { sku, precio: { valor: 100_000, moneda: 'COP' }, existencia, atributos };
}

function productoDePrueba(variantes: Variante[]): Producto {
  return {
    slug: 'camiseta',
    nombre: 'Camiseta',
    descripcion: '',
    marca: { id: '1', nombre: 'TecnoSport' },
    categoria: { nombre: 'Ropa', slug: 'ropa', linea: 'ROPA_Y_CALZADO' },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    variantes,
  };
}

const azulM = variante('SKU-AZ-M', 5, [
  { nombre: 'Color', valor: 'Azul marino', colorHex: '#1E3A8A' },
  { nombre: 'Talla', valor: 'M', colorHex: null },
]);
const negroL = variante('SKU-NG-L', 0, [
  { nombre: 'Color', valor: 'Negro', colorHex: '#111111' },
  { nombre: 'Talla', valor: 'L', colorHex: null },
]);

describe('ejesDeAtributos', () => {
  it('agrupa un eje por nombre de atributo con sus valores distintos', () => {
    const ejes = ejesDeAtributos(productoDePrueba([azulM, negroL]));

    expect(ejes).toEqual([
      {
        nombre: 'Color',
        opciones: [
          { valor: 'Azul marino', colorHex: '#1E3A8A' },
          { valor: 'Negro', colorHex: '#111111' },
        ],
      },
      {
        nombre: 'Talla',
        opciones: [
          { valor: 'M', colorHex: null },
          { valor: 'L', colorHex: null },
        ],
      },
    ]);
  });

  it('un producto sin variantes no tiene ejes', () => {
    expect(ejesDeAtributos(productoDePrueba([]))).toEqual([]);
  });
});

describe('seleccionDeVariante', () => {
  it('convierte los atributos de una variante en un mapa nombre -> valor', () => {
    expect(seleccionDeVariante(azulM)).toEqual({ Color: 'Azul marino', Talla: 'M' });
  });
});

describe('varianteSeleccionada', () => {
  it('encuentra la variante que coincide con la selección completa', () => {
    const producto = productoDePrueba([azulM, negroL]);

    expect(varianteSeleccionada(producto, { Color: 'Negro', Talla: 'L' })).toBe(negroL);
  });

  it('devuelve null si la combinación no corresponde a ningún SKU real', () => {
    const producto = productoDePrueba([azulM, negroL]);

    expect(varianteSeleccionada(producto, { Color: 'Azul marino', Talla: 'L' })).toBeNull();
  });
});

describe('variantePorDefecto', () => {
  it('prefiere la primera variante con existencia', () => {
    expect(variantePorDefecto(productoDePrueba([negroL, azulM]))).toBe(azulM);
  });

  it('si ninguna tiene existencia, devuelve la primera', () => {
    const agotada = variante('SKU-AGOTADA', 0, []);
    expect(variantePorDefecto(productoDePrueba([negroL, agotada]))).toBe(negroL);
  });

  it('un producto sin variantes devuelve null', () => {
    expect(variantePorDefecto(productoDePrueba([]))).toBeNull();
  });
});
