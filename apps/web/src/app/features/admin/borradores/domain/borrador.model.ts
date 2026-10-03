/**
 * Un borrador nace en revisión y sale aprobado o rechazado. `RENOVACION_APLICADA` es el que no
 * hizo falta revisar: la publicación era un producto que ya existía y solo se le renovó la fecha.
 */
export type EstadoBorrador = 'EN_REVISION' | 'APROBADO' | 'RECHAZADO' | 'RENOVACION_APLICADA';

export const ESTADOS_BORRADOR: readonly EstadoBorrador[] = [
  'EN_REVISION',
  'APROBADO',
  'RECHAZADO',
  'RENOVACION_APLICADA',
];

export type TipoProductoProveedor =
  | 'BOLSO'
  | 'MORRAL'
  | 'CANGURO'
  | 'CONJUNTO_PANTALON'
  | 'CONJUNTO_SHORT'
  | 'ENTERIZO'
  | 'POLO'
  | 'CAMISETA'
  | 'BUSO'
  | 'CHAQUETA'
  | 'PANTALON'
  | 'SHORT'
  | 'VESTIDO'
  | 'BLUSA'
  | 'BODY'
  | 'OTRO';

export const TIPOS_PRODUCTO_PROVEEDOR: readonly TipoProductoProveedor[] = [
  'BOLSO',
  'MORRAL',
  'CANGURO',
  'CONJUNTO_PANTALON',
  'CONJUNTO_SHORT',
  'ENTERIZO',
  'POLO',
  'CAMISETA',
  'BUSO',
  'CHAQUETA',
  'PANTALON',
  'SHORT',
  'VESTIDO',
  'BLUSA',
  'BODY',
  'OTRO',
];

/**
 * La categoría que el formulario de aprobación propone según el tipo, por slug: los ids cambian de
 * un ambiente a otro y el slug no. Solo los tipos que caen siempre en la misma hoja; los demás
 * dependen del género o de la línea y los elige quien aprueba (decidido el 2 de octubre de 2026).
 */
export const CATEGORIA_SUGERIDA_POR_TIPO: Readonly<Partial<Record<TipoProductoProveedor, string>>> =
  {
    BLUSA: 'ropa-dama-blusas',
    BODY: 'ropa-dama-bodis',
  };

/**
 * Lo que la extracción no pudo resolver sola y alguien tiene que mirar antes de aprobar. Los
 * mismos nombres que el enum del backend: la pantalla los traduce por clave.
 */
export type AlertaBorrador =
  | 'PRECIO_INCONSISTENTE'
  | 'SIN_PRECIO'
  | 'TITULO_VACIO'
  | 'TIPO_DESCONOCIDO'
  | 'CONFIANZA_BAJA'
  | 'SIN_FOTOS'
  | 'PRECIO_CAMBIO'
  | 'FOTOS_COMPARTIDAS';

export type TipoDeTalla = 'UNICA' | 'LISTA' | 'DESCONOCIDA';

/**
 * Talla única con «sirve hasta» (bolsos y enterizos: «sirve hasta L»), o una lista (`S, M, L`),
 * o nada porque el proveedor no lo dijo.
 */
export interface Tallas {
  readonly tipo: TipoDeTalla;
  readonly sirveHasta: string | null;
  readonly valores: readonly string[];
}

export interface Borrador {
  readonly id: string;
  readonly proveedorId: string;
  readonly publicacionId: string;
  readonly estado: EstadoBorrador;
  readonly titulo: string;
  readonly tipo: TipoProductoProveedor;
  readonly linea: string | null;
  /** En pesos, entero; `null` si el proveedor no puso precio (y hay alerta). */
  readonly precioProveedor: number | null;
  readonly precioVentaSugerido: number | null;
  readonly tallas: Tallas;
  readonly cantidadTonos: number;
  readonly tonosNombrados: readonly string[];
  readonly material: string | null;
  readonly caracteristicas: readonly string[];
  readonly alertas: readonly AlertaBorrador[];
  readonly motivoRechazo: string | null;
  /** El producto que creó al aprobarse, o el que renovó. */
  readonly productoId: string | null;
  readonly creadoEn: string;
}

export interface FotoBorrador {
  readonly mensajeId: string;
  /** URL de lectura firmada: caduca, y por eso no se guarda. */
  readonly url: string;
  readonly pieDeFoto: string | null;
}

export interface BorradorDetalle {
  readonly borrador: Borrador;
  readonly fotos: readonly FotoBorrador[];
  /** Los textos de la publicación tal como los escribió el proveedor, en orden. */
  readonly textos: readonly string[];
}

export interface BorradoresPaginados {
  readonly items: readonly Borrador[];
  readonly pagina: number;
  readonly totalPaginas: number;
  readonly totalBorradores: number;
}

export interface FiltroBorradores {
  /** Vacío = todos los estados. */
  readonly estado: EstadoBorrador | '';
  /** Vacío = todos los proveedores. */
  readonly proveedorId: string;
  readonly pagina: number;
}

/** Todo opcional: se manda lo que cambió. */
export interface EditarBorrador {
  readonly titulo?: string;
  readonly tipo?: TipoProductoProveedor;
  readonly precioVentaSugerido?: number;
  readonly tallas?: Tallas;
  readonly cantidadTonos?: number;
  readonly tonosNombrados?: readonly string[];
  readonly material?: string;
  readonly caracteristicas?: readonly string[];
}

export interface FotoAprobada {
  readonly mensajeId: string;
  /** El tono que muestra la foto, si el producto viene en varios. Sin tono = vale para todos. */
  readonly tono: string | null;
  readonly colorHex: string | null;
}

export interface AprobarBorrador {
  readonly titulo?: string;
  readonly descripcion?: string;
  readonly marcaId: string;
  readonly categoriaId: string;
  readonly precioVenta: number;
  readonly tallas?: Tallas;
  readonly fotos: readonly FotoAprobada[];
  readonly altEs: string;
  readonly altEn: string;
  /** Unidades por variante que entran al inventario al aprobar. Cero es válido: agotado desde ya. */
  readonly existenciaInicial: number;
}

/**
 * Las fotos que caben en un producto: la principal más las ocho de galería que admite el catálogo
 * (`Producto.TOPE_DE_GALERIA` en la API). Una publicación de ropa trae doce o catorce, así que
 * quien aprueba elige cuáles entran.
 */
export const MAXIMO_FOTOS_POR_PRODUCTO = 9;

export function borradorEditable(borrador: Borrador): boolean {
  return borrador.estado === 'EN_REVISION';
}

/**
 * Borrar se lleva el borrador con sus mensajes y sus fotos. Un aprobado o una renovación no: son la
 * huella con que la ingesta reconoce el producto la próxima vez, y la API responde 409.
 */
export function borradorBorrable(borrador: Borrador): boolean {
  return borrador.estado === 'EN_REVISION' || borrador.estado === 'RECHAZADO';
}
