import { aCarrito, aCarritoCotizado } from './mapeador-carrito';

describe('aCarrito', () => {
  it('mapea el DTO completo al modelo de dominio', () => {
    const carrito = aCarrito({
      id: 'carrito-1',
      usuarioId: 'usuario-1',
      creadoEn: '2026-09-02T12:00:00Z',
      lineas: [
        { id: 'linea-1', varianteId: 'variante-1', cantidad: 2 },
        { id: 'linea-2', varianteId: 'variante-2', cantidad: 1, enlaceCompra: 'token-1' },
      ],
    });

    expect(carrito).toEqual({
      id: 'carrito-1',
      usuarioId: 'usuario-1',
      creadoEn: '2026-09-02T12:00:00Z',
      lineas: [
        { id: 'linea-1', varianteId: 'variante-1', cantidad: 2, enlaceCompra: null },
        { id: 'linea-2', varianteId: 'variante-2', cantidad: 1, enlaceCompra: 'token-1' },
      ],
    });
  });

  it('campos ausentes se rellenan con valores por defecto, nunca undefined', () => {
    const carrito = aCarrito({});

    expect(carrito).toEqual({ id: '', usuarioId: null, creadoEn: '', lineas: [] });
  });
});

describe('aCarritoCotizado', () => {
  it('lleva el subtotal publicado y el de contado, en el carrito y en cada línea', () => {
    const cotizado = aCarritoCotizado({
      subtotal: { valor: 239_000, moneda: 'COP' },
      subtotalContado: { valor: 230_000, moneda: 'COP' },
      lineas: [
        {
          lineaId: 'linea-1',
          varianteId: 'variante-1',
          cantidad: 1,
          disponible: true,
          precioUnitario: { valor: 239_000, moneda: 'COP' },
          subtotal: { valor: 239_000, moneda: 'COP' },
          precioContado: { valor: 230_000, moneda: 'COP' },
          subtotalContado: { valor: 230_000, moneda: 'COP' },
        },
      ],
    });

    expect(cotizado).toEqual({
      subtotal: 239_000,
      subtotalContado: 230_000,
      lineas: [
        {
          lineaId: 'linea-1',
          varianteId: 'variante-1',
          cantidad: 1,
          precioUnitario: 239_000,
          subtotal: 239_000,
          precioContado: 230_000,
          subtotalContado: 230_000,
        },
      ],
    });
  });

  it('una línea que ya no se vende no tiene precio de ningún tipo, ni cero', () => {
    const cotizado = aCarritoCotizado({
      subtotal: { valor: 0, moneda: 'COP' },
      subtotalContado: { valor: 0, moneda: 'COP' },
      lineas: [{ lineaId: 'linea-1', varianteId: 'variante-1', cantidad: 1, disponible: false }],
    });

    expect(cotizado.lineas[0]).toMatchObject({
      precioUnitario: null,
      subtotal: null,
      precioContado: null,
      subtotalContado: null,
    });
  });
});
