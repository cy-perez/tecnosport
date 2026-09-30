import { Params } from '@angular/router';
import { ESTADOS_BORRADOR, EstadoBorrador, FiltroBorradores } from './borrador.model';

/**
 * Params del router -> filtro. Un estado que no exista se ignora en vez de mandarse: el servidor
 * respondería 422 y la lista quedaría en error por una URL escrita a mano.
 */
export function filtroBorradoresDesdeQueryParams(params: Params): FiltroBorradores {
  const paginaUrl = Number(params['pagina']);
  const estado = params['estado'];
  return {
    estado: ESTADOS_BORRADOR.includes(estado as EstadoBorrador) ? (estado as EstadoBorrador) : '',
    proveedorId: typeof params['proveedor'] === 'string' ? params['proveedor'] : '',
    pagina: Number.isInteger(paginaUrl) && paginaUrl > 0 ? paginaUrl - 1 : 0,
  };
}

export function queryParamsDesdeFiltroBorradores(filtro: FiltroBorradores): Params {
  const params: Params = {};
  if (filtro.estado) {
    params['estado'] = filtro.estado;
  }
  if (filtro.proveedorId) {
    params['proveedor'] = filtro.proveedorId;
  }
  if (filtro.pagina > 0) {
    params['pagina'] = filtro.pagina + 1;
  }
  return params;
}
