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
  readonly sku: string;
  readonly precio: Dinero;
  readonly existencia: number;
  readonly atributos: readonly ValorAtributo[];
}

export interface Marca {
  readonly id: string;
  readonly nombre: string;
}

export interface Categoria {
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
