import { Params } from '@angular/router';
import { FiltroLotes } from './ingesta.model';

/**
 * Params del router -> filtro. `pagina` en la URL es 1-based, el filtro y la API 0-based: mismo
 * criterio que `admin/productos` y `admin/pedidos` (docs/03-api.md).
 */
export function filtroLotesDesdeQueryParams(params: Params): FiltroLotes {
  const paginaUrl = Number(params['pagina']);
  return {
    proveedorId: typeof params['proveedor'] === 'string' ? params['proveedor'] : '',
    pagina: Number.isInteger(paginaUrl) && paginaUrl > 0 ? paginaUrl - 1 : 0,
  };
}

/** Filtro -> Params. Lo que vale por omisión no aparece en la URL. */
export function queryParamsDesdeFiltroLotes(filtro: FiltroLotes): Params {
  const params: Params = {};
  if (filtro.proveedorId) {
    params['proveedor'] = filtro.proveedorId;
  }
  if (filtro.pagina > 0) {
    params['pagina'] = filtro.pagina + 1;
  }
  return params;
}
