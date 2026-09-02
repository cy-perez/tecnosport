import { filtroDesdeQueryParams, queryParamsDesdeFiltro } from './query-params-filtro';

describe('filtroDesdeQueryParams', () => {
  it('convierte params de texto a un FiltroProductos tipado', () => {
    const filtro = filtroDesdeQueryParams({
      categoria: 'bolsos',
      marca: '01a0-abc',
      linea: 'BOLSOS',
      precioMin: '50000',
      precioMax: '200000',
      texto: 'morral',
      orden: 'PRECIO_ASC',
    });

    expect(filtro).toEqual({
      categoria: 'bolsos',
      marca: '01a0-abc',
      linea: 'BOLSOS',
      precioMin: 50000,
      precioMax: 200000,
      texto: 'morral',
      orden: 'PRECIO_ASC',
    });
  });

  it('omite campos ausentes en vez de traerlos como cadena vacía', () => {
    expect(filtroDesdeQueryParams({})).toEqual({
      categoria: undefined,
      marca: undefined,
      linea: undefined,
      precioMin: undefined,
      precioMax: undefined,
      texto: undefined,
      orden: undefined,
    });
  });

  it('ignora un precio no numérico y un orden desconocido', () => {
    const filtro = filtroDesdeQueryParams({ precioMin: 'no-es-numero', orden: 'INVENTADO' });

    expect(filtro.precioMin).toBeUndefined();
    expect(filtro.orden).toBeUndefined();
  });
});

describe('queryParamsDesdeFiltro', () => {
  it('solo incluye los campos presentes del filtro', () => {
    const params = queryParamsDesdeFiltro({ linea: 'CELULARES', precioMax: 500000 });

    expect(params).toEqual({ linea: 'CELULARES', precioMax: 500000 });
  });

  it('un filtro vacío produce un objeto de params vacío', () => {
    expect(queryParamsDesdeFiltro({})).toEqual({});
  });

  it('es el inverso de filtroDesdeQueryParams para un filtro completo', () => {
    const original = {
      categoria: 'bolsos',
      marca: '01a0-abc',
      linea: 'BOLSOS',
      precioMin: 50000,
      precioMax: 200000,
      texto: 'morral',
      orden: 'PRECIO_ASC' as const,
    };

    expect(filtroDesdeQueryParams(queryParamsDesdeFiltro(original))).toEqual(original);
  });
});
