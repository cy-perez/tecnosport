import { FILTRO_NOVEDADES, FiltroProductos, hayFiltrosActivos } from './filtro-productos.model';

describe('hayFiltrosActivos', () => {
  it('un filtro vacío es el catálogo completo', () => {
    expect(hayFiltrosActivos({})).toBe(false);
  });

  it.each<[string, FiltroProductos]>([
    ['categoría', { categoria: 'bolsos' }],
    ['marca', { marca: 'm1' }],
    ['línea', { linea: 'BOLSOS' }],
    ['precio mínimo', { precioMin: 10_000 }],
    ['precio máximo', { precioMax: 90_000 }],
    ['texto', { texto: 'morral' }],
  ])('cualquier filtro por sí solo cuenta: %s', (_nombre, filtro) => {
    expect(hayFiltrosActivos(filtro)).toBe(true);
  });

  it('ordenar no es filtrar', () => {
    expect(hayFiltrosActivos({ orden: 'PRECIO_ASC' })).toBe(false);
  });

  it('el tamaño de página tampoco: la franja de novedades no lleva filtros', () => {
    expect(hayFiltrosActivos(FILTRO_NOVEDADES)).toBe(false);
  });

  it('un precio mínimo de cero es un filtro, no la ausencia de uno', () => {
    expect(hayFiltrosActivos({ precioMin: 0 })).toBe(true);
  });
});
