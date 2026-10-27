import { FiltroProductos, OrdenProductos } from './filtro-productos.model';

/**
 * Los query params tal como los entrega el router, declarados aquí y no importando `Params` de
 * `@angular/router`: domain va sin Angular. Un parámetro repetido en la URL llega como lista.
 */
export type ParametrosDeConsulta = Readonly<Record<string, string | readonly string[] | undefined>>;

/** Lo que vuelve a la URL. Un campo ausente no aparece. */
export type ParametrosDeUrl = Record<string, string>;

/** Un parámetro de texto: solo si llegó una vez y no vacío. */
function textoDe(valor: string | readonly string[] | undefined): string | undefined {
  return typeof valor === 'string' && valor !== '' ? valor : undefined;
}

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
export function filtroDesdeQueryParams(params: ParametrosDeConsulta): FiltroProductos {
  return {
    categoria: textoDe(params['categoria']),
    marca: textoDe(params['marca']),
    linea: textoDe(params['linea']),
    texto: textoDe(params['texto']),
    orden: esOrdenValido(params['orden']) ? params['orden'] : undefined,
  };
}

/** FiltroProductos -> Params. Un campo ausente no aparece en la URL. */
export function queryParamsDesdeFiltro(filtro: FiltroProductos): ParametrosDeUrl {
  const params: ParametrosDeUrl = {};
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
