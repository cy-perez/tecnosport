/**
 * Qué fotos del borrador son la misma prenda, de qué color es cada prenda y en qué tallas viene
 * (ADR-0070, ADR-0081).
 *
 * <p>Una prenda es una variante del producto y puede tener varias fotos: dos ángulos del mismo
 * bolso son una prenda, y cuatro jeans negros de diseño distinto son cuatro. **El color no decide
 * cuál de los dos casos es**, ni en un sentido ni en el otro; lo decide quien mira las fotos.
 *
 * <p>Y una prenda puede no tener foto propia (12 de octubre de 2026): la blusa que el proveedor
 * fotografía en negro, cocoa y verde juntas, de frente y de espalda, no tiene una foto de cada tono,
 * y cada tono se vende. Esas son las prendas **agregadas aparte** de las fotos: existen aunque
 * ninguna foto las cite, y quien compra las ve en la foto de todos.
 *
 * <p>Una foto sin prenda vale para todas: no retrata ninguna de las que se pueden elegir. Marcarle
 * un color la vuelve una prenda ella sola, que es lo que pasaba antes de que hubiera prendas; y
 * para juntarla con otra se elige la prenda de la otra.
 *
 * <p>Todo es inmutable: cada operación devuelve una asignación nueva, y las prendas que se quedan
 * sin fotos —salvo las agregadas aparte— se olvidan con su color y sus tallas, para que el número
 * que se reutilice no herede nada ajeno.
 */
export interface AsignacionDePrendas {
  /** La prenda de cada foto, por `mensajeId`. Sin entrada = la foto vale para todas. */
  readonly prendaPorFoto: Readonly<Record<string, number>>;
  /** El color de cada prenda, por su número: «Negro» o «Negro / Vino». Vacío = sin elegir. */
  readonly tonoPorPrenda: Readonly<Record<number, string>>;
  /** Las prendas que existen aunque ninguna foto las cite, en el orden en que se agregaron. */
  readonly aparte: readonly number[];
  /**
   * Cómo llama el proveedor al color de la prenda —«cocoa»—, cuando la propuso la lectura: dice
   * qué elegir en la paleta, que no tiene «cocoa», y con qué nombre buscar sus tallas.
   */
  readonly pistaPorPrenda: Readonly<Record<number, string>>;
  /** Las tallas en que viene cada prenda, por su número. Sin entrada = en todas las del producto. */
  readonly tallasPorPrenda: Readonly<Record<number, readonly string[]>>;
}

export const SIN_PRENDAS: AsignacionDePrendas = {
  prendaPorFoto: {},
  tonoPorPrenda: {},
  aparte: [],
  pistaPorPrenda: {},
  tallasPorPrenda: {},
};

