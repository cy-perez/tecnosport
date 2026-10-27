import { FiltroLotes } from './ingesta.model';

/**
 * Los query params tal como los entrega el router, declarados aquí y no importando `Params` de
 * `@angular/router`: domain va sin Angular. Un parámetro repetido en la URL llega como lista.
 */
export type ParametrosDeConsulta = Readonly<Record<string, string | readonly string[] | undefined>>;

/** Lo que vuelve a la URL. Un campo ausente no aparece. */
export type ParametrosDeUrl = Record<string, string | number>;

/**
 * Params del router -> filtro. `pagina` en la URL es 1-based, el filtro y la API 0-based: mismo
 * criterio que `admin/productos` y `admin/pedidos` (docs/03-api.md).
 */
export function filtroLotesDesdeQueryParams(params: ParametrosDeConsulta): FiltroLotes {
  const paginaUrl = Number(params['pagina']);
  return {
    proveedorId: typeof params['proveedor'] === 'string' ? params['proveedor'] : '',
    pagina: Number.isInteger(paginaUrl) && paginaUrl > 0 ? paginaUrl - 1 : 0,
  };
}

/** Filtro -> Params. Lo que vale por omisión no aparece en la URL. */
export function queryParamsDesdeFiltroLotes(filtro: FiltroLotes): ParametrosDeUrl {
  const params: ParametrosDeUrl = {};
  if (filtro.proveedorId) {
    params['proveedor'] = filtro.proveedorId;
  }
  if (filtro.pagina > 0) {
    params['pagina'] = filtro.pagina + 1;
  }
  return params;
}
