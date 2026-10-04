/**
 * Lo que no es un color sino un diseño. Los mismos nombres que `PatronDeColor` en la API.
 */
export type PatronDeColor = 'MULTICOLOR' | 'ESTAMPADO' | 'ANIMAL_PRINT';

/**
 * Una porción del círculo: `patron` nulo es un color liso y `colores` trae uno solo; un patrón
 * trae los suyos en orden. Los colores vienen de la paleta, en la base: este tipo los transporta,
 * no los inventa.
 */
export interface ParteDeMuestra {
  readonly patron: PatronDeColor | null;
  readonly colores: readonly string[];
}

/** Cómo se escribe una combinación en el valor del atributo: «Negro / Rojo». */
export const SEPARADOR_DE_COLORES = ' / ';

/** Cuántos colores se combinan como máximo, como `MuestraDeColor.MAXIMO_DE_PARTES` en la API. */
export const MAXIMO_DE_COLORES = 3;

/** «Negro / Rojo» → ['Negro', 'Rojo']; los espacios alrededor de la barra no importan. */
export function separarColores(valor: string): string[] {
  return valor
    .split('/')
    .map((parte) => parte.trim())
    .filter((parte) => parte !== '');
}

/** ['Negro', 'Rojo'] → «Negro / Rojo». */
export function unirColores(nombres: readonly string[]): string {
  return nombres.join(SEPARADOR_DE_COLORES);
}
