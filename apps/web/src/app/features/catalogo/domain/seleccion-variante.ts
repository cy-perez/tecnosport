import { Imagen, Producto, Variante } from './producto.model';

export interface OpcionEje {
  readonly valor: string;
  readonly colorHex: string | null;
  /**
   * Si alguna variante del producto la tiene. Falso en las tallas que la escala de la categoría
   * enseña aunque este producto no venga en ellas: se pintan tachadas, como las agotadas, para que
   * quien compra vea la escala entera y no tenga que adivinar si su talla existe.
   */
  readonly existe: boolean;
}

export interface EjeAtributo {
  readonly nombre: string;
  /** Del eje y no de cada opción: todos los valores de un atributo se miden igual. */
  readonly unidad: string | null;
  readonly opciones: readonly OpcionEje[];
}

/** Lo que se pinta en el botón: "12 meses", o "M" cuando el eje no tiene unidad. */
export function etiquetaDeOpcion(eje: EjeAtributo, opcion: OpcionEje): string {
  return eje.unidad ? `${opcion.valor} ${eje.unidad}` : opcion.valor;
}

/** Nombre de atributo -> valor elegido. */
export type Seleccion = Readonly<Record<string, string>>;

/** El valor de talla con que la ingesta publica una prenda de talla única. */
export const TALLA_UNICA = 'Única';

