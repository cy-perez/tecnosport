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
 *
 * ## Por qué esto compara vectores de gravedad y no ángulos
 *
 * La primera versión restaba `beta` y `gamma` contra los de la toma de referencia. **Se midió con
 * un teléfono el 23 de septiembre de 2026 y no servía.** La pose de trabajo del asistente —el
 * aparato casi vertical, apuntando a un producto sobre una mesa— cae justo encima de la
 * singularidad de los ángulos de Euler: con `beta` sostenido a mano en 82,6° y una desviación real
 * de 1,14°, el `gamma` medido barría 54,9°, porque ahí `gamma` se acopla con la brújula y se
 * amplifica por `1/cos(beta)` —teórico 7,8×, medido 10,2×—. Sobre una vuelta completa a un
 * producto, ese nivel bloqueaba el obturador **el 92 % del tiempo**, y entre dos lecturas seguidas
 * —16 ms— llegó a ver un salto de `gamma` de 178,6°.
 *
 * Lo que sí es estable es **hacia dónde cae la gravedad vista desde el teléfono**. Sale de `beta`
 * y `gamma`, no tiene singularidad, y en ese mismo salto de 178,6° se movió 1,1°. Sobre la misma
 * vuelta, el bloqueo baja del 92 % al 44 %, que es el tiempo que el aparato estuvo de verdad
 * desnivelado. La medición está en `tools/analizar-nivel-360.mjs`, que reproduce las grabaciones
 * contra este archivo.
 *
 * **Lo que se pierde, y es a propósito**: la gravedad no ve el giro alrededor del eje vertical. Eso
 * es exactamente lo que se quiere —dar la vuelta al producto no es desnivelarse, y por eso la
 * brújula no entra aquí—, pero tiene un precio honesto: con el teléfono casi horizontal, apuntando
 * hacia abajo, parte del giro del encuadre deja de ser visible para el nivel. En la pose vertical
 * del asistente el giro se observa entero; en una toma cenital, no.
 */

/** Tolerancia por omisión, en grados de desviación total (`docs/10-captura-360.md`). */
export const TOLERANCIA_GRADOS = 3;

/**
 * Cuánto más hay que alejarse para **salir** de rango una vez dentro.
 *
 * Sin esto el obturador parpadea: sobre el umbral exacto, el temblor de la mano lo habilita y lo
 * deshabilita varias veces por segundo, y tocar el botón justo en un parpadeo no dispara. Medido
 * sobre la vuelta completa grabada: sin histéresis, 31,8 cambios por minuto; con 2°, 12,3.
 */
export const HISTERESIS_GRADOS = 2;

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

/**
 * Hacia dónde cae la gravedad, vista desde el teléfono. Unitario, en el marco del aparato: `x` a
 * la derecha de la pantalla, `y` hacia arriba, `z` saliendo de la pantalla hacia quien mira.
 *
 * Es lo que el nivelador compara, y lo que se guarda como referencia de un set.
 */
export interface Gravedad {
  readonly x: number;
  readonly y: number;
  readonly z: number;
}

export type EstadoDeNivel =
  /** No hay lectura: modo degradado, sin nivel y con aviso permanente. */
  | 'SIN_SENSOR'
  /** Lejos del objetivo. El obturador se deshabilita y se dice por qué, en texto. */
  | 'FUERA_DE_RANGO'
  /** A punto, pero todavía no. */
  | 'CERCA'
  /** Dentro de la tolerancia. */
  | 'EN_RANGO';

export interface Nivel {
  readonly estado: EstadoDeNivel;
  /** Grados que separan la inclinación actual de la de referencia. Cero sin lectura. */
  readonly desviacion: number;
  /**
   * Grados que hay que inclinar el teléfono, con signo: positivo es hacia atrás. Es lo que
   * necesita el texto de "inclina el teléfono hacia adelante", que nunca puede ser solo un color.
   */
  readonly inclinar: number;
  /** Grados que hay que girar sobre el eje de la pantalla, con signo: positivo es a la derecha. */
  readonly girar: number;
  /** Cuál de los dos está más lejos, para no dar dos instrucciones a la vez. `null` sin lectura. */
  readonly ejeDominante: 'INCLINAR' | 'GIRAR' | null;
  /** **Sin sensor es `true`**: el modo degradado no bloquea la captura. */
  readonly puedeDisparar: boolean;
}

