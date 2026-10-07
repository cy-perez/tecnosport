/**
 * Qué fotos del borrador son la misma prenda, y de qué color es cada prenda (ADR-0070).
 *
 * <p>Una prenda es una variante del producto y puede tener varias fotos: dos ángulos del mismo
 * bolso son una prenda, y cuatro jeans negros de diseño distinto son cuatro. **El color no decide
 * cuál de los dos casos es**, ni en un sentido ni en el otro; lo decide quien mira las fotos.
 *
 * <p>Una foto sin prenda vale para todas: no retrata ninguna de las que se pueden elegir. Marcarle
 * un color la vuelve una prenda ella sola, que es lo que pasaba antes de que hubiera prendas; y
 * para juntarla con otra se elige la prenda de la otra.
 *
 * <p>Todo es inmutable: cada operación devuelve una asignación nueva, y las prendas que se quedan
 * sin fotos se olvidan con su color, para que el número que se reutilice no herede uno ajeno.
 */
export interface AsignacionDePrendas {
  /** La prenda de cada foto, por `mensajeId`. Sin entrada = la foto vale para todas. */
  readonly prendaPorFoto: Readonly<Record<string, number>>;
  /** El color de cada prenda, por su número: «Negro» o «Negro / Vino». Vacío = sin elegir. */
  readonly tonoPorPrenda: Readonly<Record<number, string>>;
}

export const SIN_PRENDAS: AsignacionDePrendas = { prendaPorFoto: {}, tonoPorPrenda: {} };

/** Los números de prenda que tienen alguna foto, de menor a mayor. */
export function prendasEnUso(asignacion: AsignacionDePrendas): number[] {
  return [...new Set(Object.values(asignacion.prendaPorFoto))].sort((a, b) => a - b);
}

/** El número más bajo libre: quitar una prenda deja su número para la siguiente. */
function prendaLibre(asignacion: AsignacionDePrendas): number {
  const usadas = new Set(prendasEnUso(asignacion));
  let numero = 1;
  while (usadas.has(numero)) {
    numero++;
  }
  return numero;
}

function sinPrendasVacias(asignacion: AsignacionDePrendas): AsignacionDePrendas {
  const usadas = new Set(prendasEnUso(asignacion));
  const tonoPorPrenda: Record<number, string> = {};
  for (const [prenda, tono] of Object.entries(asignacion.tonoPorPrenda)) {
    if (usadas.has(Number(prenda))) {
      tonoPorPrenda[Number(prenda)] = tono;
    }
  }
  return { prendaPorFoto: asignacion.prendaPorFoto, tonoPorPrenda };
}

function conFotoEn(
  asignacion: AsignacionDePrendas,
  mensajeId: string,
  prenda: number | null,
): Record<string, number> {
  const prendaPorFoto = { ...asignacion.prendaPorFoto };
  if (prenda === null) {
    delete prendaPorFoto[mensajeId];
  } else {
    prendaPorFoto[mensajeId] = prenda;
  }
  return prendaPorFoto;
}

function fotosDe(asignacion: AsignacionDePrendas, prenda: number): number {
  return Object.values(asignacion.prendaPorFoto).filter((p) => p === prenda).length;
}

/** El color que muestra la foto: el de su prenda, o vacío si vale para todas. */
export function tonoDeFoto(asignacion: AsignacionDePrendas, mensajeId: string): string {
  const prenda = asignacion.prendaPorFoto[mensajeId];
  return prenda === undefined ? '' : (asignacion.tonoPorPrenda[prenda] ?? '');
}

/**
 * Marcarle un color a una foto. Si es de una prenda, lo cambia la prenda entera —todas sus fotos
 * son la misma prenda y tienen el mismo color—; si vale para todas, la vuelve una prenda nueva con
 * ese color.
 *
 * <p>Quitárselo a la foto que está sola en su prenda la devuelve a valer para todas, como antes de
 * que hubiera prendas. En una prenda de varias fotos deja **la prenda** sin color y no saca a nadie:
 * el selector de colores es de casillas, y cambiar «Negro» por «Café» pasa por un momento sin
 * ninguno marcado — si eso sacara la foto, cambiar de color deshacería el grupo. Para sacar una
 * foto de su prenda está el selector de prenda.
 */
export function elegirTonoDeFoto(
  asignacion: AsignacionDePrendas,
  mensajeId: string,
  tono: string,
): AsignacionDePrendas {
  const prenda = asignacion.prendaPorFoto[mensajeId];
  if (!tono && prenda !== undefined && fotosDe(asignacion, prenda) > 1) {
    return {
      prendaPorFoto: asignacion.prendaPorFoto,
      tonoPorPrenda: { ...asignacion.tonoPorPrenda, [prenda]: '' },
    };
  }
  if (!tono) {
    return sinPrendasVacias({
      prendaPorFoto: conFotoEn(asignacion, mensajeId, null),
      tonoPorPrenda: asignacion.tonoPorPrenda,
    });
  }
  if (prenda !== undefined) {
    return {
      prendaPorFoto: asignacion.prendaPorFoto,
      tonoPorPrenda: { ...asignacion.tonoPorPrenda, [prenda]: tono },
    };
  }
  const nueva = prendaLibre(asignacion);
  return {
    prendaPorFoto: conFotoEn(asignacion, mensajeId, nueva),
    tonoPorPrenda: { ...asignacion.tonoPorPrenda, [nueva]: tono },
  };
}

