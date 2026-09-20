import { aIntentoDePago, aIntentoSistecredito } from './mapeador-pago';

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

describe('aIntentoSistecredito', () => {
  it('mapea el DTO completo al modelo de dominio', () => {
    expect(
      aIntentoSistecredito({
        referencia: 'TS-2026-000123-1',
        monto: { valor: 189_900, moneda: 'COP' },
        urlRedireccion: 'https://siste.credinet.co/pago/abc',
      }),
    ).toEqual({
      referencia: 'TS-2026-000123-1',
      monto: { valor: 189_900, moneda: 'COP' },
      urlRedireccion: 'https://siste.credinet.co/pago/abc',
    });
  });

  it('campos ausentes se rellenan con valores por defecto, nunca undefined', () => {
    expect(aIntentoSistecredito({})).toEqual({
      referencia: '',
      monto: { valor: 0, moneda: 'COP' },
      urlRedireccion: '',
    });
  });
});
