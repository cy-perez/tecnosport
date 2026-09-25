import { Params } from '@angular/router';
import { FiltroProductos, OrdenProductos } from './filtro-productos.model';

const ORDENES_VALIDOS: readonly OrdenProductos[] = [
  'RELEVANCIA',
  'PRECIO_ASC',
  'PRECIO_DESC',
  'MAS_RECIENTES',
];

function esOrdenValido(valor: unknown): valor is OrdenProductos {
  return typeof valor === 'string' && (ORDENES_VALIDOS as readonly string[]).includes(valor);
}

/**
 * Params del router -> FiltroProductos. Comparten esta función el resolver de
 * la ruta y la página, para no parsear los query params dos veces con reglas
 * distintas — si difieren, vuelve el bug de skeletons en SSR con filtros.
 */
export function filtroDesdeQueryParams(params: Params): FiltroProductos {
  return {
    categoria: params['categoria'] || undefined,
    marca: params['marca'] || undefined,
    linea: params['linea'] || undefined,
    texto: params['texto'] || undefined,
    orden: esOrdenValido(params['orden']) ? params['orden'] : undefined,
  };
}

/** FiltroProductos -> Params. Un campo ausente no aparece en la URL. */
export function queryParamsDesdeFiltro(filtro: FiltroProductos): Params {
  const params: Params = {};
  if (filtro.categoria) {
    params['categoria'] = filtro.categoria;
  }
  if (filtro.marca) {
    params['marca'] = filtro.marca;
  }
  if (filtro.linea) {
    params['linea'] = filtro.linea;
  }
  if (filtro.texto) {
    params['texto'] = filtro.texto;
  }
  if (filtro.orden) {
    params['orden'] = filtro.orden;
  }
  return params;
}