export interface OpcionesDeNivel {
  /** Grados de tolerancia. Por omisión, `TOLERANCIA_GRADOS`. */
  readonly toleranciaGrados?: number;
  /** Grados de más para salir de rango una vez dentro. Por omisión, `HISTERESIS_GRADOS`. */
  readonly histeresisGrados?: number;
}

const grados = (radianes: number): number => (radianes * 180) / Math.PI;
const radianes = (grados: number): number => (grados * Math.PI) / 180;
const acotar = (valor: number): number => Math.min(1, Math.max(-1, valor));

/**
 * La gravedad vista desde el teléfono, a partir de la lectura del sensor.
 *
 * Sale de la tercera fila de `Rz(alpha)·Rx(beta)·Ry(gamma)`, que **no depende de `alpha`**: girar
 * alrededor del eje vertical del mundo deja quieto al propio vector vertical. De ahí que la
 * brújula no haga falta y que dar la vuelta al producto no cuente como desnivel.
 *
 * **Una lectura sin datos devuelve `null`**: si el sensor se cae a mitad de sesión eso tiene que
 * verse, y no arrastrarse el último valor bueno.
 */
export function gravedad(lectura: LecturaDeOrientacion): Gravedad | null {
  const { beta, gamma } = lectura;
  if (beta === null || gamma === null || !Number.isFinite(beta) || !Number.isFinite(gamma)) {
    return null;
  }

  const cb = Math.cos(radianes(beta));
  const sb = Math.sin(radianes(beta));
  const cg = Math.cos(radianes(gamma));
  const sg = Math.sin(radianes(gamma));
  return { x: cb * sg, y: -sb, z: -cb * cg };
}

/**
 * Los grados que separan dos direcciones de gravedad.
 *
 * Con `atan2` del tamaño del producto cruzado contra el producto punto, y no con un `acos` del
 * producto punto a secas: para ángulos pequeños —que son todos los que le importan a un nivel de
 * 3° de tolerancia— el coseno vale casi 1 y su arco pierde casi toda la precisión, mientras que
 * esta forma la conserva en todo el rango.
 */
export function anguloEntre(uno: Gravedad, otro: Gravedad): number {
  const punto = uno.x * otro.x + uno.y * otro.y + uno.z * otro.z;
  const cruz = [
    uno.y * otro.z - uno.z * otro.y,
    uno.z * otro.x - uno.x * otro.z,
    uno.x * otro.y - uno.y * otro.x,
  ];
  const largo = Math.sqrt(cruz[0] * cruz[0] + cruz[1] * cruz[1] + cruz[2] * cruz[2]);
  return grados(Math.atan2(largo, punto));
}

/**
 * Media exponencial sobre la **dirección** de la gravedad: el vector anterior se mueve una
 * fracción hacia el nuevo y se vuelve a normalizar. La primera lectura pasa tal cual, para que el
 * indicador no arranque arrastrándose desde un valor inventado.
 *
 * Se suaviza el vector y no los ángulos, y esa es la diferencia que importa: suavizar `gamma`
 * cerca de la vertical es suavizar saltos de 180° que no corresponden a ningún movimiento, y el
 * promedio tarda decenas de lecturas en recuperarse de cada uno.
 *
 * **Una lectura sin datos devuelve `null` y no arrastra la anterior**, por lo mismo de siempre:
 * quedarse mostrando el último valor bueno dejaría el obturador habilitado con el teléfono
 * torcido, que es peor que no tener nivel.
 */
export function suavizar(
  anterior: Gravedad | null,
  cruda: LecturaDeOrientacion,
  factor: number = FACTOR_DE_SUAVIZADO,
): Gravedad | null {
  const nueva = gravedad(cruda);
  if (nueva === null) {
    return null;
  }
  if (anterior === null) {
    return nueva;
  }

  const peso = Math.min(Math.max(factor, 0), 1);
  const x = anterior.x + (nueva.x - anterior.x) * peso;
  const y = anterior.y + (nueva.y - anterior.y) * peso;
  const z = anterior.z + (nueva.z - anterior.z) * peso;
  const largo = Math.sqrt(x * x + y * y + z * z);

  // Dos direcciones opuestas se cancelan al promediarlas. No puede pasar con un teléfono real
  // —serían 180° entre dos lecturas seguidas— pero devolver un vector nulo sería peor que
  // quedarse con la lectura nueva.
  return largo < 1e-6 ? nueva : { x: x / largo, y: y / largo, z: z / largo };
}