function plano(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

/** «Talla», «Talla calzado»: el eje que se ordena y se completa con la escala de la categoría. */
export function esEjeDeTalla(nombre: string): boolean {
  return plano(nombre).startsWith('talla');
}

/**
 * Un eje por cada nombre de atributo distinto entre las variantes del producto (Color, Talla...).
 *
 * Con la escala de tallas de la categoría, el eje de talla la sigue entera y en su orden —XS antes
 * que S, 34 antes que 35—, con las tallas que este producto no trae marcadas como inexistentes; lo
 * que el producto traiga fuera de la escala va al final, en el orden en que llegó.
 */
export function ejesDeAtributos(
  producto: Producto,
  escalaTallas: readonly string[] = [],
): EjeAtributo[] {
  const valoresPorEje = new Map<string, Map<string, string | null>>();
  const unidadPorEje = new Map<string, string | null>();

  for (const variante of producto.variantes) {
    for (const valorAtributo of variante.atributos) {
      if (!valoresPorEje.has(valorAtributo.nombre)) {
        valoresPorEje.set(valorAtributo.nombre, new Map());
        unidadPorEje.set(valorAtributo.nombre, valorAtributo.unidad);
      }
      valoresPorEje.get(valorAtributo.nombre)!.set(valorAtributo.valor, valorAtributo.colorHex);
    }
  }

  return [...valoresPorEje.entries()].map(([nombre, valores]) => {
    const presentes: OpcionEje[] = [...valores.entries()].map(([valor, colorHex]) => ({
      valor,
      colorHex,
      existe: true,
    }));
    const unicaSola = presentes.length === 1 && esTallaUnica(presentes[0].valor);
    return {
      nombre,
      unidad: unidadPorEje.get(nombre) ?? null,
      opciones:
        esEjeDeTalla(nombre) && escalaTallas.length > 0 && !unicaSola
          ? segunLaEscala(presentes, escalaTallas)
          : presentes,
    };
  });
}

function segunLaEscala(presentes: OpcionEje[], escala: readonly string[]): OpcionEje[] {
  const porValor = new Map(presentes.map((opcion) => [plano(opcion.valor), opcion]));
  const enEscala = escala.map(
    (talla) => porValor.get(plano(talla)) ?? { valor: talla, colorHex: null, existe: false },
  );
  const deLaEscala = new Set(escala.map(plano));
  const fuera = presentes.filter((opcion) => !deLaEscala.has(plano(opcion.valor)));
  return [...enEscala, ...fuera];
}

function esTallaUnica(valor: string): boolean {
  return plano(valor) === plano(TALLA_UNICA);
}

export function seleccionDeVariante(variante: Variante): Seleccion {
  return Object.fromEntries(variante.atributos.map((va) => [va.nombre, va.valor]));
}

/** `null` si la combinación elegida no corresponde a ningún SKU real. */
export function varianteSeleccionada(producto: Producto, seleccion: Seleccion): Variante | null {
  const nombresEjes = Object.keys(seleccion);
  return (
    producto.variantes.find((variante) => {
      const valoresVariante = seleccionDeVariante(variante);
      return nombresEjes.every((nombre) => valoresVariante[nombre] === seleccion[nombre]);
    }) ?? null
  );
}

/**
 * Si elegir esta opción, con lo demás que ya está elegido, lleva a algo que se puede comprar: una
 * talla agotada en el color elegido, o que no existe en él, no lo hace. Es lo que la ficha tacha.
 */
export function opcionDisponible(
  producto: Producto,
  seleccion: Seleccion,
  nombreEje: string,
  valor: string,
): boolean {
  return producto.variantes.some((variante) => {
    if (!variante.disponible) {
      return false;
    }
    const valores = seleccionDeVariante(variante);
    if (valores[nombreEje] !== valor) {
      return false;
    }
    return Object.entries(seleccion).every(
      ([nombre, elegido]) => nombre === nombreEje || valores[nombre] === elegido,
    );
  });
}

/** Lo que la variante es, para decirlo en el carrito: «Negro · M», «12 meses». */
export function detalleDeVariante(variante: Variante): string {
  return variante.atributos
    .map((valor) => (valor.unidad ? `${valor.valor} ${valor.unidad}` : valor.valor))
    .join(' · ');
}

/** La primera variante disponible; si ninguna lo está, la primera de todas. */
export function variantePorDefecto(producto: Producto): Variante | null {
  return (
    producto.variantes.find((variante) => variante.disponible) ?? producto.variantes[0] ?? null
  );
}

/**
 * La talla de una prenda de talla única, para decirla en la tarjeta y en la ficha: quien compra no
 * sabe de qué talla es un bodi si solo ve la marca, el nombre y el precio. `null` si el producto
 * talla de otra forma o no talla.
 */
export function tallaUnicaDe(producto: Producto): { readonly sirveHasta: string | null } | null {
  const tallas = new Set<string>();
  for (const variante of producto.variantes) {
    for (const valor of variante.atributos) {
      if (esEjeDeTalla(valor.nombre)) {
        tallas.add(plano(valor.valor));
      }
    }
  }
  if (tallas.size !== 1 || !tallas.has(plano(TALLA_UNICA))) {
    return null;
  }
  return { sirveHasta: producto.tallaSirveHasta };
}

/** Un color del producto con su muestra: lo que pinta la tarjeta debajo de la foto. */
export interface ColorDeProducto {
  readonly valor: string;
  readonly colorHex: string;
}

/** Los colores del producto, sin repetir y en el orden de sus variantes; solo los que tienen HEX. */
export function coloresDe(producto: Producto): ColorDeProducto[] {
  const vistos = new Map<string, string>();
  for (const variante of producto.variantes) {
    for (const valor of variante.atributos) {
      if (valor.colorHex && !vistos.has(valor.valor)) {
        vistos.set(valor.valor, valor.colorHex);
      }
    }
  }
  return [...vistos.entries()].map(([valor, colorHex]) => ({ valor, colorHex }));
}

function variantesDelColor(producto: Producto, color: string): Set<string> {
  return new Set(
    producto.variantes
      .filter((variante) =>
        variante.atributos.some((valor) => valor.colorHex !== null && valor.valor === color),
      )
      .map((variante) => variante.id),
  );
}

/**
 * Las fotos de la ficha para el color elegido: las de ese tono y las que valen para todos. Sin
 * color, o si ninguna foto es de ese tono, todas — una galería vacía no le sirve a nadie.
 */
export function imagenesDelColor(
  producto: Producto,
  imagenes: readonly Imagen[],
  color: string | null,
): Imagen[] {
  if (!color) {
    return [...imagenes];
  }
  const delColor = variantesDelColor(producto, color);
  const propias = imagenes.filter((imagen) => imagen.varianteId && delColor.has(imagen.varianteId));
  if (propias.length === 0) {
    return [...imagenes];
  }
  return [...propias, ...imagenes.filter((imagen) => imagen.varianteId === null)];
}

/**
 * La foto de la tarjeta para un color: la primera de ese tono, o la principal. La principal no
 * cuelga de ninguna variante —el panel la reemplaza como la del producto—, así que el color que
 * muestra es el que no tiene otra foto propia.
 */
export function imagenDelColor(producto: Producto, color: string | null): Imagen | null {
  if (!color) {
    return producto.imagenPrincipal;
  }
  const delColor = variantesDelColor(producto, color);
  return (
    producto.galeria.find((imagen) => imagen.varianteId && delColor.has(imagen.varianteId)) ??
    producto.imagenPrincipal
  );
}

/** El nombre del eje de color del producto, si lo tiene: «Color». */
export function ejeDeColor(producto: Producto): string | null {
  for (const variante of producto.variantes) {
    const color = variante.atributos.find((valor) => valor.colorHex !== null);
    if (color) {
      return color.nombre;
    }
  }
  return null;
}
