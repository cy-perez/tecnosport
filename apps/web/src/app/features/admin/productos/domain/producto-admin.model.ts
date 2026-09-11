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
  readonly pesoGramos: number;
  readonly largoCm: number;
  readonly anchoCm: number;
  readonly altoCm: number;
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
