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
  /**
   * La variante cuyo tono muestra la foto, o `null` si vale para todas. Es lo que deja cambiar la
   * foto al elegir un color, en la tarjeta y en la ficha.
   */
  readonly varianteId: string | null;
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

/** Lo que no es un color sino un diseño. Los mismos nombres que `PatronDeColor` en la API. */
export type PatronDeColor = 'MULTICOLOR' | 'ESTAMPADO' | 'ANIMAL_PRINT';

/**
 * Una porción del círculo de un color: `patron` nulo es liso y trae un solo color; un patrón trae
 * los suyos en orden. «Negro / Rojo» son dos porciones, en el orden en que se eligieron.
 */
export interface ParteDeMuestra {
  readonly patron: PatronDeColor | null;
  readonly colores: readonly string[];
}

export interface ValorAtributo {
  readonly nombre: string;
  readonly valor: string;
  readonly colorHex: string | null;
  /** Lo que acompaña al valor cuando el número solo no dice nada: "12" + "meses". */
  readonly unidad: string | null;
  /**
   * Las porciones del círculo, en un color. Ausente o vacía, la muestra es `colorHex` solo: así la
   * cargaba todo antes de las combinaciones (4 de octubre de 2026). Leerla con `muestraDe`.
   */
  readonly muestra?: readonly ParteDeMuestra[];
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
  /**
   * Las etiquetas con que se difunden en redes los productos de esta categoría, con almohadilla.
   *
   * Viajan también en la respuesta pública, que solo las ignora: el razonamiento está en
   * `CategoriaRespuesta` del backend. Vacío es lo normal mientras nadie las escriba — un pie sin
   * etiquetas se publica igual, solo llega a menos gente.
   */
  readonly hashtags: readonly string[];
  /**
   * Las tallas propias de la categoría, en su orden; vacía si usa las de su rama o no talla. Quien
   * necesita la que vale —la propia o la heredada— la pide a `escalaDeTallasDe`.
   */
  readonly escalaTallas: readonly string[];
}

/**
 * Un color de la paleta con que se marca el tono de cada foto. Vive en la base y llega por la API:
 * es un dato del producto, no del sistema visual, así que su HEX no es un literal del frontend.
 */
export interface ColorDePaleta {
  readonly nombre: string;
  readonly nombreEn: string;
  readonly hex: string;
  /** Un diseño —multicolor, estampado, animal print— y los colores que lo dibujan. */
  readonly patron?: PatronDeColor | null;
  readonly coloresPatron?: readonly string[];
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
  /**
   * Las tallas de la categoría en su orden —XS…XXXL, 26…42, 34…43—, heredadas de la rama si la
   * hoja no tiene las suyas. Solo llega con la ficha: en la rejilla viene vacía.
   */
  readonly escalaTallas: readonly string[];
  /** Hasta qué talla le sirve una prenda de talla única, si el proveedor lo dijo. */
  readonly tallaSirveHasta: string | null;
  /**
   * Si las fotos sin variante —casi siempre la principal— acompañan a las de cada color en la
   * galería. Falso en el producto que solo trae fotos por color: ahí la general es la de uno de
   * ellos y se colaría en los demás.
   */
  readonly fotosGeneralesEnCadaColor: boolean;
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

/** La muestra de un valor de color: sus porciones, o `colorHex` solo si no las trae. */
export function muestraDe(valor: {
  readonly colorHex: string | null;
  readonly muestra?: readonly ParteDeMuestra[];
}): readonly ParteDeMuestra[] {
  if (valor.muestra && valor.muestra.length > 0) {
    return valor.muestra;
  }
  return valor.colorHex ? [{ patron: null, colores: [valor.colorHex] }] : [];
}

/** La porción que pinta un color de la paleta: su patrón, o su HEX liso. */
export function parteDeColor(color: ColorDePaleta): ParteDeMuestra {
  return color.patron && color.coloresPatron && color.coloresPatron.length > 0
    ? { patron: color.patron, colores: color.coloresPatron }
    : { patron: null, colores: [color.hex] };
}

/** Un color de la paleta listo para marcar: su nombre en español, lo que se lee y su muestra. */
export interface ColorParaElegir {
  readonly valor: string;
  readonly etiqueta: string;
  readonly muestra: ParteDeMuestra;
}

/**
 * La paleta como se ofrece para elegir, en orden alfabético de lo que se lee: el valor es el
 * nombre en español —el del atributo Color— y, en inglés, la etiqueta lleva el inglés delante.
 */
export function paletaParaElegir(
  paleta: readonly ColorDePaleta[],
  idioma: string,
): ColorParaElegir[] {
  const ingles = idioma === 'en';
  const comparador = new Intl.Collator(idioma, { sensitivity: 'base', numeric: true });
  return paleta
    .map((color) => ({
      valor: color.nombre,
      etiqueta: ingles ? `${color.nombreEn} (${color.nombre})` : color.nombre,
      muestra: parteDeColor(color),
    }))
    .sort((uno, otro) => comparador.compare(uno.etiqueta, otro.etiqueta));
}
