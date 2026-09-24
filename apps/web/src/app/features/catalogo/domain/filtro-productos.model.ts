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

/**
 * El orden que el backend aplica cuando la URL no trae `orden`
 * (`ProductoControlador`: `@RequestParam(defaultValue = "RELEVANCIA")`). No es
 * una preferencia del frontend: es el dato que hace falta para que el control
 * de "Ordenar por" muestre el orden que de verdad está aplicado, en vez de
 * quedarse en blanco por no corresponder a ninguna opción.
 */
export const ORDEN_POR_DEFECTO: OrdenProductos = 'RELEVANCIA';

/**
 * Las cuatro líneas de negocio de docs/00-producto.md, **en el orden en que el menú las pinta**.
 * No es alfabético y no debe serlo: tecnología va primera porque es la línea con más rotación, y
 * ropa y calzado van juntas porque quien mira una mira la otra.
 *
 * Fueron tres hasta el 24 de septiembre de 2026, cuando `ROPA_Y_CALZADO` se partió en dos. Sigue
 * valiendo la regla de fondo: una línea más es un control más en este filtro, una rama más en el
 * menú y una etiqueta más en la guía de envío; una categoría más es una fila que se crea desde el
 * panel. Lo tecnológico se amplió a ocho categorías sin tocar esta lista, y ropa llegó a trece sin
 * tocarla tampoco.
 */
export const LINEAS = ['TECNOLOGIA', 'ROPA', 'CALZADO', 'BOLSOS'] as const;

export type Linea = (typeof LINEAS)[number];

/**
 * La clave de i18n del nombre de una línea.
 *
 * Vive en el dominio y no en el componente del filtro porque la leen cinco sitios —el filtro, la
 * portada, el menú lateral, la pantalla de categorías del panel y los desplegables de producto— y
 * la clave es la misma para todos. Está en el diccionario **raíz** y no en el scope de catálogo:
 * tres de esos cinco no cargan ese scope, y una clave que no está cargada se pinta cruda.
 */
export function claveDeLinea(linea: string): string {
  return `lineas.${linea.toLowerCase()}`;
}

/**
 * Lo más nuevo del catálogo, para la franja de la portada. `tamano` chico a
 * propósito: es una franja, no una rejilla paginada.
 */
export const FILTRO_NOVEDADES: FiltroProductos = { orden: 'MAS_RECIENTES', tamano: 4 };

/**
 * ¿El visitante restringió la búsqueda, o está viendo el catálogo completo?
 * Sirve para distinguir "no encontramos nada con estos filtros" de "todavía no
 * hay productos publicados": son dos situaciones distintas y una sola de ellas
 * la puede resolver quien mira.
 *
 * `orden` y `tamano` no cuentan: ordenar no es filtrar, y el tamaño es
 * paginación. Ninguno de los dos cambia el conjunto de resultados.
 */
export function hayFiltrosActivos(filtro: FiltroProductos): boolean {
  return (
    filtro.categoria !== undefined ||
    filtro.marca !== undefined ||
    filtro.linea !== undefined ||
    filtro.precioMin !== undefined ||
    filtro.precioMax !== undefined ||
    filtro.texto !== undefined
  );
}
