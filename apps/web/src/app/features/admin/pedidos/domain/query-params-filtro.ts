import { Params } from '@angular/router';
import { EstadoPedido, FiltroPedidosAdmin } from './pedido-admin.model';

const TAMANO_PAGINA = 20;

const ESTADOS_VALIDOS: readonly EstadoPedido[] = [
  'PAGO_PENDIENTE',
  'PAGADO',
  'PAGO_FALLIDO',
  'CONFIRMADO_CONTRAENTREGA',
  'EN_PREPARACION',
  'DESPACHADO',
  'ENTREGADO',
  'RECHAZADO_EN_ENTREGA',
  'DEVUELTO',
  'RECAUDO_PENDIENTE',
  'RECAUDO_CONCILIADO',
];

function esEstadoValido(valor: unknown): valor is EstadoPedido {
  return typeof valor === 'string' && (ESTADOS_VALIDOS as readonly string[]).includes(valor);
}

/** Params del router -> FiltroPedidosAdmin. `pagina` en la URL es 1-based (más legible para un
 * humano); el filtro y la API son 0-based (`docs/03-api.md`). */
export function filtroDesdeQueryParams(params: Params): FiltroPedidosAdmin {
  const paginaUrl = Number(params['pagina']);
  return {
    pagina: Number.isInteger(paginaUrl) && paginaUrl > 0 ? paginaUrl - 1 : 0,
    tamano: TAMANO_PAGINA,
    estado: esEstadoValido(params['estado']) ? params['estado'] : null,
  };
}

/** FiltroPedidosAdmin -> Params. La página 1 (la 0-based) y "sin filtro de estado" no aparecen
 * en la URL. */
export function queryParamsDesdeFiltro(filtro: FiltroPedidosAdmin): Params {
  const params: Params = {};
  if (filtro.pagina > 0) {
    params['pagina'] = filtro.pagina + 1;
  }
  if (filtro.estado) {
    params['estado'] = filtro.estado;
  }
  return params;
}
