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
