import {
  diferenciaAngular,
  evaluarNivel,
  normalizarAngulo,
  suavizar,
  type Inclinacion,
} from './nivel-360';

/** El montaje real: el teléfono vertical frente a un producto sobre la mesa, no plano. */
const OBJETIVO: Inclinacion = { beta: 90, gamma: 0 };

describe('normalizarAngulo', () => {
  it('deja como está un ángulo que ya está en rango', () => {
    expect(normalizarAngulo(90)).toBe(90);
    expect(normalizarAngulo(-45)).toBe(-45);
  });

  it('lleva lo que se pasa de media vuelta al otro lado', () => {
    expect(normalizarAngulo(190)).toBe(-170);
    expect(normalizarAngulo(-190)).toBe(170);
  });

  it('media vuelta es siempre 180, venga por donde venga', () => {
    expect(normalizarAngulo(180)).toBe(180);
    expect(normalizarAngulo(-180)).toBe(180);
  });

  it('aguanta varias vueltas completas', () => {
    expect(normalizarAngulo(540)).toBe(180);
    expect(normalizarAngulo(720)).toBe(0);
  });

  it('un valor que no es un número no envenena el resto del cálculo', () => {
    expect(normalizarAngulo(Number.NaN)).toBe(0);
  });
});

describe('diferenciaAngular', () => {
  it('cuenta los grados que faltan, con signo', () => {
    expect(diferenciaAngular(90, 93)).toBe(3);
    expect(diferenciaAngular(93, 90)).toBe(-3);
  });

  it('cruzando media vuelta toma el arco corto, no la vuelta larga', () => {
    // Sin esto, el teléfono quieto en el límite marcaría 358 grados de desvío.
    expect(diferenciaAngular(179, -179)).toBe(2);
    expect(diferenciaAngular(-179, 179)).toBe(-2);
  });
});

describe('suavizar', () => {
  it('la primera lectura pasa tal cual: el indicador no arranca desde un valor inventado', () => {
    expect(suavizar(null, { beta: 88, gamma: -2 })).toEqual({ beta: 88, gamma: -2 });
  });

  it('una lectura nueva mueve el valor una fracción, no de golpe', () => {
    const suavizada = suavizar({ beta: 90, gamma: 0 }, { beta: 100, gamma: 10 }, 0.2);

    expect(suavizada?.beta).toBeCloseTo(92);
    expect(suavizada?.gamma).toBeCloseTo(2);
  });

  it('converge hacia la lectura si se sostiene', () => {
    let suavizada: Inclinacion | null = { beta: 90, gamma: 0 };
    for (let i = 0; i < 40; i++) {
      suavizada = suavizar(suavizada, { beta: 100, gamma: 0 }, 0.2);
    }

    expect(suavizada?.beta).toBeCloseTo(100, 1);
  });

  it('cruzando media vuelta se mueve por el arco corto', () => {
    const suavizada = suavizar({ beta: 179, gamma: 0 }, { beta: -179, gamma: 0 }, 0.5);

    expect(suavizada?.beta).toBe(180);
  });

  it('una lectura sin datos no se arrastra: perder el sensor tiene que verse', () => {
    // Quedarse con el último valor bueno dejaría el obturador habilitado con el teléfono torcido.
    expect(suavizar({ beta: 90, gamma: 0 }, { beta: null, gamma: 0 })).toBeNull();
    expect(suavizar({ beta: 90, gamma: 0 }, { beta: 90, gamma: null })).toBeNull();
    expect(suavizar({ beta: 90, gamma: 0 }, { beta: Number.NaN, gamma: 0 })).toBeNull();
  });

  it('un factor fuera de rango se acota en vez de disparar el valor', () => {
    expect(suavizar({ beta: 90, gamma: 0 }, { beta: 100, gamma: 0 }, 5)?.beta).toBeCloseTo(100);
    expect(suavizar({ beta: 90, gamma: 0 }, { beta: 100, gamma: 0 }, -1)?.beta).toBeCloseTo(90);
  });
});

