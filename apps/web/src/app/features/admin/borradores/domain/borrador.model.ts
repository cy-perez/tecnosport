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
  | 'BODI'
  | 'TENIS'
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
  'BODI',
  'TENIS',
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
    BODI: 'ropa-dama-bodis',
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
  | 'FOTOS_COMPARTIDAS'
  | 'REPLICA';

/**
 * Cómo se llama la marca con que se publica una réplica («1.1» o «AAA»): la original solo va en el título,
 * como «Camiseta estilo Puma - BMW» (decidido por el negocio el 3 de octubre de 2026). Se busca por
 * nombre, sin tildes ni mayúsculas, porque el id cambia de un ambiente a otro.
 */
export const MARCA_DE_REPLICAS = 'generica';

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
  /** Lo que la ficha va a decir del producto; la redacta la extracción y es obligatoria al aprobar. */
  readonly descripcion: string | null;
  /** El título en inglés que propone la extracción, para el texto alternativo de las fotos. */
  readonly altEn: string | null;
  readonly alertas: readonly AlertaBorrador[];
  readonly motivoRechazo: string | null;
  /** El producto que creó al aprobarse, o el que renovó. */
  readonly productoId: string | null;
  readonly creadoEn: string;
}

/** De dónde salió la foto: del mensaje del proveedor, o de quien revisa, desde el panel. */
export type OrigenFoto = 'PROVEEDOR' | 'PANEL';

export interface FotoBorrador {
  /**
   * El id de la foto, venga de donde venga: el del mensaje del proveedor o el de la subida. Es el
   * que se manda al aprobar y al eliminarla; se llama así por el contrato de la API.
   */
  readonly mensajeId: string;
  /** URL de lectura firmada: caduca, y por eso no se guarda. */
  readonly url: string;
  readonly pieDeFoto: string | null;
  /** La del panel es solo de este borrador: eliminarla borra el archivo. */
  readonly origen: OrigenFoto;
}

/**
 * Lo que se admite al subir una foto a un borrador: lo que la API sabe abrir para medirla al
 * aprobar. Un WebP pasaría la subida y fallaría con el formulario de aprobación ya lleno.
 */
export const TIPOS_DE_FOTO_ADMITIDOS: readonly string[] = ['image/jpeg', 'image/png'];

export function fotoAdmitida(archivo: File): boolean {
  return TIPOS_DE_FOTO_ADMITIDOS.includes(archivo.type);
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
  readonly descripcion?: string;
  readonly altEn?: string;
}

export interface FotoAprobada {
  readonly mensajeId: string;
  /** El tono que muestra la foto, si el producto viene en varios. Sin tono = vale para todos. */
  readonly tono: string | null;
  readonly colorHex: string | null;
  /**
   * La prenda de la foto (`prendas.ts`): las del mismo número son una sola variante. Nula en la
   * que vale para todas.
   */
  readonly prenda: number | null;
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
  /**
   * Si las fotos sin tono —la principal casi siempre— acompañan a las de cada color en la ficha.
   * Se apaga cuando cada color trae sus propias fotos.
   */
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
  // Un aprobado o una renovación también, cuando su producto ya se borró: no reconoce nada y solo
  // guarda las fotos del proveedor. Con el producto vivo, no (`EliminarBorrador` en la API).
  return (
    borrador.estado === 'EN_REVISION' ||
    borrador.estado === 'RECHAZADO' ||
    borrador.productoId === null
  );
}

/** Un aprobado o una renovación que perdió su producto: el único borrado que no es un descarte. */
export function borradorSinProducto(borrador: Borrador): boolean {
  return (
    (borrador.estado === 'APROBADO' || borrador.estado === 'RENOVACION_APLICADA') &&
    borrador.productoId === null
  );
}
