import { filtroDesdeQueryParams, queryParamsDesdeFiltro } from './query-params-filtro';

describe('filtroDesdeQueryParams', () => {
  it('convierte params de texto a un FiltroProductos tipado', () => {
    const filtro = filtroDesdeQueryParams({
      categoria: 'bolsos-dama-morrales',
      marca: '01a0-abc',
      linea: 'BOLSOS',
      texto: 'morral',
      orden: 'PRECIO_ASC',
    });

    expect(filtro).toEqual({
      categoria: 'bolsos-dama-morrales',
      marca: '01a0-abc',
      linea: 'BOLSOS',
      texto: 'morral',
      orden: 'PRECIO_ASC',
    });
  });

  it('omite campos ausentes en vez de traerlos como cadena vacía', () => {
    expect(filtroDesdeQueryParams({})).toEqual({
      categoria: undefined,
      marca: undefined,
      linea: undefined,
      texto: undefined,
      orden: undefined,
    });
  });

  it('ignora un orden desconocido', () => {
    expect(filtroDesdeQueryParams({ orden: 'INVENTADO' }).orden).toBeUndefined();
  });

  /**
   * El router entrega `?texto=a&texto=b` como lista. Con `Params` de `@angular/router` (`any`) esa
   * lista entraba al filtro como si fuera texto y viajaba así a la API; con el tipo propio de domain
   * el compilador obliga a decidir, y lo que se decide es ignorarla.
   */
  it('un parámetro repetido en la URL no entra como texto', () => {
    const filtro = filtroDesdeQueryParams({ texto: ['morral', 'bolso'], marca: ['a', 'b'] });

    expect(filtro.texto).toBeUndefined();
    expect(filtro.marca).toBeUndefined();
  });

  /**
   * El rango de precio salió del filtro el 24 de septiembre de 2026 y de aquí también: una URL
   * vieja con `?precioMin=` no vuelve a entrar por esta puerta. Se comprueba porque el fallo
   * natural al quitar un campo es dejar el parseo puesto "por si acaso", y entonces el filtro
   * lleva una clave que ya nadie sabe quitar.
   */
  it('un precio en la URL ya no entra al filtro', () => {
    const filtro = filtroDesdeQueryParams({ precioMin: '50000', precioMax: '200000' });

    expect(filtro).not.toHaveProperty('precioMin');
    expect(filtro).not.toHaveProperty('precioMax');
  });
});

describe('queryParamsDesdeFiltro', () => {
  it('solo incluye los campos presentes del filtro', () => {
    const params = queryParamsDesdeFiltro({ linea: 'TECNOLOGIA', texto: 'morral' });

    expect(params).toEqual({ linea: 'TECNOLOGIA', texto: 'morral' });
  });

  it('un filtro vacío produce un objeto de params vacío', () => {
    expect(queryParamsDesdeFiltro({})).toEqual({});
  });

  it('es el inverso de filtroDesdeQueryParams para un filtro completo', () => {
    const original = {
      categoria: 'bolsos-dama-morrales',
      marca: '01a0-abc',
      linea: 'BOLSOS',
      texto: 'morral',
      orden: 'PRECIO_ASC' as const,
    };

    expect(filtroDesdeQueryParams(queryParamsDesdeFiltro(original))).toEqual(original);
  });
});
