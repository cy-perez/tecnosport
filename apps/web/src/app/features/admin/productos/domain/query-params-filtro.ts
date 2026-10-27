import { FiltroProductosAdmin } from './producto-admin.model';

/**
 * Los query params tal como los entrega el router, declarados aquí y no importando `Params` de
 * `@angular/router`: domain va sin Angular. Un parámetro repetido en la URL llega como lista.
 */
export type ParametrosDeConsulta = Readonly<Record<string, string | readonly string[] | undefined>>;

/** Lo que vuelve a la URL. Un campo ausente no aparece. */
export type ParametrosDeUrl = Record<string, string | number>;

const TAMANO_PAGINA = 20;

/** Params del router -> FiltroProductosAdmin. `pagina` en la URL es 1-based (más legible para un
 * humano); el filtro y la API son 0-based (`docs/03-api.md`), mismo criterio que `admin/pedidos`. */
export function filtroDesdeQueryParams(params: ParametrosDeConsulta): FiltroProductosAdmin {
  const paginaUrl = Number(params['pagina']);
  return {
    pagina: Number.isInteger(paginaUrl) && paginaUrl > 0 ? paginaUrl - 1 : 0,
    tamano: TAMANO_PAGINA,
  };
}

/** FiltroProductosAdmin -> Params. La página 1 (la 0-based) no aparece en la URL. */
export function queryParamsDesdeFiltro(filtro: FiltroProductosAdmin): ParametrosDeUrl {
  const params: ParametrosDeUrl = {};
  if (filtro.pagina > 0) {
    params['pagina'] = filtro.pagina + 1;
  }
  return params;
}