/**
 * Los tres estados del nivel, más el cuarto que no es un estado del nivel sino su ausencia.
 *
 * El objetivo se recibe: **no se supone que "nivelado" sea una postura concreta**. Un teléfono
 * apuntando a un producto sobre una mesa no está plano, y fijar una postura de fábrica haría que
 * el nivel nunca se pusiera verde en el montaje real. Quién decide el objetivo —una calibración al
 * empezar, o la inclinación de la primera toma aceptada— es del flujo, no de esta función; con
 * `objetivo` en `null`, la lectura actual **es** la referencia y por eso no hay desviación.
 *
 * `estadoAnterior` es lo único que esta función necesita del pasado, y solo para la histéresis:
 * estando ya en rango, la tolerancia se ensancha. Sin eso el obturador parpadea sobre el umbral.
 */
export function evaluarNivel(
  actual: Gravedad | null,
  objetivo: Gravedad | null,
  estadoAnterior: EstadoDeNivel | null = null,
  opciones: OpcionesDeNivel = {},
): Nivel {
  if (actual === null) {
    return {
      estado: 'SIN_SENSOR',
      desviacion: 0,
      inclinar: 0,
      girar: 0,
      ejeDominante: null,
      puedeDisparar: true,
    };
  }

  const referencia = objetivo ?? actual;
  const tolerancia = Math.abs(opciones.toleranciaGrados ?? TOLERANCIA_GRADOS);
  const histeresis = Math.abs(opciones.histeresisGrados ?? HISTERESIS_GRADOS);
  const paraSalir = estadoAnterior === 'EN_RANGO' ? tolerancia + histeresis : tolerancia;

  const desviacion = anguloEntre(referencia, actual);

  // El producto cruzado de las dos gravedades es el eje del giro que lleva una a la otra, y su
  // tamaño el seno del ángulo. Proyectado sobre los ejes del aparato da las dos instrucciones, sin
  // pasar por ningún ángulo de Euler.
  const rx = referencia.y * actual.z - referencia.z * actual.y;
  const rz = referencia.x * actual.y - referencia.y * actual.x;

  const estado: EstadoDeNivel =
    desviacion <= paraSalir
      ? 'EN_RANGO'
      : desviacion <= tolerancia * FACTOR_DE_CERCANIA
        ? 'CERCA'
        : 'FUERA_DE_RANGO';

  const inclinar = grados(Math.asin(acotar(rx)));
  // El signo se invierte respecto al eje de inclinación: con el producto cruzado en este orden,
  // un `rz` negativo es el que pide girar a la derecha. Comprobado con casos de signo conocido.
  const girar = -grados(Math.asin(acotar(rz)));

  return {
    estado,
    desviacion,
    inclinar,
    girar,
    ejeDominante: Math.abs(inclinar) >= Math.abs(girar) ? 'INCLINAR' : 'GIRAR',
    puedeDisparar: estado === 'EN_RANGO',
  };
}

// ---------------------------------------------------------------------------------------------
// Qué se le dice a un lector de pantalla, y cada cuánto
// ---------------------------------------------------------------------------------------------

/**
 * Lo que el nivel tiene que decir, sin los grados. **Es la identidad del anuncio**: dos lecturas
 * que caen en la misma clave dicen lo mismo, aunque los grados hayan cambiado.
 *
 * Los grados quedan fuera a propósito, y es la mitad de la deuda 32 del plan. El indicador pinta
 * "Inclina el teléfono 7° hacia adelante", y ese número cambia con cada grado entero: sobre la
 * vuelta completa grabada, eso son **84,5 anuncios por minuto**. Con los grados fuera y el retardo
 * de abajo, quedan menos de cuatro.
 */
