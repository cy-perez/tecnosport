export type EstadoProducto = 'BORRADOR' | 'PUBLICADO';

export interface MarcaAdmin {
  readonly id: string;
  readonly nombre: string;
}

export interface CategoriaAdmin {
  readonly id: string;
  readonly nombre: string;
  readonly slug: string;
  readonly linea: string;
}

export interface ProductoAdmin {
  readonly id: string;
  readonly nombre: string;
  readonly descripcion: string;
  readonly slug: string;
  readonly estado: EstadoProducto;
  readonly marca: MarcaAdmin;
  readonly categoria: CategoriaAdmin;
  readonly imagenPrincipalUrl: string | null;
  readonly totalVariantes: number;
}

export interface ProductosPaginadosAdmin {
  readonly items: readonly ProductoAdmin[];
  readonly pagina: number;
  readonly totalPaginas: number;
  readonly totalProductos: number;
}

/** Sin filtros todavía (solo el primer caso de uso de Track B: listar). */
export interface FiltroProductosAdmin {
  readonly pagina: number;
  readonly tamano: number;
}

export interface CrearProductoAdmin {
  readonly nombre: string;
  readonly descripcion: string;
  readonly marcaId: string;
  readonly categoriaId: string;
}

export interface EditarProductoAdmin {
  readonly nombre: string;
  readonly descripcion: string;
  readonly marcaId: string;
  readonly categoriaId: string;
}

export interface ValorAtributoAdmin {
  readonly atributoId: string;
  readonly valor: string;
  readonly colorHex: string | null;
}

export interface AgregarVarianteAdmin {
  readonly productoId: string;
  readonly sku: string;
  readonly precio: number;
  readonly tasaIva: number;
  readonly codigoBarras: string | null;
  readonly existenciaInicial: number;
  /** El paquete: sin peso ni dimensiones no hay cotización de envío (adr/0021). */
  /**
   * Las cuatro medidas van juntas o no van (`ADR-0046`). Ausentes, la variante se vende solo con
   * recogida en el punto — el servidor lo traduce a `ARTICULO_SIN_MEDIDAS` al cotizar.
   */
  readonly pesoGramos: number | null;
  readonly largoCm: number | null;
  readonly anchoCm: number | null;
  readonly altoCm: number | null;
  readonly atributos: readonly ValorAtributoAdmin[];
}

export interface ImagenAdmin {
  readonly url: string;
  readonly urlWebp: string;
  readonly ancho: number;
  readonly alto: number;
  readonly altEs: string;
  readonly altEn: string;
}

export interface SubirImagenPrincipalAdmin {
  readonly productoId: string;
  readonly archivo: File;
  readonly ancho: number;
  readonly alto: number;
  readonly altEs: string;
  readonly altEn: string;
}

/**
 * Una variante activa a la que le falta el paquete. Solo se puede entregar con recogida en el
 * punto: cotizar su envío responde 409 nombrándola (`ADR-0046`).
 */
export interface VarianteSinMedir {
  readonly varianteId: string;
  readonly productoId: string;
  readonly nombreProducto: string;
  readonly sku: string;
  readonly estadoProducto: EstadoProducto;
}

/**
 * Los dos conteos vienen del servidor y no se derivan de `items`: el aviso del panel enseña el
 * número sin traerse la lista.
 */
export interface InventarioSinMedir {
  readonly total: number;
  readonly totalEnPublicados: number;
  readonly items: readonly VarianteSinMedir[];
}

/** Las cuatro medidas, obligatorias y en las unidades del dominio: gramos y centímetros enteros. */
export interface MedirVarianteAdmin {
  readonly varianteId: string;
  readonly pesoGramos: number;
  readonly largoCm: number;
  readonly anchoCm: number;
  readonly altoCm: number;
}

/** `correccion` dice que había una medida anterior, y que algún pedido salió con ella. */
export interface VarianteMedida {
  readonly varianteId: string;
  readonly sku: string;
  readonly pesoGramos: number;
  readonly largoCm: number;
  readonly anchoCm: number;
  readonly altoCm: number;
  readonly correccion: boolean;
}
