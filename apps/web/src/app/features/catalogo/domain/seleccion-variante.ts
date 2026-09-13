import { Producto, Variante } from './producto.model';

export interface OpcionEje {
  readonly valor: string;
  readonly colorHex: string | null;
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

/** Un eje por cada nombre de atributo distinto entre las variantes del producto (Color, Talla...). */
export function ejesDeAtributos(producto: Producto): EjeAtributo[] {
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

  return [...valoresPorEje.entries()].map(([nombre, valores]) => ({
    nombre,
    unidad: unidadPorEje.get(nombre) ?? null,
    opciones: [...valores.entries()].map(([valor, colorHex]) => ({ valor, colorHex })),
  }));
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

/** La primera variante con existencia; si ninguna tiene, la primera de todas. */
export function variantePorDefecto(producto: Producto): Variante | null {
  return (
    producto.variantes.find((variante) => variante.existencia > 0) ?? producto.variantes[0] ?? null
  );
}
