export type OrdenProductos = 'RELEVANCIA' | 'PRECIO_ASC' | 'PRECIO_DESC' | 'MAS_RECIENTES';

/** Todo campo ausente significa "sin ese filtro" — mismo criterio que el backend. */
export interface FiltroProductos {
  readonly categoria?: string;
  readonly marca?: string;
  readonly linea?: string;
  readonly precioMin?: number;
  readonly precioMax?: number;
  readonly texto?: string;
  readonly orden?: OrdenProductos;
  readonly tamano?: number;
}

/** Las tres líneas de negocio de docs/00-producto.md. */
export const LINEAS = ['ROPA_Y_CALZADO', 'BOLSOS', 'CELULARES'] as const;

export type Linea = (typeof LINEAS)[number];

/**
 * Lo más nuevo del catálogo, para la franja de la portada. `tamano` chico a
 * propósito: es una franja, no una rejilla paginada.
 */
export const FILTRO_NOVEDADES: FiltroProductos = { orden: 'MAS_RECIENTES', tamano: 4 };