/** Los números de prenda que existen —con fotos o agregados aparte—, de menor a mayor. */
export function prendasEnUso(asignacion: AsignacionDePrendas): number[] {
  return [...new Set([...Object.values(asignacion.prendaPorFoto), ...asignacion.aparte])].sort(
    (a, b) => a - b,
  );
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

/** Solo las entradas de las prendas que siguen existiendo. */
function deLasQueQuedan<T>(
  porPrenda: Readonly<Record<number, T>>,
  quedan: ReadonlySet<number>,
): Record<number, T> {
  const filtrado: Record<number, T> = {};
  for (const [prenda, valor] of Object.entries(porPrenda)) {
    if (quedan.has(Number(prenda))) {
      filtrado[Number(prenda)] = valor;
    }
  }
  return filtrado;
}

function sinPrendasVacias(asignacion: AsignacionDePrendas): AsignacionDePrendas {
  const quedan = new Set(prendasEnUso(asignacion));
  return {
    ...asignacion,
    tonoPorPrenda: deLasQueQuedan(asignacion.tonoPorPrenda, quedan),
    pistaPorPrenda: deLasQueQuedan(asignacion.pistaPorPrenda, quedan),
    tallasPorPrenda: deLasQueQuedan(asignacion.tallasPorPrenda, quedan),
  };
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
 * que hubiera prendas; si su prenda es una agregada aparte, la prenda se queda, sin foto y con su
 * color. En una prenda de varias fotos deja **la prenda** sin color y no saca a nadie: el selector
 * de colores es de casillas, y cambiar «Negro» por «Café» pasa por un momento sin ninguno marcado —
 * si eso sacara la foto, cambiar de color deshacería el grupo. Para sacar una foto de su prenda está
 * el selector de prenda.
 *
 * <p>Una foto sin prenda que recibe el color de una prenda sin ninguna foto entra en ella: es la
 * foto de ese tono que el anuncio nombró.
 */
export function elegirTonoDeFoto(
  asignacion: AsignacionDePrendas,
  mensajeId: string,
  tono: string,
): AsignacionDePrendas {
  const prenda = asignacion.prendaPorFoto[mensajeId];
  if (!tono && prenda !== undefined && fotosDe(asignacion, prenda) > 1) {
    return elegirTonoDePrenda(asignacion, prenda, '');
  }
  if (!tono) {
    return sinPrendasVacias({
      ...asignacion,
      prendaPorFoto: conFotoEn(asignacion, mensajeId, null),
    });
  }
  if (prenda !== undefined) {
    return elegirTonoDePrenda(asignacion, prenda, tono);
  }
  // Si ya hay una prenda de ese color sin ninguna foto —la propuso el anuncio—, esta foto es la
  // suya: crear otra la dejaría como «Negro 1» y «Negro 2», y la de sin foto, repetida.
  const sinFotoDelColor = asignacion.aparte.find(
    (aparte) =>
      fotosDe(asignacion, aparte) === 0 &&
      claveDeColor(asignacion.tonoPorPrenda[aparte] ?? '') === claveDeColor(tono),
  );
  if (sinFotoDelColor !== undefined) {
    return { ...asignacion, prendaPorFoto: conFotoEn(asignacion, mensajeId, sinFotoDelColor) };
  }
  const nueva = prendaLibre(asignacion);
  return {
    ...asignacion,
    prendaPorFoto: conFotoEn(asignacion, mensajeId, nueva),
    tonoPorPrenda: { ...asignacion.tonoPorPrenda, [nueva]: tono },
  };
}

/** El color de una prenda entera, tenga fotos o no. */
export function elegirTonoDePrenda(
  asignacion: AsignacionDePrendas,
  prenda: number,
  tono: string,
): AsignacionDePrendas {
  return { ...asignacion, tonoPorPrenda: { ...asignacion.tonoPorPrenda, [prenda]: tono } };
}

/**
 * Pasar una foto a otra prenda, a una nueva o a ninguna. En una que ya existe toma su color; en
 * una nueva se lleva el que tenía —y la pista y las tallas—, para que separar una foto de su grupo
 * no le borre nada.
 */
export function moverFotoAPrenda(
  asignacion: AsignacionDePrendas,
  mensajeId: string,
  destino: number | 'nueva' | null,
): AsignacionDePrendas {
  if (destino === 'nueva') {
    const tono = tonoDeFoto(asignacion, mensajeId);
    const deDonde = asignacion.prendaPorFoto[mensajeId];
    const pista = deDonde === undefined ? undefined : asignacion.pistaPorPrenda[deDonde];
    const tallas = deDonde === undefined ? undefined : asignacion.tallasPorPrenda[deDonde];
    const sinElla = sinPrendasVacias({
      ...asignacion,
      prendaPorFoto: conFotoEn(asignacion, mensajeId, null),
    });
    const nueva = prendaLibre(sinElla);
    return {
      ...sinElla,
      prendaPorFoto: conFotoEn(sinElla, mensajeId, nueva),
      tonoPorPrenda: { ...sinElla.tonoPorPrenda, [nueva]: tono },
      pistaPorPrenda: pista
        ? { ...sinElla.pistaPorPrenda, [nueva]: pista }
        : sinElla.pistaPorPrenda,
      tallasPorPrenda: tallas
        ? { ...sinElla.tallasPorPrenda, [nueva]: tallas }
        : sinElla.tallasPorPrenda,
    };
  }
  return sinPrendasVacias({
    ...asignacion,
    prendaPorFoto: conFotoEn(asignacion, mensajeId, destino),
  });
}

/**
 * Todas las fotos dadas en una sola prenda, la 1, con el primer color que alguna ya tuviera: el
 * atajo para el producto que viene en una sola prenda fotografiada desde varios ángulos. Las
 * agregadas aparte se van: el atajo dice que el producto es una sola prenda.
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
  return { ...SIN_PRENDAS, prendaPorFoto, tonoPorPrenda: { 1: tono } };
}

/** Una foto eliminada sale de su prenda; si era la única, la prenda se va con su color. */
export function olvidarFotoDePrendas(
  asignacion: AsignacionDePrendas,
  mensajeId: string,
): AsignacionDePrendas {
  return moverFotoAPrenda(asignacion, mensajeId, null);
}

/**
 * Una prenda sin foto propia: un tono que se ve en la foto de todos. Con la pista del proveedor
 * cuando la propuso la lectura —«cocoa»—, y sin color si la paleta no tiene uno con ese nombre:
 * quien aprueba lo elige.
 */
export function agregarPrendaAparte(
  asignacion: AsignacionDePrendas,
  tono: string,
  pista: string | null = null,
): AsignacionDePrendas {
  const nueva = prendaLibre(asignacion);
  return {
    ...asignacion,
    aparte: [...asignacion.aparte, nueva],
    tonoPorPrenda: { ...asignacion.tonoPorPrenda, [nueva]: tono },
    pistaPorPrenda: pista
      ? { ...asignacion.pistaPorPrenda, [nueva]: pista }
      : asignacion.pistaPorPrenda,
  };
}

/** Quitar una prenda entera: sus fotos vuelven a valer para todas. */
export function quitarPrenda(asignacion: AsignacionDePrendas, prenda: number): AsignacionDePrendas {
  const prendaPorFoto: Record<string, number> = {};
  for (const [id, suya] of Object.entries(asignacion.prendaPorFoto)) {
    if (suya !== prenda) {
      prendaPorFoto[id] = suya;
    }
  }
  return sinPrendasVacias({
    ...asignacion,
    prendaPorFoto,
    aparte: asignacion.aparte.filter((p) => p !== prenda),
  });
}

/** Una variante que la aprobación va a crear, con las fotos que cuelgan de ella. */
export interface VarianteQueSeCrea {
  readonly prenda: number;
  /** El valor del atributo Color: el tono, numerado si otra prenda tiene el mismo. */
  readonly valor: string;
  /**
   * Las fotos de la prenda que entran al producto, en el orden en que se publican. Vacía en una
   * prenda sin foto propia.
   */
  readonly fotos: readonly string[];
}

/**
 * Las variantes de color que va a crear la aprobación, con la misma numeración y el mismo orden
 * que `AprobarBorrador` en la API: primero las prendas con fotos, en el orden de su primera foto;
 * detrás las agregadas aparte que no tienen ninguna que entre, en el orden en que se agregaron. Un
 * tono que se repite entre prendas distintas sale «Negro 1», «Negro 2»; el de una sola prenda se
 * queda como está, tenga las fotos que tenga.
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
  for (const prenda of asignacion.aparte) {
    if (!fotosPorPrenda.has(prenda) && asignacion.tonoPorPrenda[prenda]) {
      fotosPorPrenda.set(prenda, []);
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
  /** Una prenda con fotos que entran, o agregada aparte, y ningún color: no hay qué crear. */
  | { readonly tipo: 'SIN_COLOR'; readonly prenda: number }
  /**
   * Una prenda con color cuyas fotos se quedan todas fuera: quien la armó cuenta con esa variante
   * en la ficha, y no se crearía.
   */
  | { readonly tipo: 'FUERA'; readonly mensajeId: string }
  /**
   * Una prenda sin foto con el mismo color que otra: sin foto, «Negro 1» y «Negro 2» se leen igual
   * en la ficha, y la API la rechaza.
   */
  | { readonly tipo: 'REPETIDA'; readonly prenda: number; readonly tono: string }
  /** Con las tallas por color, una prenda que no viene en ninguna de las del producto. */
  | { readonly tipo: 'SIN_TALLAS'; readonly prenda: number; readonly tono: string };

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
  for (const prenda of asignacion.aparte) {
    if (!asignacion.tonoPorPrenda[prenda]) {
      return { tipo: 'SIN_COLOR', prenda };
    }
  }
  const conFotosDentro = new Set(
    elegidas.map((id) => asignacion.prendaPorFoto[id]).filter((p) => p !== undefined),
  );
  for (const [id, prenda] of Object.entries(asignacion.prendaPorFoto)) {
    if (
      !entran.has(id) &&
      !conFotosDentro.has(prenda) &&
      !asignacion.aparte.includes(prenda) &&
      asignacion.tonoPorPrenda[prenda]
    ) {
      return { tipo: 'FUERA', mensajeId: id };
    }
  }
  const variantes = variantesQueSeCrean(asignacion, elegidas);
  for (const variante of variantes.filter((v) => v.fotos.length === 0)) {
    const tono = asignacion.tonoPorPrenda[variante.prenda];
    const iguales = variantes.filter(
      (otra) => claveDeColor(asignacion.tonoPorPrenda[otra.prenda]) === claveDeColor(tono),
    );
    if (iguales.length > 1) {
      return { tipo: 'REPETIDA', prenda: variante.prenda, tono };
    }
  }
  return null;
}

/** «Café», «CAFE» y «café» son el mismo color. */
export function claveDeColor(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

/** La foto como la lee la propuesta: su color sugerido y los que se vieron si retrata varios. */
export interface FotoParaProponer {
  readonly mensajeId: string;
  readonly tonoSugerido: string | null;
  readonly tonosVistos?: readonly string[];
}

/**
 * Las prendas que propone la lectura de fotos (10 de octubre de 2026): cada foto con un color
 * sugerido que está en la paleta va a la prenda de ese color, y las fotos del mismo color son la
 * misma prenda —en un anuncio de Violeta, la blusa negra de frente y de espalda—. La que no trae
 * sugerencia, o trae un color que la paleta no tiene, vale para todas.
 *
 * <p>Y desde el 12 de octubre de 2026, los tonos de la foto que retrata varios —y los que nombra el
 * texto— que ninguna foto de un solo tono cubre se proponen como prendas **sin foto propia**: es
 * el bodi en seis colores de una sola foto, que antes se aprobaba sin un solo color que elegir.
 * Si la paleta no tiene ese nombre, la prenda llega sin color y con la pista del proveedor, para
 * que quien aprueba lo elija: no se inventa un color, pero tampoco se pierde un tono que se vende.
 *
 * <p>Es un punto de partida: quien aprueba lo corrige con los mismos selectores de siempre.
 *
 * @param paleta los nombres en español de los colores que se pueden elegir: el valor del atributo
 * @param tonosDelTexto los tonos que el proveedor escribió en el anuncio
 */
export function prendasSugeridas(
  fotos: readonly FotoParaProponer[],
  paleta: readonly string[],
  tonosDelTexto: readonly string[] = [],
): AsignacionDePrendas {
  const porClave = new Map(paleta.map((nombre) => [claveDeColor(nombre), nombre]));
  const prendaPorFoto: Record<string, number> = {};
  const tonoPorPrenda: Record<number, string> = {};
  const pistaPorPrenda: Record<number, string> = {};
  const prendaDeTono = new Map<string, number>();
  const cubiertos = new Set<string>();
  for (const foto of fotos) {
    const tono = foto.tonoSugerido ? porClave.get(claveDeColor(foto.tonoSugerido)) : undefined;
    if (!tono || !foto.tonoSugerido) {
      continue;
    }
    let prenda = prendaDeTono.get(tono);
    if (prenda === undefined) {
      prenda = prendaDeTono.size + 1;
      prendaDeTono.set(tono, prenda);
      tonoPorPrenda[prenda] = tono;
      pistaPorPrenda[prenda] = foto.tonoSugerido;
    }
    prendaPorFoto[foto.mensajeId] = prenda;
    cubiertos.add(claveDeColor(tono));
    cubiertos.add(claveDeColor(foto.tonoSugerido));
  }

  let asignacion: AsignacionDePrendas = {
    ...SIN_PRENDAS,
    prendaPorFoto,
    tonoPorPrenda,
    pistaPorPrenda,
  };
  const vistos = [...fotos.flatMap((foto) => foto.tonosVistos ?? []), ...tonosDelTexto];
  for (const visto of vistos) {
    const clave = claveDeColor(visto);
    if (!clave || cubiertos.has(clave)) {
      continue;
    }
    const tono = porClave.get(clave) ?? '';
    if (tono && cubiertos.has(claveDeColor(tono))) {
      continue;
    }
    cubiertos.add(clave);
    if (tono) {
      cubiertos.add(claveDeColor(tono));
    }
    asignacion = agregarPrendaAparte(asignacion, tono, visto.trim());
  }
  return asignacion;
}

/**
 * Las tallas de cada prenda que el proveedor escribió en el anuncio —«Talla ML(cocoa)»—, para
 * llenar la tabla de color y talla. El tono se busca por el color de la prenda y por la pista del
 * proveedor, porque la paleta no tiene «cocoa» y quien aprueba la marca «Café». Solo las prendas
 * que el anuncio nombra; las demás vienen en todas.
 *
 * @param aprobadas las tallas del producto: las de la prenda se quedan entre ellas
 */
export function tallasSugeridasPorPrenda(
  asignacion: AsignacionDePrendas,
  tallasPorTono: readonly { readonly tono: string; readonly tallas: readonly string[] }[],
  aprobadas: readonly string[],
): AsignacionDePrendas {
  const tallasPorPrenda: Record<number, readonly string[]> = {};
  for (const prenda of prendasEnUso(asignacion)) {
    const nombres = [asignacion.tonoPorPrenda[prenda], asignacion.pistaPorPrenda[prenda]]
      .filter((nombre): nombre is string => !!nombre)
      .map(claveDeColor);
    const delTono = tallasPorTono.find((t) => nombres.includes(claveDeColor(t.tono)));
    if (!delTono) {
      continue;
    }
    const comunes = aprobadas.filter((talla) =>
      delTono.tallas.some((suya) => suya.toLowerCase() === talla.toLowerCase()),
    );
    if (comunes.length > 0) {
      tallasPorPrenda[prenda] = comunes;
    }
  }
  return { ...asignacion, tallasPorPrenda };
}

/** Las tallas en que viene una prenda, entre las del producto: sin decirlo, en todas. */
export function tallasDePrenda(
  asignacion: AsignacionDePrendas,
  prenda: number,
  aprobadas: readonly string[],
): string[] {
  const suyas = asignacion.tallasPorPrenda[prenda];
  if (!suyas) {
    return [...aprobadas];
  }
  return aprobadas.filter((talla) => suyas.some((s) => s.toLowerCase() === talla.toLowerCase()));
}

/** Marcar o desmarcar una casilla de la tabla de color y talla. */
export function marcarTallaDePrenda(
  asignacion: AsignacionDePrendas,
  prenda: number,
  talla: string,
  marcada: boolean,
  aprobadas: readonly string[],
): AsignacionDePrendas {
  const actuales = new Set(
    tallasDePrenda(asignacion, prenda, aprobadas).map((t) => t.toLowerCase()),
  );
  if (marcada) {
    actuales.add(talla.toLowerCase());
  } else {
    actuales.delete(talla.toLowerCase());
  }
  return {
    ...asignacion,
    tallasPorPrenda: {
      ...asignacion.tallasPorPrenda,
      [prenda]: aprobadas.filter((t) => actuales.has(t.toLowerCase())),
    },
  };
}

/**
 * Con las tallas por color, la primera prenda que se quedó sin ninguna: no tendría variante que
 * crear, y la API la rechaza.
 */
export function prendaSinTallas(
  asignacion: AsignacionDePrendas,
  elegidas: readonly string[],
  aprobadas: readonly string[],
): ProblemaDePrendas | null {
  for (const variante of variantesQueSeCrean(asignacion, elegidas)) {
    if (tallasDePrenda(asignacion, variante.prenda, aprobadas).length === 0) {
      return { tipo: 'SIN_TALLAS', prenda: variante.prenda, tono: variante.valor };
    }
  }
  return null;
}
