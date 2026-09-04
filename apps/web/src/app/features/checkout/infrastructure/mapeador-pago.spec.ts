import { aIntentoDePago } from './mapeador-pago';

describe('aIntentoDePago', () => {
  it('mapea el DTO completo al modelo de dominio', () => {
    const intento = aIntentoDePago({
      referencia: 'TS-2026-000123-1',
      monto: { valor: 189_900, moneda: 'COP' },
      firmaIntegridad: 'abc123firma',
      llavePublica: 'pub_test_xyz',
      ambiente: 'sandbox',
    });

    expect(intento).toEqual({
      referencia: 'TS-2026-000123-1',
      monto: { valor: 189_900, moneda: 'COP' },
      firmaIntegridad: 'abc123firma',
      llavePublica: 'pub_test_xyz',
      ambiente: 'sandbox',
    });
  });

  it('campos ausentes se rellenan con valores por defecto, nunca undefined', () => {
    expect(aIntentoDePago({})).toEqual({
      referencia: '',
      monto: { valor: 0, moneda: 'COP' },
      firmaIntegridad: '',
      llavePublica: '',
      ambiente: '',
    });
  });
});
