import { describe, expect, it } from 'vitest';
import { VarianteResumenAdmin } from './producto-admin.model';
import { tallasDelProducto } from './tallas-del-producto';

function variante(
  id: string,
  atributos: readonly [nombre: string, valor: string, colorHex?: string][],
): VarianteResumenAdmin {
  return {
    id,
    sku: id,
    atributos: atributos.map(([nombre, valor, colorHex]) => ({
      nombre,
      valor,
      colorHex: colorHex ?? null,
    })),
  };
}

describe('tallas del producto', () => {
  it('una por modelo, sin repetirla por cada color', () => {
    const variantes = [
      variante('n-s', [
        ['Color', 'Negro', '#111111'],
        ['Talla', 'S-M'],
      ]),
      variante('n-l', [
        ['Color', 'Negro', '#111111'],
        ['Talla', 'L-XL'],
      ]),
      variante('v-s', [
        ['Color', 'Vino', '#722F37'],
        ['Talla', 'S-M'],
      ]),
    ];

    expect(tallasDelProducto(variantes)).toEqual([
      { modeloId: 'n-s', talla: 'S-M', etiqueta: 'S-M' },
      { modeloId: 'n-l', talla: 'L-XL', etiqueta: 'L-XL' },
    ]);
  });

  it('reconoce el eje por su nombre, «Talla calzado» también, y deja la etiqueta completa', () => {
    const variantes = [
      variante('a', [
        ['Talla calzado', '38'],
        ['Material', 'Cuero'],
      ]),
    ];

    expect(tallasDelProducto(variantes)).toEqual([
      { modeloId: 'a', talla: '38', etiqueta: '38 · Cuero' },
    ]);
  });

  it('un producto que no talla no tiene tallas que corregir', () => {
    expect(tallasDelProducto([variante('n', [['Color', 'Negro', '#111111']])])).toEqual([]);
    expect(tallasDelProducto([])).toEqual([]);
  });
});