export type ClaveDeNivel =
  | 'sin_sensor'
  | 'fijando_referencia'
  | 'en_rango'
  | 'inclina_adelante'
  | 'inclina_atras'
  | 'gira_izquierda'
  | 'gira_derecha';

/**
 * Cuánto tiene que sostenerse una clave antes de anunciarla.
 *
 * Elegido midiendo, no a ojo. Sobre la vuelta completa a un producto, con la histéresis puesta:
 * sin retardo son 20,5 anuncios por minuto; con 600 ms bajan a 9,2 y lo anunciado deja de
 * coincidir con la realidad el 7 % del tiempo; con 1 s bajan a 2,6 con un 8 %. Más allá la región
 * se queda casi muda y el desajuste sube sin que el conteo mejore, que es pagar por nada.
 *
 * 600 ms es el punto donde el anuncio deja de ser ruido y todavía llega a tiempo de servir.
 */
export const RETARDO_DE_ANUNCIO_MS = 600;

/** Qué dice el nivel en este instante, sin grados. */
export function claveDeNivel(nivel: Nivel, fijandoReferencia = false): ClaveDeNivel {
  if (nivel.estado === 'SIN_SENSOR') {
    return 'sin_sensor';
  }
  if (fijandoReferencia) {
    return 'fijando_referencia';
  }
  if (nivel.estado === 'EN_RANGO') {
    return 'en_rango';
  }

  const gira = nivel.ejeDominante === 'GIRAR';
  const valor = gira ? nivel.girar : nivel.inclinar;
  if (gira) {
    return valor > 0 ? 'gira_derecha' : 'gira_izquierda';
  }
  return valor > 0 ? 'inclina_atras' : 'inclina_adelante';
}

/**
 * El anuncio que un lector de pantalla oye, y el que está esperando su turno.
 *
 * `clave` es lo dicho; `candidata` lo que cambió hace poco y todavía no se ha sostenido lo
 * suficiente; `desde` cuándo apareció esa candidata.
 */
export interface AnuncioDeNivel {
  readonly clave: ClaveDeNivel | null;
  /**
   * Los grados **del instante en que la clave se asentó**, no los de ahora.
   *
   * Así el anuncio lleva el número —que a quien no ve la pantalla le sirve igual que a quien la
   * ve— sin que el número sea lo que dispara el anuncio. Es la distinción que hace falta: los
   * grados cambian sesenta veces por segundo, la clave no.
   */
  readonly grados: number;
  readonly candidata: ClaveDeNivel | null;
  readonly desde: number;
}

export const ANUNCIO_EN_BLANCO: AnuncioDeNivel = {
  clave: null,
  grados: 0,
  candidata: null,
  desde: 0,
};

/**
 * Avanza el anuncio con una lectura nueva: una clave solo se anuncia cuando lleva `retardoMs`
 * sosteniéndose.
 *
 * **Sin temporizador, a propósito.** El reloj lo trae cada lectura del sensor —sesenta por
 * segundo—, así que no hace falta agendar nada: el estado no puede cambiar sin que llegue una
 * lectura, y si dejan de llegar tampoco hay nada nuevo que anunciar. Eso deja esta función pura y
 * la prueba sin relojes falsos.
 *
 * La primera clave se anuncia de inmediato: hacer esperar al primer mensaje de la pantalla sería
 * dejar en silencio a quien acaba de entrar.
 */
export function asentarAnuncio(
  anterior: AnuncioDeNivel,
  clave: ClaveDeNivel,
  grados: number,
  ahora: number,
  retardoMs: number = RETARDO_DE_ANUNCIO_MS,
): AnuncioDeNivel {
  const sostenida = anterior.candidata === clave;
  const candidata = clave;
  const desde = sostenida ? anterior.desde : ahora;

  if (clave === anterior.clave) {
    // Lo mismo que ya se dijo: no se vuelve a anunciar, y el número tampoco se refresca — hacerlo
    // cambiaría el contenido de la región viva y la haría hablar otra vez.
    return { clave: anterior.clave, grados: anterior.grados, candidata, desde };
  }
  if (anterior.clave === null || ahora - desde >= retardoMs) {
    return { clave, grados, candidata, desde };
  }
  return { clave: anterior.clave, grados: anterior.grados, candidata, desde };
}
