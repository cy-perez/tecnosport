import { IntentoDePago } from './intento-pago.model';
import { urlWebCheckoutWompi } from './wompi';

function intentoDePrueba(overrides: Partial<IntentoDePago> = {}): IntentoDePago {
  return {
    referencia: 'TS-2026-000123-1',
    monto: { valor: 189_900, moneda: 'COP' },
    firmaIntegridad: 'abc123firma',
    llavePublica: 'pub_test_xyz',
    ambiente: 'sandbox',
    ...overrides,
  };
}

describe('urlWebCheckoutWompi', () => {
  it('apunta al Web Checkout hospedado de Wompi', () => {
    const url = new URL(urlWebCheckoutWompi(intentoDePrueba(), 'https://tecnosport.co/es/checkout/retorno-wompi'));

    expect(url.origin + url.pathname).toBe('https://checkout.wompi.co/p/');
  });

  it('multiplica el monto por 100, aunque el peso no se fraccione (ejemplo de Wompi: 10000 = $100 COP)', () => {
    const url = new URL(urlWebCheckoutWompi(intentoDePrueba({ monto: { valor: 100, moneda: 'COP' } }), 'https://x'));

    expect(url.searchParams.get('amount-in-cents')).toBe('10000');
  });

  it('lleva los cinco parámetros obligatorios de Wompi con sus nombres exactos', () => {
    const intento = intentoDePrueba();
    const url = new URL(urlWebCheckoutWompi(intento, 'https://tecnosport.co/es/checkout/retorno-wompi'));

    expect(url.searchParams.get('public-key')).toBe('pub_test_xyz');
    expect(url.searchParams.get('currency')).toBe('COP');
    expect(url.searchParams.get('amount-in-cents')).toBe('18990000');
    expect(url.searchParams.get('reference')).toBe('TS-2026-000123-1');
    expect(url.searchParams.get('signature:integrity')).toBe('abc123firma');
  });

  it('pasa la URL de retorno tal cual, para volver con el id de transacción', () => {
    const url = new URL(urlWebCheckoutWompi(intentoDePrueba(), 'https://tecnosport.co/es/checkout/retorno-wompi'));

    expect(url.searchParams.get('redirect-url')).toBe('https://tecnosport.co/es/checkout/retorno-wompi');
  });
});
