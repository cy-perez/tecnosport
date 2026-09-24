export interface Dinero {
  readonly valor: number;
  readonly moneda: string;
}

/** Una resolución publicada de la imagen. La lista completa es lo que arma el `srcset`. */
export interface VarianteDeImagen {
  readonly ancho: number;
  readonly url: string;
}

export interface Imagen {
  /** La variante mayor: lo que se sirve cuando el navegador no elige. */
  readonly url: string;
  /** De menor a mayor ancho, como las devuelve la API. Nunca vacía. */
  readonly variantes: readonly VarianteDeImagen[];
  /** El JPEG para los previsualizadores de enlaces. Vacío mientras no se haya subido. */
  readonly urlVistaPrevia: string | null;
  readonly ancho: number;
  readonly alto: number;
  readonly altEs: string;
  readonly altEn: string;
}

/** Un fotograma de rotación se publica en un solo ancho: el visor los pinta todos igual. */
export interface ImagenRotacion {
  readonly orden: number;
  readonly url: string;
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
  /** Lo que acompaña al valor cuando el número solo no dice nada: "12" + "meses". */
  readonly unidad: string | null;
}

export interface Variante {
  readonly id: string;
  readonly sku: string;
  readonly precio: Dinero;
  /**
   * Si se puede comprar ahora mismo. Un booleano y no un número desde `ADR-0050`: el servidor lo
   * calcula sobre el libro de movimientos —descontando las reservas de los pedidos en vuelo— y la
   * vitrina nunca usó el número para otra cosa que compararlo con cero.
   */
  readonly disponible: boolean;
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
  readonly unidad: string | null;
}

export interface Categoria {
  readonly id: string;
  readonly nombre: string;
  readonly slug: string;
  readonly linea: string;
  /**
   * La categoría de la que cuelga, o `null` si cuelga directamente de la línea.
   *
   * Llega plano y no anidado desde la API a propósito: el menú necesita el árbol, el desplegable
   * del filtro necesita la lista, y anidar en el contrato obligaría al segundo a aplanar lo que el
   * primero va a colgar. Lo cuelga `construirArbolDeCategorias`.
   */
  readonly padreId: string | null;
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
 * Los `loaderParams` que el `IMAGE_LOADER` lee para resolver cada ancho, **memoizados por imagen**.
 *
 * La memoización no es una optimización: es lo que hace que esto funcione. Todas las entradas de
 * `NgOptimizedImage` distintas de `ngSrc` están congeladas tras inicializar, y un objeto literal
 * construido en la plantilla cambia de identidad en cada ciclo de detección — con lo que la
 * directiva revienta con NG02953 en el primer refresco, aunque la imagen no haya cambiado. Con el
 * `WeakMap` la identidad dura lo que dure el objeto de la imagen, que TanStack Query conserva
 * entre revalidaciones idénticas por su *structural sharing*.
 *
 * Cuando la imagen sí cambia —elegir otra miniatura—, la identidad cambia con razón, y ahí lo que
 * toca es que el `<img>` nazca de nuevo: eso lo resuelve la plantilla con un `@for` de una sola
 * entrada.
 */
const PARAMETROS_DE_IMAGEN = new WeakMap<Imagen, Record<string, unknown>>();

export function parametrosDe(imagen: Imagen): Record<string, unknown> {
  const memoizados = PARAMETROS_DE_IMAGEN.get(imagen);
  if (memoizados) {
    return memoizados;
  }
  const parametros = { variantes: imagen.variantes };
  PARAMETROS_DE_IMAGEN.set(imagen, parametros);
  return parametros;
}

/**
 * Los descriptores del `srcset`: `"480w, 800w, 1200w"`.
 *
 * Solo los anchos, no las URL. Quien las resuelve es el `IMAGE_LOADER` de Angular, que recibe las
 * variantes por `loaderParams` y busca la del ancho pedido — así el frontend nunca deduce una URL
 * a partir de la forma de una key del bucket, que es el acoplamiento que produjo `url_webp`.
 *
 * Vive aquí y no en cada plantilla para que la tarjeta y la galería no elijan distinto sobre el
 * mismo dato: durante un tiempo la galería sirvió el original y el visor la WebP, sin que nadie lo
 * decidiera.
 */
export function descriptoresDe(imagen: Imagen): string {
  return imagen.variantes.map((variante) => `${variante.ancho}w`).join(', ');
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
  return producto.variantes.some((variante) => variante.disponible);
}