/**
 * Pasar una foto a otra prenda, a una nueva o a ninguna. En una que ya existe toma su color; en
 * una nueva se lleva el que tenía, para que separar una foto de su grupo no le borre el color.
 */
export function moverFotoAPrenda(
  asignacion: AsignacionDePrendas,
  mensajeId: string,
  destino: number | 'nueva' | null,
): AsignacionDePrendas {
  if (destino === 'nueva') {
    const tono = tonoDeFoto(asignacion, mensajeId);
    const sinElla = sinPrendasVacias({
      prendaPorFoto: conFotoEn(asignacion, mensajeId, null),
      tonoPorPrenda: asignacion.tonoPorPrenda,
    });
    const nueva = prendaLibre(sinElla);
    return {
      prendaPorFoto: conFotoEn(sinElla, mensajeId, nueva),
      tonoPorPrenda: { ...sinElla.tonoPorPrenda, [nueva]: tono },
    };
  }
  return sinPrendasVacias({
    prendaPorFoto: conFotoEn(asignacion, mensajeId, destino),
    tonoPorPrenda: asignacion.tonoPorPrenda,
  });
}

/**
 * Todas las fotos dadas en una sola prenda, la 1, con el primer color que alguna ya tuviera: el
 * atajo para el producto que viene en una sola prenda fotografiada desde varios ángulos.
 */
export function unaSolaPrenda(
  asignacion: AsignacionDePrendas,
  mensajeIds: readonly string[],
): AsignacionDePrendas {
  const tono = mensajeIds.map((id) => tonoDeFoto(asignacion, id)).find((t) => t) ?? '';
  const prendaPorFoto: Record<string, number> = {};
  for (const id of mensajeIds) {
    prendaPorFoto[id] = 1;
  }
  return { prendaPorFoto, tonoPorPrenda: { 1: tono } };
}

/** Una foto eliminada sale de su prenda; si era la única, la prenda se va con su color. */
export function olvidarFotoDePrendas(
  asignacion: AsignacionDePrendas,
  mensajeId: string,
): AsignacionDePrendas {
  return moverFotoAPrenda(asignacion, mensajeId, null);
}

/** Una variante que la aprobación va a crear, con las fotos que cuelgan de ella. */
export interface VarianteQueSeCrea {
  readonly prenda: number;
  /** El valor del atributo Color: el tono, numerado si otra prenda tiene el mismo. */
  readonly valor: string;
  /** Las fotos de la prenda que entran al producto, en el orden en que se publican. */
  readonly fotos: readonly string[];
}

/**
 * Las variantes de color que va a crear la aprobación, en el orden de su primera foto, con la misma
 * numeración que `AprobarBorrador` en la API: un tono que se repite entre prendas distintas sale
 * «Negro 1», «Negro 2»; el de una sola prenda se queda como está, tenga las fotos que tenga.
 *
 * @param elegidas las fotos que entran, en el orden de publicación
 */
export function variantesQueSeCrean(
  asignacion: AsignacionDePrendas,
  elegidas: readonly string[],
): VarianteQueSeCrea[] {
  const fotosPorPrenda = new Map<number, string[]>();
  for (const id of elegidas) {
    const prenda = asignacion.prendaPorFoto[id];
    if (prenda !== undefined && asignacion.tonoPorPrenda[prenda]) {
      fotosPorPrenda.set(prenda, [...(fotosPorPrenda.get(prenda) ?? []), id]);
    }
  }
  const veces = new Map<string, number>();
  for (const prenda of fotosPorPrenda.keys()) {
    const tono = asignacion.tonoPorPrenda[prenda];
    veces.set(tono, (veces.get(tono) ?? 0) + 1);
  }
  const vistos = new Map<string, number>();
  return [...fotosPorPrenda.entries()].map(([prenda, fotos]) => {
    const tono = asignacion.tonoPorPrenda[prenda];
    const cual = (vistos.get(tono) ?? 0) + 1;
    vistos.set(tono, cual);
    return { prenda, valor: veces.get(tono) === 1 ? tono : `${tono} ${cual}`, fotos };
  });
}

/** Lo que impide aprobar las prendas como están, o nulo si nada. */
export type ProblemaDePrendas =
  /** Una prenda con fotos que entran y ningún color: no hay variante que crear con ellas. */
  | { readonly tipo: 'SIN_COLOR'; readonly prenda: number }
  /**
   * Una prenda con color cuyas fotos se quedan todas fuera: quien la armó cuenta con esa variante
   * en la ficha, y no se crearía.
   */
  | { readonly tipo: 'FUERA'; readonly mensajeId: string };

export function problemaDePrendas(
  asignacion: AsignacionDePrendas,
  elegidas: readonly string[],
): ProblemaDePrendas | null {
  const entran = new Set(elegidas);
  for (const id of elegidas) {
    const prenda = asignacion.prendaPorFoto[id];
    if (prenda !== undefined && !asignacion.tonoPorPrenda[prenda]) {
      return { tipo: 'SIN_COLOR', prenda };
    }
  }
  const conFotosDentro = new Set(
    elegidas.map((id) => asignacion.prendaPorFoto[id]).filter((p) => p !== undefined),
  );
  for (const [id, prenda] of Object.entries(asignacion.prendaPorFoto)) {
    if (!entran.has(id) && !conFotosDentro.has(prenda) && asignacion.tonoPorPrenda[prenda]) {
      return { tipo: 'FUERA', mensajeId: id };
    }
  }
  return null;
}
