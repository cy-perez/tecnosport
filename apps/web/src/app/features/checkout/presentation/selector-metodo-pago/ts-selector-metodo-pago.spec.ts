import { fireEvent, render, screen } from '@testing-library/angular';
import { OpcionMetodoPago, TsSelectorMetodoPago } from './ts-selector-metodo-pago';

const opciones: OpcionMetodoPago[] = [
  {
    valor: 'WOMPI',
    etiqueta: 'Tarjeta',
    detalle: 'Pagar en Wompi con tarjeta, PSE o Bancolombia.',
  },
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
      inputs: { opciones, seleccionado: 'WOMPI', etiquetaGrupo: 'Método de pago' },
    });

    expect(screen.getByRole('button', { name: 'Tarjeta' }).getAttribute('aria-pressed')).toBe(
      'true',
    );
    expect(screen.getByRole('button', { name: 'Contraentrega' }).getAttribute('aria-pressed')).toBe(
      'false',
    );
  });

  it('usa etiquetaGrupo como leyenda del grupo', async () => {
    await render(TsSelectorMetodoPago, {
      inputs: { opciones, seleccionado: null, etiquetaGrupo: 'Método de pago' },
    });

    expect(screen.getByText('Método de pago')).toBeTruthy();
  });

  /**
   * El detalle se pinta al frente del botón, fuera de él. Lo que antes hacía el DOM solo —meterlo
   * en el nombre accesible— ahora lo sostiene el `aria-describedby`: si alguien lo quita, el nombre
   * sigue siendo correcto y la prueba de "aria-pressed" sigue pasando, así que hace falta esta.
   */
  it('el detalle queda fuera del botón y enlazado como su descripción', async () => {
    await render(TsSelectorMetodoPago, {
      inputs: { opciones, seleccionado: null, etiquetaGrupo: 'Método de pago' },
    });

    const boton = screen.getByRole('button', { name: 'Tarjeta' });
    const detalle = screen.getByText('Pagar en Wompi con tarjeta, PSE o Bancolombia.');

    expect(boton.contains(detalle)).toBe(false);
    expect(boton.getAttribute('aria-describedby')).toBe(detalle.id);
  });

  /** Una opción sin detalle no enlaza nada: un `aria-describedby` a un id vacío no describe nada. */
  it('una opción sin detalle no declara aria-describedby', async () => {
    await render(TsSelectorMetodoPago, {
      inputs: { opciones, seleccionado: null, etiquetaGrupo: 'Método de pago' },
    });

    expect(
      screen.getByRole('button', { name: 'Contraentrega' }).getAttribute('aria-describedby'),
    ).toBeNull();
  });
});
