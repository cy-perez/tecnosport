import { Params } from '@angular/router';
import { FiltroProductosAdmin } from './producto-admin.model';

const TAMANO_PAGINA = 20;

/** Params del router -> FiltroProductosAdmin. `pagina` en la URL es 1-based (más legible para un
 * humano); el filtro y la API son 0-based (`docs/03-api.md`), mismo criterio que `admin/pedidos`. */
export function filtroDesdeQueryParams(params: Params): FiltroProductosAdmin {
  const paginaUrl = Number(params['pagina']);
  return {
    pagina: Number.isInteger(paginaUrl) && paginaUrl > 0 ? paginaUrl - 1 : 0,
    tamano: TAMANO_PAGINA,
  };
}

/** FiltroProductosAdmin -> Params. La página 1 (la 0-based) no aparece en la URL. */
export function queryParamsDesdeFiltro(filtro: FiltroProductosAdmin): Params {
  const params: Params = {};
  if (filtro.pagina > 0) {
    params['pagina'] = filtro.pagina + 1;
  }
  return params;
}
