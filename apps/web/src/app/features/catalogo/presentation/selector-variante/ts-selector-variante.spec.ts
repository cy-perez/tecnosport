import { fireEvent, render, screen } from '@testing-library/angular';
import { EjeAtributo } from '../../domain/seleccion-variante';
import { TsSelectorVariante } from './ts-selector-variante';

const ejes: EjeAtributo[] = [
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
];

describe('TsSelectorVariante', () => {
  it('elegir un color emite la selección con ese eje actualizado', async () => {
    let emitido: unknown;
    await render(TsSelectorVariante, {
      inputs: { ejes, seleccion: { Color: 'Azul marino', Talla: 'M' } },
      on: { seleccionCambio: (valor) => (emitido = valor) },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Negro' }));

    expect(emitido).toEqual({ Color: 'Negro', Talla: 'M' });
  });

  it('elegir una talla emite la selección con ese eje actualizado', async () => {
    let emitido: unknown;
    await render(TsSelectorVariante, {
      inputs: { ejes, seleccion: { Color: 'Azul marino', Talla: 'M' } },
      on: { seleccionCambio: (valor) => (emitido = valor) },
    });

    fireEvent.click(screen.getByRole('button', { name: 'L' }));

    expect(emitido).toEqual({ Color: 'Azul marino', Talla: 'L' });
  });

  it('marca la opción activa con aria-pressed', async () => {
    await render(TsSelectorVariante, {
      inputs: { ejes, seleccion: { Color: 'Azul marino', Talla: 'M' } },
    });

    expect(screen.getByRole('button', { name: 'Azul marino' }).getAttribute('aria-pressed')).toBe(
      'true',
    );
    expect(screen.getByRole('button', { name: 'Negro' }).getAttribute('aria-pressed')).toBe(
      'false',
    );
  });
});
