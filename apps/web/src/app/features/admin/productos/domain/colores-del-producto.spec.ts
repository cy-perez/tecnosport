import { describe, expect, it } from 'vitest';
import { coloresDelProducto, modelosSinColor, tieneColor } from './colores-del-producto';
import { VarianteResumenAdmin } from './producto-admin.model';

function variante(id: string, talla: string | null, color: string | null): VarianteResumenAdmin {
  return {
    id,
    sku: id,
    atributos: [
      ...(color ? [{ nombre: 'Color', valor: color, colorHex: '#111111' }] : []),
      ...(talla ? [{ nombre: 'Talla', valor: talla, colorHex: null }] : []),
    ],
  };
}

describe('colores del producto', () => {
  it('una variante por talla, la primera de cada una, sin importar en cuántos colores esté', () => {
    const variantes = [
      variante('n-s', 'S-M', 'Negro'),
      variante('n-l', 'L-XL', 'Negro'),
      variante('v-s', 'S-M', 'Vino'),
      variante('v-l', 'L-XL', 'Vino'),
    ];

    expect(modelosSinColor(variantes)).toEqual([
      { modeloId: 'n-s', etiqueta: 'S-M' },
      { modeloId: 'n-l', etiqueta: 'L-XL' },
    ]);
  });

  it('un producto que no talla tiene un solo modelo, sin etiqueta', () => {
    expect(modelosSinColor([variante('n', null, 'Negro'), variante('v', null, 'Vino')])).toEqual([
      { modeloId: 'n', etiqueta: '' },
    ]);
  });

  it('dice si tiene color y cuáles, sin repetir', () => {
    const sinColor = [variante('s', 'S-M', null)];
    const conColor = [variante('n-s', 'S-M', 'Negro'), variante('n-l', 'L-XL', 'Negro')];

    expect(tieneColor(sinColor)).toBe(false);
    expect(coloresDelProducto(sinColor)).toEqual([]);
    expect(tieneColor(conColor)).toBe(true);
    expect(coloresDelProducto(conColor)).toEqual(['Negro']);
  });
});
