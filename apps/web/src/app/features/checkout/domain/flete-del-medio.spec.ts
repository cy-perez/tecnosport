import { fleteDelMedio } from './flete-del-medio';
import { MetodoDePagoConPrecio } from './pedido.model';

const CONTRAENTREGA: MetodoDePagoConPrecio = {
  metodo: 'CONTRAENTREGA',
  subtotal: { valor: 150_000, moneda: 'COP' },
  descuento: { valor: 0, moneda: 'COP' },
};

describe('fleteDelMedio', () => {
  it('con el flete del medio, cobra ese y no el de la cotización', () => {
    expect(
      fleteDelMedio({ ...CONTRAENTREGA, costoEnvio: { valor: 14_200, moneda: 'COP' } }, 9_540),
    ).toBe(14_200);
  });

  it('un flete del medio en cero también manda: no es lo mismo que no traerlo', () => {
    expect(
      fleteDelMedio({ ...CONTRAENTREGA, costoEnvio: { valor: 0, moneda: 'COP' } }, 9_540),
    ).toBe(0);
  });

  it('sin flete del medio —el contrato de hoy—, se queda con el de la cotización', () => {
    expect(fleteDelMedio(CONTRAENTREGA, 9_540)).toBe(9_540);
    expect(fleteDelMedio({ ...CONTRAENTREGA, costoEnvio: null }, 9_540)).toBe(9_540);
  });

  it('sin medio elegido todavía, el de la cotización', () => {
    expect(fleteDelMedio(null, 9_540)).toBe(9_540);
  });
});
