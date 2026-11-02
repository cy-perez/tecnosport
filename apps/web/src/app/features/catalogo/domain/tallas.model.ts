import { TALLA_UNICA, tallaNormalizada } from './seleccion-variante';

/**
 * Una talla del filtro de la vitrina: `valor` es la normalizada, la que viaja en la URL y en la
 * consulta; `etiqueta`, como la escribe el catálogo.
 */
export interface OpcionDeTalla {
  readonly valor: string;
  readonly etiqueta: string;
}

/** Las tallas con existencia de cada línea, tal como las devuelve `/api/v1/tallas`. */
export interface TallasDisponibles {
  readonly ropa: readonly OpcionDeTalla[];
  readonly calzado: readonly OpcionDeTalla[];
}

export const SIN_TALLAS: TallasDisponibles = { ropa: [], calzado: [] };

/** La talla única normalizada, como llega en `valor`. */
export const VALOR_TALLA_UNICA = tallaNormalizada(TALLA_UNICA);

function comoNumero(valor: string): number | null {
  return /^\d+([.,]\d+)?$/.test(valor) ? Number(valor.replace(',', '.')) : null;
}

/**
 * Las tallas en el orden en que se leen, no en el alfabético que trae el servidor —que pondría
 * «L» antes que «M» y «XS» después de «XL»—.
 *
 * Primero las de letra, en el orden de las escalas de la línea (XS, S, M…); después las de número,
 * de menor a mayor —un jean 28 antes que un 30, un tenis 36 antes que un 37—; después lo que
 * ninguna escala nombra, como «S-M», en orden alfabético; y al final la talla única, que no es un
 * punto de la escala sino la ausencia de una. Las escalas se reciben aparte y no se adivinan: son
 * las de las categorías de la línea (`escalaTallas`), las mismas que ordenan el selector de la ficha.
 */
export function ordenarTallas(
  opciones: readonly OpcionDeTalla[],
  escalas: readonly (readonly string[])[],
): OpcionDeTalla[] {
  const posicionEnEscala = new Map<string, number>();
  for (const talla of escalas.flat().map(tallaNormalizada)) {
    if (!posicionEnEscala.has(talla)) {
      posicionEnEscala.set(talla, posicionEnEscala.size);
    }
  }

  const clave = (opcion: OpcionDeTalla): readonly [number, number, string] => {
    const valor = tallaNormalizada(opcion.valor);
    if (valor === VALOR_TALLA_UNICA) {
      return [3, 0, valor];
    }
    const numero = comoNumero(valor);
    if (numero !== null) {
      return [1, numero, valor];
    }
    const posicion = posicionEnEscala.get(valor);
    return posicion === undefined ? [2, 0, valor] : [0, posicion, valor];
  };

  return [...opciones].sort((a, b) => {
    const [grupoA, ordenA, textoA] = clave(a);
    const [grupoB, ordenB, textoB] = clave(b);
    return grupoA - grupoB || ordenA - ordenB || textoA.localeCompare(textoB);
  });
}

/** Las dos líneas que tienen talla, cada una con su desplegable en el filtro. */
export type LineaConTalla = 'ROPA' | 'CALZADO';

/**
 * Lo que ya se eligió en los otros filtros, en términos de línea: la línea misma, la de la
 * categoría y las líneas en que vende la marca. Ausente es "no se eligió" o "todavía no se sabe".
 */
export interface SeleccionDeLineas {
  readonly linea?: string;
  readonly lineaDeCategoria?: string;
  readonly lineasDeMarca?: readonly string[];
}

/**
 * Qué desplegables de talla tienen sentido con lo que ya se eligió.
 *
 * Cada elección acota: una línea o una categoría de ropa dejan solo la talla de ropa; una marca que
 * solo vende calzado, solo la de calzado; una marca que vende las dos, las dos; y tecnología o una
 * marca de bolsos, ninguna. Ofrecer la talla de calzado con una categoría de ropa elegida es
 * ofrecer una combinación que siempre da la rejilla vacía.
 *
 * Lo que no se sabe no acota: mientras las categorías o las marcas no han cargado, quien llama
 * pasa el campo ausente, y desactivar por no saber quitaría un filtro que sí aplica.
 */
export function lineasDeTallaPosibles(seleccion: SeleccionDeLineas): ReadonlySet<LineaConTalla> {
  const posibles: LineaConTalla[] = ['ROPA', 'CALZADO'];
  return new Set(
    posibles.filter(
      (linea) =>
        (!seleccion.linea || seleccion.linea === linea) &&
        (!seleccion.lineaDeCategoria || seleccion.lineaDeCategoria === linea) &&
        (!seleccion.lineasDeMarca || seleccion.lineasDeMarca.includes(linea)),
    ),
  );
}
