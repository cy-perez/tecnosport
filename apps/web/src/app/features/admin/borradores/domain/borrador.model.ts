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
  | 'CONJUNTO_FALDA'
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
  | 'FALDA'
  | 'FALDA_SHORT'
  | 'SUDADERA'
  | 'BLAZER'
  | 'CHALECO'
  | 'OTRO';

export const TIPOS_PRODUCTO_PROVEEDOR: readonly TipoProductoProveedor[] = [
  'BOLSO',
  'MORRAL',
  'CANGURO',
  'CONJUNTO_PANTALON',
  'CONJUNTO_SHORT',
  'CONJUNTO_FALDA',
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
  'FALDA',
  'FALDA_SHORT',
  'SUDADERA',
  'BLAZER',
  'CHALECO',
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
    FALDA: 'ropa-dama-faldas',
    // El polo, el blazer y el chaleco caen siempre en la misma hoja (11 de octubre de 2026, V108).
    POLO: 'ropa-caballero-polos',
    BLAZER: 'ropa-dama-blazers',
    CHALECO: 'ropa-dama-chalecos',
    // La falda con short tiene su hoja, y la falda con su blusa es un conjunto de dama: el set que
    // arma la ingesta de La Riverah (12 de octubre de 2026). El set de camiseta o polo con gorra
    // conserva el tipo de la prenda, y con él la categoría.
    FALDA_SHORT: 'ropa-dama-falda-short',
    CONJUNTO_FALDA: 'ropa-dama-conjuntos',
  };

/**
 * Los tipos que arma la ingesta y no lee el extractor: excluir uno no descartaría nada, porque lo
 * que se compara con las exclusiones del proveedor es lo que el anuncio dice, antes de armar el set.
 */
export const TIPOS_QUE_ARMA_LA_INGESTA: readonly TipoProductoProveedor[] = ['CONJUNTO_FALDA'];

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
  | 'REPLICA'
  | 'ACOMPANANTE_SIN_LEER'
  | 'SET_SIN_DESCRIPCION'
  | 'TALLAS_CORRIDAS'
  | 'TALLAS_SIN_CORRER';

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

/** «Talla ML(cocoa)»: las tallas que hay de un tono cuando no son todas las del producto. */
export interface TallasDeTono {
  readonly tono: string;
  readonly tallas: readonly string[];
}

/**
 * Qué es un precio adicional. `PRENDA_SOLA` es el de la prenda sin su acompañante, cuando el
 * borrador es el set; `null` en los guardados antes del 12 de octubre de 2026.
 */
export type ClaseDePrecioAdicional = 'ACOMPANANTE' | 'COMBO' | 'PROMOCION' | 'PRENDA_SOLA';

/** La prenda que acompaña a la del anuncio: la «básica» de La Riverah es la blusa. */
export type PrendaAcompanante = 'BLUSA' | 'GORRA' | 'OTRA';

/**
 * Un precio del anuncio que no es del producto: la gorra que acompaña, el combo, la promoción por
 * cantidad, y en un set la prenda sola. Solo se muestra.
 */
export interface PrecioAdicional {
  readonly concepto: string;
  /** En pesos, entero. */
  readonly precio: number;
  readonly clase: ClaseDePrecioAdicional | null;
  readonly prenda: PrendaAcompanante | null;
}

/** De dónde salió el costo de un set: el precio que anunció el proveedor, o la suma de las prendas. */
export type OrigenDelCostoDeSet = 'ANUNCIADO' | 'SUMA_DE_PRENDAS';

/**
 * El borrador es la prenda vendida con su acompañante: la falda con la blusa, la camiseta o el polo
 * con la gorra de la foto (La Riverah, 12 de octubre de 2026).
 */
export interface SetDelBorrador {
  readonly acompanante: Exclude<PrendaAcompanante, 'OTRA'>;
  readonly origenDelCosto: OrigenDelCostoDeSet;
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
  /**
   * Las tallas de cada tono cuando el proveedor no las tiene todas en todos. Al aprobar, un tono
   * solo se crea en las suyas. Vacía casi siempre.
   */
  readonly tallasPorTono: readonly TallasDeTono[];
  readonly preciosAdicionales: readonly PrecioAdicional[];
  /** Cómo se armó el set; `null` si el borrador es la prenda sola. */
  readonly set: SetDelBorrador | null;
  /** Cuántas tallas más arriba las nombra el proveedor (ADR-0085); 0 si igual. */
  readonly ajusteDeTalla: number;
  /** Cómo se le pide al proveedor cada talla, con lo guardado; vacía sin ajuste. */
  readonly tallasPedidas: readonly TallaPedida[];
}

/** La talla que se vende y la que se le pide al proveedor; esta, nula si no se puede contar. */
export interface TallaPedida {
  readonly talla: string;
  readonly delProveedor: string | null;
}

