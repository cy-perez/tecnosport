import { aPreciosPorRecalcular } from './mapeador-recalculo-precios';

describe('mapeador del recálculo de precios', () => {
  /** Los tres precios por separado: el de contado no cambia, el publicado pasa de uno a otro. */
  it('lleva cada variante con su contado y sus dos publicados', () => {
    expect(
      aPreciosPorRecalcular({
        variantes: [
          {
            varianteId: 'v1',
            sku: 'TS-BOLSO-NEGRO',
            nombreProducto: 'Bolso tote',
            precioContado: 230000,
            publicadoActual: 239000,
            publicadoNuevo: 240000,
          },
        ],
      }),
    ).toEqual([
      {
        varianteId: 'v1',
        sku: 'TS-BOLSO-NEGRO',
        nombreProducto: 'Bolso tote',
        precioContado: 230000,
        publicadoActual: 239000,
        publicadoNuevo: 240000,
      },
    ]);
  });

  it('sin variantes desactualizadas devuelve una lista vacía', () => {
    expect(aPreciosPorRecalcular({ variantes: [] })).toEqual([]);
  });
});
