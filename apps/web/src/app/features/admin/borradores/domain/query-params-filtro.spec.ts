import {
  filtroBorradoresDesdeQueryParams,
  queryParamsDesdeFiltroBorradores,
} from './query-params-filtro';

describe('query-params-filtro de borradores', () => {
  it('la página de la URL es 1-based y el filtro 0-based, ida y vuelta', () => {
    const filtro = filtroBorradoresDesdeQueryParams({ pagina: '3', estado: 'EN_REVISION' });

    expect(filtro).toEqual({ estado: 'EN_REVISION', proveedorId: '', pagina: 2 });
    expect(queryParamsDesdeFiltroBorradores(filtro)).toEqual({ estado: 'EN_REVISION', pagina: 3 });
  });

  /** Una URL escrita a mano con un estado inventado no manda ese estado al servidor. */
  it('un estado que no existe se ignora', () => {
    expect(filtroBorradoresDesdeQueryParams({ estado: 'CUALQUIERA' }).estado).toBe('');
  });

  it('lo que vale por omisión no aparece en la URL', () => {
    expect(queryParamsDesdeFiltroBorradores({ estado: '', proveedorId: '', pagina: 0 })).toEqual(
      {},
    );
  });
});
