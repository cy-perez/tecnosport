import { aCarrito } from './mapeador-carrito';

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
