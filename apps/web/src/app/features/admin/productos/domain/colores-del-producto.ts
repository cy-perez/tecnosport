import { VarianteResumenAdmin } from './producto-admin.model';

/** Una talla —o lo que distinga a la variante sin el color— en que se pide la existencia. */
export interface ModeloDeColorNuevo {
  /** La variante que ya existe en esa talla: la nueva copia su precio y su empaque. */
  readonly modeloId: string;
  /** «S-M», «S-M · Algodón», o vacío si el producto no tiene más que el color. */
  readonly etiqueta: string;
}

function esColor(atributo: VarianteResumenAdmin['atributos'][number]): boolean {
  return atributo.colorHex !== null;
}

/** Si alguna variante tiene color. */
export function tieneColor(variantes: readonly VarianteResumenAdmin[]): boolean {
  return variantes.some((variante) => variante.atributos.some(esColor));
}

/** Los colores en que ya se vende, como se escriben: «Negro», «Negro / Vino». */
export function coloresDelProducto(variantes: readonly VarianteResumenAdmin[]): string[] {
  const colores = variantes.flatMap((variante) =>
    variante.atributos.filter(esColor).map((atributo) => atributo.valor),
  );
  return [...new Set(colores)];
}

/**
 * Una variante por cada combinación distinta de lo que no es color —en una prenda, una por talla—,
 * la primera de cada una. Es la misma regla que `Producto.modelosSinColor` en la API, que es quien
 * decide: si aquí faltara una talla, el servidor rechaza el color nuevo en vez de crearlo cojo.
 */
export function modelosSinColor(variantes: readonly VarianteResumenAdmin[]): ModeloDeColorNuevo[] {
  const porCombinacion = new Map<string, ModeloDeColorNuevo>();
  for (const variante of variantes) {
    const otros = variante.atributos.filter((atributo) => !esColor(atributo));
    const clave = JSON.stringify(otros.map((atributo) => [atributo.nombre, atributo.valor]));
    if (!porCombinacion.has(clave)) {
      porCombinacion.set(clave, {
        modeloId: variante.id,
        etiqueta: otros.map((atributo) => atributo.valor).join(' · '),
      });
    }
  }
  return [...porCombinacion.values()];
}
