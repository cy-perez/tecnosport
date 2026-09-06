/**
 * Funciones puras del nivelador digital del asistente de captura (`docs/10-captura-360.md`,
 * "Nivelador digital"). Sin DOM y sin sensores: reciben una lectura de orientación y devuelven
 * qué hay que mostrar y si el obturador se habilita.
 *
 * Quien escuche `DeviceOrientationEvent` —detrás del guardia de plataforma, la verificación de
 * disponibilidad y el permiso pedido desde un gesto, como exige `apps/web/CLAUDE.md`— entrega
 * aquí lo que leyó. Este módulo no sabe que existe un sensor: sabe que a veces hay lectura y a
 * veces no, y **cuando no la hay el obturador se habilita igual**, que es el modo degradado que
 * pide el documento. Un flujo que se bloquea sin sensor no se puede usar en medio teléfono del
 * mercado.
 */

/** Tolerancia por omisión, en grados, en `beta` y en `gamma` (`docs/10-captura-360.md`). */
export const TOLERANCIA_GRADOS = 3;

/**
 * Hasta cuántas veces la tolerancia se considera "cerca", el estado intermedio de los tres. Es la
 * distancia a la que ya vale la pena decirle a alguien que está a punto, no un dato de negocio.
 */
const FACTOR_DE_CERCANIA = 2;

/**
 * Peso de la lectura nueva en el suavizado. Bajo a propósito: el dato crudo del sensor tiembla y
 * un indicador nervioso es inutilizable. Más bajo es más estable y más lento en reaccionar.
 */
export const FACTOR_DE_SUAVIZADO = 0.2;

/**
 * Lo que entrega `DeviceOrientationEvent`: los dos ejes que le importan al nivel, y cualquiera de
 * los dos puede venir vacío. `alpha` (la brújula) no se usa — girar alrededor del producto es
 * justamente lo que se quiere.
 */
export interface LecturaDeOrientacion {
  readonly beta: number | null;
  readonly gamma: number | null;
}

/** Una lectura completa, ya utilizable: adelante/atrás en `beta`, izquierda/derecha en `gamma`. */
export interface Inclinacion {
  readonly beta: number;
  readonly gamma: number;
}

export type EstadoDeNivel =
  /** No hay lectura: modo degradado, sin nivel y con aviso permanente. */
  | 'SIN_SENSOR'
  /** Lejos del objetivo. El obturador se deshabilita y se dice por qué, en texto. */
  | 'FUERA_DE_RANGO'
  /** A punto, pero todavía no. */
  | 'CERCA'
  /** Dentro de la tolerancia en los dos ejes. */
  | 'EN_RANGO';

export interface Nivel {
  readonly estado: EstadoDeNivel;
  /**
   * Grados que le faltan a la lectura para llegar al objetivo, con signo: positivo significa que
   * hay que aumentar ese eje. Es lo que necesita el texto de "inclina el teléfono hacia adelante",
   * que nunca puede ser solo un color. Cero cuando no hay lectura.
   */
  readonly desviacionBeta: number;
  readonly desviacionGamma: number;
  /** El eje que más lejos está, para no dar dos instrucciones a la vez. `null` sin lectura. */
  readonly ejeDominante: 'BETA' | 'GAMMA' | null;
  /** **Sin sensor es `true`**: el modo degradado no bloquea la captura. */
  readonly puedeDisparar: boolean;
}

export interface OpcionesDeNivel {
  /** Grados de tolerancia en cada eje. Por omisión, `TOLERANCIA_GRADOS`. */
  readonly toleranciaGrados?: number;
}

/** Lleva un ángulo al rango `(-180, 180]`, que es donde las diferencias son el arco corto. */
export function normalizarAngulo(grados: number): number {
  if (!Number.isFinite(grados)) {
    return 0;
  }
  const resto = ((grados % 360) + 360) % 360;
  return resto > 180 ? resto - 360 : resto;
}

/**
 * Cuántos grados hay que moverse **desde** un ángulo **hasta** otro, por el camino corto. Sin
 * esto, pasar de 179 a -179 se leería como un giro de 358 grados en vez de los 2 reales, y el
 * nivel diría "fuera de rango" con el teléfono quieto.
 */
export function diferenciaAngular(desde: number, hasta: number): number {
  return normalizarAngulo(hasta - desde);
}

/**
 * Media exponencial sobre el arco corto: el valor anterior se mueve una fracción hacia la lectura
 * nueva. La primera lectura pasa tal cual, para que el indicador no arranque arrastrándose desde
 * un valor inventado.
 *
 * **Una lectura sin datos devuelve `null` y no arrastra la anterior.** Si el sensor se cae a
 * mitad de sesión, eso tiene que verse: quedarse mostrando el último valor bueno dejaría el
 * obturador habilitado con el teléfono torcido, que es peor que no tener nivel.
 */
export function suavizar(
  anterior: Inclinacion | null,
  cruda: LecturaDeOrientacion,
  factor: number = FACTOR_DE_SUAVIZADO,
): Inclinacion | null {
  if (
    cruda.beta === null ||
    cruda.gamma === null ||
    !Number.isFinite(cruda.beta) ||
    !Number.isFinite(cruda.gamma)
  ) {
    return null;
  }

  const beta = normalizarAngulo(cruda.beta);
  const gamma = normalizarAngulo(cruda.gamma);
  if (anterior === null) {
    return { beta, gamma };
  }

  const peso = Math.min(Math.max(factor, 0), 1);
  return {
    beta: normalizarAngulo(anterior.beta + diferenciaAngular(anterior.beta, beta) * peso),
    gamma: normalizarAngulo(anterior.gamma + diferenciaAngular(anterior.gamma, gamma) * peso),
  };
}

/**
 * Los tres estados del nivel, más el cuarto que no es un estado del nivel sino su ausencia.
 *
 * El objetivo se recibe: **no se supone que "nivelado" sea cero en los dos ejes**. Un teléfono
 * apuntando a un producto sobre una mesa no está plano, y fijar el cero como objetivo haría que
 * el nivel nunca se pusiera verde en el montaje real. Quién decide el objetivo —una calibración
 * al empezar, o la inclinación de la primera toma aceptada— es del flujo, no de esta función.
 */
export function evaluarNivel(
  inclinacion: Inclinacion | null,
  objetivo: Inclinacion,
  opciones: OpcionesDeNivel = {},
): Nivel {
  if (inclinacion === null) {
    return {
      estado: 'SIN_SENSOR',
      desviacionBeta: 0,
      desviacionGamma: 0,
      ejeDominante: null,
      puedeDisparar: true,
    };
  }

  const desviacionBeta = diferenciaAngular(inclinacion.beta, objetivo.beta);
  const desviacionGamma = diferenciaAngular(inclinacion.gamma, objetivo.gamma);
  const tolerancia = Math.abs(opciones.toleranciaGrados ?? TOLERANCIA_GRADOS);
  const peor = Math.max(Math.abs(desviacionBeta), Math.abs(desviacionGamma));

  const estado: EstadoDeNivel =
    peor <= tolerancia
      ? 'EN_RANGO'
      : peor <= tolerancia * FACTOR_DE_CERCANIA
        ? 'CERCA'
        : 'FUERA_DE_RANGO';

  return {
    estado,
    desviacionBeta,
    desviacionGamma,
    ejeDominante: Math.abs(desviacionBeta) >= Math.abs(desviacionGamma) ? 'BETA' : 'GAMMA',
    puedeDisparar: estado === 'EN_RANGO',
  };
}
