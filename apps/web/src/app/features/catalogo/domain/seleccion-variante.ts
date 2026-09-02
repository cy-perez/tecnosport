import { Producto, Variante } from './producto.model';

export interface OpcionEje {
  readonly valor: string;
  readonly colorHex: string | null;
}

export interface EjeAtributo {
  readonly nombre: string;
  readonly opciones: readonly OpcionEje[];
}

/** Nombre de atributo -> valor elegido. */
export type Seleccion = Readonly<Record<string, string>>;

/** Un eje por cada nombre de atributo distinto entre las variantes del producto (Color, Talla...). */
export function ejesDeAtributos(producto: Producto): EjeAtributo[] {
  const valoresPorEje = new Map<string, Map<string, string | null>>();

  for (const variante of producto.variantes) {
    for (const valorAtributo of variante.atributos) {
      if (!valoresPorEje.has(valorAtributo.nombre)) {
        valoresPorEje.set(valorAtributo.nombre, new Map());
      }
      valoresPorEje.get(valorAtributo.nombre)!.set(valorAtributo.valor, valorAtributo.colorHex);
    }
  }

  return [...valoresPorEje.entries()].map(([nombre, valores]) => ({
    nombre,
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
  return producto.variantes.find((variante) => variante.existencia > 0) ?? producto.variantes[0] ?? null;
}