describe('evaluarNivel', () => {
  it('sin lectura no hay nivel, y aun así se puede disparar', () => {
    // Modo degradado: un flujo que se bloquea sin sensor no se puede usar en medio teléfono.
    const nivel = evaluarNivel(null, OBJETIVO);

    expect(nivel.estado).toBe('SIN_SENSOR');
    expect(nivel.puedeDisparar).toBe(true);
    expect(nivel.ejeDominante).toBeNull();
  });

  it('dentro de la tolerancia, en rango y con el obturador habilitado', () => {
    const nivel = evaluarNivel({ beta: 91, gamma: -1 }, OBJETIVO);

    expect(nivel.estado).toBe('EN_RANGO');
    expect(nivel.puedeDisparar).toBe(true);
  });

  it('el límite exacto de la tolerancia todavía cuenta como en rango', () => {
    expect(evaluarNivel({ beta: 93, gamma: 0 }, OBJETIVO).estado).toBe('EN_RANGO');
  });

  it('pasada la tolerancia está cerca, pero no dispara', () => {
    const nivel = evaluarNivel({ beta: 95, gamma: 0 }, OBJETIVO);

    expect(nivel.estado).toBe('CERCA');
    expect(nivel.puedeDisparar).toBe(false);
  });

  it('lejos está fuera de rango y tampoco dispara', () => {
    const nivel = evaluarNivel({ beta: 110, gamma: 0 }, OBJETIVO);

    expect(nivel.estado).toBe('FUERA_DE_RANGO');
    expect(nivel.puedeDisparar).toBe(false);
  });

  it('los dos ejes cuentan: gamma torcido saca de rango aunque beta esté perfecto', () => {
    const nivel = evaluarNivel({ beta: 90, gamma: 12 }, OBJETIVO);

    expect(nivel.estado).toBe('FUERA_DE_RANGO');
    expect(nivel.ejeDominante).toBe('GAMMA');
  });

  it('la desviación va con signo, que es lo que dice hacia dónde corregir', () => {
    const inclinadoDeMenos = evaluarNivel({ beta: 80, gamma: 0 }, OBJETIVO);
    const inclinadoDeMas = evaluarNivel({ beta: 100, gamma: 0 }, OBJETIVO);

    expect(inclinadoDeMenos.desviacionBeta).toBeCloseTo(10);
    expect(inclinadoDeMas.desviacionBeta).toBeCloseTo(-10);
  });

  it('el objetivo no es cero: el teléfono apunta al producto, no al techo', () => {
    // Con el cero como objetivo fijo, el nivel nunca se pondría verde en el montaje real.
    expect(evaluarNivel({ beta: 90, gamma: 0 }, { beta: 90, gamma: 0 }).estado).toBe('EN_RANGO');
    expect(evaluarNivel({ beta: 0, gamma: 0 }, { beta: 90, gamma: 0 }).estado).toBe(
      'FUERA_DE_RANGO',
    );
  });

  it('un objetivo cerca de media vuelta no saca de rango al teléfono quieto', () => {
    expect(evaluarNivel({ beta: -179, gamma: 0 }, { beta: 179, gamma: 0 }).estado).toBe('EN_RANGO');
  });

  it('la tolerancia es configurable', () => {
    const nivel = evaluarNivel({ beta: 95, gamma: 0 }, OBJETIVO, { toleranciaGrados: 6 });

    expect(nivel.estado).toBe('EN_RANGO');
  });
});

describe('el temblor de la mano', () => {
  /** Un pulso normal sobre un objetivo de 90 grados: nadie sostiene un teléfono quieto. */
  const TEMBLOR = [0, 2.6, -3.4, 3.8, -2.2, 3.1, -3.6, 2.9];

  it('el dato crudo hace saltar el indicador de estado', () => {
    const estados = TEMBLOR.map(
      (ruido) => evaluarNivel({ beta: 90 + ruido, gamma: 0 }, OBJETIVO).estado,
    );

    expect(new Set(estados).size).toBeGreaterThan(1);
  });

  it('suavizado se queda quieto en rango, que es lo único usable', () => {
    let suavizada: Inclinacion | null = null;
    const estados: string[] = [];

    for (const ruido of TEMBLOR) {
      suavizada = suavizar(suavizada, { beta: 90 + ruido, gamma: 0 });
      estados.push(evaluarNivel(suavizada, OBJETIVO).estado);
    }

    expect(estados).toEqual(Array(TEMBLOR.length).fill('EN_RANGO'));
  });
});
