import { fireEvent, render, screen } from '@testing-library/angular';
import { OpcionMetodoPago, TsSelectorMetodoPago } from './ts-selector-metodo-pago';

const opciones: OpcionMetodoPago[] = [
  { valor: 'TARJETA', etiqueta: 'Tarjeta' },
  { valor: 'CONTRAENTREGA', etiqueta: 'Contraentrega' },
];

describe('TsSelectorMetodoPago', () => {
  it('elegir una opción emite su valor', async () => {
    let emitido: unknown;
    await render(TsSelectorMetodoPago, {
      inputs: { opciones, seleccionado: null, etiquetaGrupo: 'Método de pago' },
      on: { seleccionCambio: (valor) => (emitido = valor) },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Contraentrega' }));

    expect(emitido).toBe('CONTRAENTREGA');
  });

  it('marca la opción elegida con aria-pressed', async () => {
    await render(TsSelectorMetodoPago, {
      inputs: { opciones, seleccionado: 'TARJETA', etiquetaGrupo: 'Método de pago' },
    });

    expect(screen.getByRole('button', { name: 'Tarjeta' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: 'Contraentrega' }).getAttribute('aria-pressed')).toBe('false');
  });

  it('usa etiquetaGrupo como leyenda del grupo', async () => {
    await render(TsSelectorMetodoPago, {
      inputs: { opciones, seleccionado: null, etiquetaGrupo: 'Método de pago' },
    });

    expect(screen.getByText('Método de pago')).toBeTruthy();
  });
});
