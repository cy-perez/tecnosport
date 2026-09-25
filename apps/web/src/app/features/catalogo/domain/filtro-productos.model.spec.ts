import { FILTRO_NOVEDADES, FiltroProductos, hayFiltrosActivos } from './filtro-productos.model';

describe('hayFiltrosActivos', () => {
  it('un filtro vacío es el catálogo completo', () => {
    expect(hayFiltrosActivos({})).toBe(false);
  });

  it.each<[string, FiltroProductos]>([
    ['categoría', { categoria: 'bolsos' }],
    ['marca', { marca: 'm1' }],
    ['línea', { linea: 'BOLSOS' }],
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

  /**
   * Quedaba una prueba de "un precio mínimo de cero es un filtro": el rango de precio salió del
   * modelo, y con él esa. Lo que decía sigue valiendo para cualquier campo que llegue vacío pero
   * presente, y lo cubre el caso de texto.
   */
  it('una cadena vacía presente cuenta como filtro, porque presente es presente', () => {
    expect(hayFiltrosActivos({ texto: '' })).toBe(true);
  });
});