/** Hasta cuántas tallas puede nombrar corridas el proveedor: más es un error de lectura. */
export const AJUSTE_DE_TALLA_MAXIMO = 4;

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
  /**
   * El color que la lectura de fotos vio en ella, si mostraba uno solo de lo que se vende. El
   * formulario de aprobación lo propone; nulo si no hay sugerencia.
   */
  readonly tonoSugerido: string | null;
  /**
   * Los colores que la lectura vio en ella cuando retrata varios —la blusa en negro, cocoa y verde
   * en una sola foto—, como los nombró el lector. Son los que el panel propone como prendas sin
   * foto propia; vacía si muestra uno solo o ninguno.
   */
  readonly tonosVistos: readonly string[];
  /**
   * Las del rótulo impreso en ella —«Tallas disponibles en la foto!»—, ya como se venden en este
   * borrador (ADR-0085): las que el panel propone a su prenda. Vacía si no trae.
   */
  readonly tallas: readonly string[];
}

/**
 * Lo que se admite al subir una foto a un borrador: lo que la API sabe abrir para medirla al
 * aprobar. Un WebP pasaría la subida y fallaría con el formulario de aprobación ya lleno.
 */
export const TIPOS_DE_FOTO_ADMITIDOS: readonly string[] = ['image/jpeg', 'image/png'];

export function fotoAdmitida(archivo: File): boolean {
  return TIPOS_DE_FOTO_ADMITIDOS.includes(archivo.type);
}

/** Otro borrador en revisión de la misma publicación: a donde se puede mover una foto. */
export interface HermanoBorrador {
  readonly id: string;
  /** Vacío si la extracción no le dio título. */
  readonly titulo: string;
  /**
   * La lectura de fotos vio en él el mismo diseño que en este: posiblemente es el mismo producto en
   * otro color, que el álbum dio aparte por traer otro SKU. El panel lo sugiere para unirlos.
   */
  readonly mismoDiseno: boolean;
}

/**
 * Una prenda de un álbum de La Riverah (13 de octubre de 2026): un diseño con el SKU de su pie, sus
 * fotos y sus tallas. La aprobación deja el SKU en sus variantes, para que el proveedor la pueda
 * agotar sin agotar las demás.
 */
export interface PrendaDelAlbum {
  readonly referencia: string;
  /** Las de su pie; vacía si no las dice. */
  readonly tallas: readonly string[];
  /** Sus fotos, por `mensajeId` y en orden. */
  readonly fotos: readonly string[];
}

export interface BorradorDetalle {
  readonly borrador: Borrador;
  readonly fotos: readonly FotoBorrador[];
  /** Los textos de la publicación tal como los escribió el proveedor, en orden. */
  readonly textos: readonly string[];
  /** El otro producto del conjunto, los otros diseños del álbum; vacío si no hay. */
  readonly hermanos: readonly HermanoBorrador[];
  /** Si la publicación es un álbum, una prenda por SKU; vacío si no. */
  readonly prendasDelAlbum: readonly PrendaDelAlbum[];
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
  readonly ajusteDeTalla?: number;
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
   * Las prendas del producto, con foto propia o sin ella (`prendas.ts`). Vacía = salen de las
   * fotos, como antes del 12 de octubre de 2026.
   */
  readonly prendas: readonly PrendaAprobada[];
  /** Si la principal que vale para todos los tonos entra también en la ficha (ADR-0082). */
  readonly principalEnLaFicha: boolean;
  /** El escrito en el panel, se haya guardado o no; ausente = el del borrador (ADR-0085). */
  readonly ajusteDeTalla?: number;
}

/** Una prenda: una variante de color. La que ninguna foto cita se ve en la foto de todos. */
export interface PrendaAprobada {
  readonly numero: number;
  readonly tono: string;
  readonly colorHex: string | null;
  /** En cuáles tallas del producto viene; nula = en todas. */
  readonly tallas: readonly string[] | null;
  /**
   * La foto en que se ve, si no tiene foto propia y la retrata junto con otros tonos (ADR-0082).
   * Nula = en la de todos.
   */
  readonly seVeEn: string | null;
  /** El SKU del proveedor, si es la prenda de un álbum; nulo si no. */
  readonly referencia: string | null;
}

/**
 * Las fotos que caben en un producto: la principal más las nueve de galería que admite el catálogo
 * (`Producto.TOPE_DE_GALERIA` en la API; subió de ocho a nueve el 13 de octubre de 2026). Una
 * publicación de ropa trae doce o catorce, así que quien aprueba elige cuáles entran.
 */
export const MAXIMO_FOTOS_POR_PRODUCTO = 10;

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

/**
 * Una tanda del borrado en bloque de los borradores sin aprobar. El servidor borra por tandas para
 * no rozar su límite de tiempo, y dice cuántos quedan para que el panel pida la siguiente.
 */
export interface TandaDeBorradoresEliminados {
  readonly eliminados: number;
  readonly quedan: number;
}
