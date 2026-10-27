import { aPeticionDeRecalculo, aPreciosPorRecalcular } from './mapeador-recalculo-precios';

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

  /** La fila tal como se vio, para que el servidor solo grabe lo que sigue igual. */
  it('la petición lleva cada fila revisada con sus tres precios', () => {
    expect(
      aPeticionDeRecalculo([
        {
          varianteId: 'v1',
          sku: 'TS-BOLSO-NEGRO',
          nombreProducto: 'Bolso tote',
          precioContado: 230000,
          publicadoActual: 239000,
          publicadoNuevo: 240000,
        },
      ]),
    ).toEqual({
      variantes: [
        {
          varianteId: 'v1',
          precioContado: 230000,
          publicadoActual: 239000,
          publicadoNuevo: 240000,
        },
      ],
    });
  });
});
