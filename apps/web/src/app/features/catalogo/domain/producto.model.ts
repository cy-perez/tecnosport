export interface Dinero {
  readonly valor: number;
  readonly moneda: string;
}

export interface Imagen {
  readonly url: string;
  readonly urlWebp: string;
  readonly ancho: number;
  readonly alto: number;
  readonly altEs: string;
  readonly altEn: string;
}

export interface ImagenRotacion {
  readonly orden: number;
  readonly url: string;
  readonly urlWebp: string;
  readonly ancho: number;
  readonly alto: number;
}

export interface Rotacion {
  readonly fotogramas: number;
  readonly imagenes: readonly ImagenRotacion[];
}

export interface ValorAtributo {
  readonly nombre: string;
  readonly valor: string;
  readonly colorHex: string | null;
}

export interface Variante {
  readonly id: string;
  readonly sku: string;
  readonly precio: Dinero;
  readonly existencia: number;
  readonly atributos: readonly ValorAtributo[];
}

export interface Marca {
  readonly id: string;
  readonly nombre: string;
}

export type TipoAtributo = 'TEXTO' | 'NUMERO' | 'COLOR';

/** Catálogo global de ejes de variación (talla, color...) — sin asociación a categoría en el
 * esquema, ver docs/02-modelo-datos.md. Lo usa el panel admin al armar una variante. */
export interface Atributo {
  readonly id: string;
  readonly nombre: string;
  readonly tipo: TipoAtributo;
  readonly valoresPermitidos: readonly string[];
}

export interface Categoria {
  readonly id: string;
  readonly nombre: string;
  readonly slug: string;
  readonly linea: string;
}

export interface Producto {
  readonly slug: string;
  readonly nombre: string;
  readonly descripcion: string;
  readonly marca: Marca;
  readonly categoria: Categoria;
  readonly imagenPrincipal: Imagen | null;
  readonly galeria: readonly Imagen[];
  readonly rotacion: Rotacion | null;
  readonly variantes: readonly Variante[];
}

/**
 * WebP con el original de respaldo, que es la regla de imágenes de `apps/web/CLAUDE.md`. Vive aquí
 * y no en cada plantilla para que la galería y el visor 360 no elijan distinto sobre el mismo dato:
 * durante un tiempo la galería sirvió el original y el visor la WebP, sin que nadie lo decidiera.
 */
export function urlPreferida(imagen: { readonly url: string; readonly urlWebp: string }): string {
  return imagen.urlWebp || imagen.url;
}

/** El precio vive en la variante, no en el producto: "desde" es el menor entre sus variantes. */
export function precioDesde(producto: Producto): Dinero | null {
  if (producto.variantes.length === 0) {
    return null;
  }
  return producto.variantes.reduce(
    (menor, variante) => (variante.precio.valor < menor.valor ? variante.precio : menor),
    producto.variantes[0].precio,
  );
}

export function hayExistencia(producto: Producto): boolean {
  return producto.variantes.some((variante) => variante.existencia > 0);
}
