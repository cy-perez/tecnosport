/**
 * Las referencias de envío (`ADR-0071`): con ellas se cotiza la ropa, el calzado y los bolsos que
 * nadie ha pasado por la báscula. Van todos en una bolsa con las medidas de referencia y la suma
 * del peso promedio de cada prenda.
 */

/** Las líneas que se cotizan con promedios. La tecnología no: se mide con la ficha del fabricante. */
export type LineaConPromedio = 'ROPA' | 'CALZADO' | 'BOLSOS';

/** Las medidas de la bolsa, en centímetros. Transversales: una sola para las tres líneas. */
export interface MedidasDeReferencia {
  readonly largoCm: number;
  readonly anchoCm: number;
  readonly altoCm: number;
}

/**
 * Una categoría hoja con su peso promedio. `rama` es el nombre de la madre ("Dama"), `null` si
 * cuelga directo de la línea, como "Calzado › Unisex". `pesoGramos` en `null` es "sin promedio":
 * sus productos sin medir solo se venden con recogida en el punto.
 */
export interface CategoriaConPeso {
  readonly categoriaId: string;
  readonly nombre: string;
  readonly rama: string | null;
  readonly linea: LineaConPromedio;
  readonly pesoGramos: number | null;
}

export interface ReferenciasDeEnvio {
  /** `null` si nadie las ha fijado: entonces nada se cotiza con promedios. */
  readonly medidas: MedidasDeReferencia | null;
  readonly categorias: readonly CategoriaConPeso[];
}
