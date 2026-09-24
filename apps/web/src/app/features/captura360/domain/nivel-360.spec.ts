import {
  anguloEntre,
  ANUNCIO_EN_BLANCO,
  asentarAnuncio,
  claveDeNivel,
  evaluarNivel,
  gravedad,
  RETARDO_DE_ANUNCIO_MS,
  suavizar,
  TOLERANCIA_GRADOS,
  type EstadoDeNivel,
  type Gravedad,
} from './nivel-360';
import { LECTURAS_DE_TELEFONO_QUIETO } from './lecturas-de-telefono-quieto';

/** El montaje real: el teléfono vertical frente a un producto sobre la mesa, no plano. */
const OBJETIVO = gravedad({ beta: 90, gamma: 0 }) as Gravedad;

const desde = (beta: number, gamma: number): Gravedad => gravedad({ beta, gamma }) as Gravedad;

/**
 * La gravedad de un teléfono vertical al que se le da un cabeceo y un balanceo, en grados.
 *
 * Hace falta porque **cerca de la vertical el par `beta`/`gamma` no sabe expresar un balanceo**:
 * a `beta` de 90° la gravedad vale `(0, -1, 0)` sea cual sea `gamma`, porque ahí girar sobre el eje
 * largo del teléfono es girar sobre la vertical, o sea la brújula. Esa es justamente la
 * singularidad que hundía al nivelador anterior.
 */
function verticalCon(cabeceo: number, balanceo: number): Gravedad {
  const p = (cabeceo * Math.PI) / 180;
  const r = (balanceo * Math.PI) / 180;
  return {
    x: Math.cos(p) * Math.sin(r),
    y: -Math.cos(p) * Math.cos(r),
    z: -Math.sin(p),
  };
}

describe('gravedad', () => {
  it('es un vector unitario, venga de donde venga la lectura', () => {
    for (const [beta, gamma] of [
      [0, 0],
      [90, 0],
      [82.6, 24],
      [-45, 170],
    ]) {
      const g = desde(beta, gamma);
      expect(Math.sqrt(g.x * g.x + g.y * g.y + g.z * g.z)).toBeCloseTo(1);
    }
  });

  it('con el teléfono vertical la gravedad cae por la pantalla hacia abajo', () => {
    expect(desde(90, 0).y).toBeCloseTo(-1);
  });

  it('una lectura sin datos no se inventa una dirección', () => {
    expect(gravedad({ beta: null, gamma: 0 })).toBeNull();
    expect(gravedad({ beta: 90, gamma: null })).toBeNull();
    expect(gravedad({ beta: Number.NaN, gamma: 0 })).toBeNull();
  });
});

describe('anguloEntre', () => {
  it('cuenta los grados que separan dos direcciones', () => {
    expect(anguloEntre(desde(90, 0), desde(80, 0))).toBeCloseTo(10);
    expect(anguloEntre(desde(90, 0), desde(90, 0))).toBeCloseTo(0);
  });

  it('nunca devuelve un NaN por redondeo, ni con el mismo vector dos veces', () => {
    const g = desde(82.6, 24);
    expect(Number.isNaN(anguloEntre(g, g))).toBe(false);
  });
});

describe('suavizar', () => {
  it('la primera lectura pasa tal cual: el indicador no arranca desde un valor inventado', () => {
    expect(suavizar(null, { beta: 88, gamma: -2 })).toEqual(desde(88, -2));
  });

  it('una lectura nueva mueve la dirección una fracción, no de golpe', () => {
    const suavizada = suavizar(desde(90, 0), { beta: 100, gamma: 0 }, 0.2) as Gravedad;

    expect(anguloEntre(desde(90, 0), suavizada)).toBeCloseTo(2, 0);
  });

  it('converge hacia la lectura si se sostiene', () => {
    let suavizada: Gravedad | null = desde(90, 0);
    for (let i = 0; i < 40; i++) {
      suavizada = suavizar(suavizada, { beta: 100, gamma: 0 }, 0.2);
    }

    expect(anguloEntre(suavizada as Gravedad, desde(100, 0))).toBeLessThan(0.1);
  });

  it('una lectura sin datos no se arrastra: perder el sensor tiene que verse', () => {
    // Quedarse con el último valor bueno dejaría el obturador habilitado con el teléfono torcido.
    expect(suavizar(desde(90, 0), { beta: null, gamma: 0 })).toBeNull();
    expect(suavizar(desde(90, 0), { beta: 90, gamma: null })).toBeNull();
    expect(suavizar(desde(90, 0), { beta: Number.NaN, gamma: 0 })).toBeNull();
  });

  it('un factor fuera de rango se acota en vez de disparar el valor', () => {
    const rapido = suavizar(desde(90, 0), { beta: 100, gamma: 0 }, 5) as Gravedad;
    const quieto = suavizar(desde(90, 0), { beta: 100, gamma: 0 }, -1) as Gravedad;

    expect(anguloEntre(rapido, desde(100, 0))).toBeCloseTo(0);
    expect(anguloEntre(quieto, desde(90, 0))).toBeCloseTo(0);
  });

  it('sigue devolviendo un unitario después de suavizar', () => {
    const g = suavizar(desde(90, 0), { beta: 60, gamma: 30 }, 0.5) as Gravedad;

    expect(Math.sqrt(g.x * g.x + g.y * g.y + g.z * g.z)).toBeCloseTo(1);
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

  it('sin referencia todavía, la lectura actual es la referencia', () => {
    // Es la primera toma del set: la que fija la inclinación de todas las demás.
    const nivel = evaluarNivel(desde(82, 24), null);

    expect(nivel.estado).toBe('EN_RANGO');
    expect(nivel.desviacion).toBeCloseTo(0);
  });

  it('dentro de la tolerancia, en rango y con el obturador habilitado', () => {
    const nivel = evaluarNivel(desde(91, 0), OBJETIVO);

    expect(nivel.estado).toBe('EN_RANGO');
    expect(nivel.puedeDisparar).toBe(true);
  });

  it('justo dentro de la tolerancia está en rango, y justo fuera no', () => {
    // El límite exacto no se afirma a propósito: la desviación sale de un cálculo en coma
    // flotante, así que "exactamente 3°" no existe — y ninguna mano sostiene un teléfono ahí.
    expect(evaluarNivel(desde(92.9, 0), OBJETIVO).estado).toBe('EN_RANGO');
    expect(evaluarNivel(desde(93.1, 0), OBJETIVO).estado).toBe('CERCA');
  });

  it('pasada la tolerancia está cerca, pero no dispara', () => {
    const nivel = evaluarNivel(desde(95, 0), OBJETIVO);

    expect(nivel.estado).toBe('CERCA');
    expect(nivel.puedeDisparar).toBe(false);
  });

  it('lejos está fuera de rango y tampoco dispara', () => {
    const nivel = evaluarNivel(desde(110, 0), OBJETIVO);

    expect(nivel.estado).toBe('FUERA_DE_RANGO');
    expect(nivel.puedeDisparar).toBe(false);
  });

  it('el balanceo también saca de rango, y por su propio eje', () => {
    const nivel = evaluarNivel(verticalCon(0, 12), OBJETIVO);

    expect(nivel.estado).toBe('FUERA_DE_RANGO');
    expect(nivel.ejeDominante).toBe('GIRAR');
    expect(nivel.desviacion).toBeCloseTo(12);
  });

  it('cabeceo y balanceo se reparten en los dos ejes, cada uno con su magnitud', () => {
    const nivel = evaluarNivel(verticalCon(8, 3), OBJETIVO);

    expect(Math.abs(nivel.inclinar)).toBeCloseTo(8, 0);
    expect(Math.abs(nivel.girar)).toBeCloseTo(3, 0);
    expect(nivel.ejeDominante).toBe('INCLINAR');
  });

  it('la corrección va con signo, que es lo que dice hacia dónde mover el teléfono', () => {
    expect(evaluarNivel(desde(80, 0), OBJETIVO).inclinar).toBeCloseTo(10);
    expect(evaluarNivel(desde(100, 0), OBJETIVO).inclinar).toBeCloseTo(-10);
    expect(evaluarNivel(verticalCon(0, 10), OBJETIVO).girar).toBeLessThan(0);
    expect(evaluarNivel(verticalCon(0, -10), OBJETIVO).girar).toBeGreaterThan(0);
  });

  it('el objetivo no es una postura de fábrica: el teléfono apunta al producto', () => {
    expect(evaluarNivel(desde(90, 0), OBJETIVO).estado).toBe('EN_RANGO');
    expect(evaluarNivel(desde(0, 0), OBJETIVO).estado).toBe('FUERA_DE_RANGO');
  });

  it('la tolerancia es configurable', () => {
    expect(evaluarNivel(desde(95, 0), OBJETIVO, null, { toleranciaGrados: 6 }).estado).toBe(
      'EN_RANGO',
    );
  });
});

describe('la histéresis', () => {
  it('estando ya en rango, aguanta un poco más antes de soltar el obturador', () => {
    const justoPasado = desde(94.5, 0); // 4,5 grados: fuera de los 3 de tolerancia

    expect(evaluarNivel(justoPasado, OBJETIVO, 'CERCA').estado).toBe('CERCA');
    expect(evaluarNivel(justoPasado, OBJETIVO, 'EN_RANGO').estado).toBe('EN_RANGO');
  });

  it('no aguanta indefinidamente: pasada la histéresis, suelta igual', () => {
    expect(evaluarNivel(desde(96, 0), OBJETIVO, 'EN_RANGO').estado).not.toBe('EN_RANGO');
  });

  it('para entrar en rango exige la tolerancia entera, no la ensanchada', () => {
    // Si no, entrar sería tan fácil como salir y el umbral no serviría de nada.
    expect(evaluarNivel(desde(94, 0), OBJETIVO, 'FUERA_DE_RANGO').estado).not.toBe('EN_RANGO');
  });
});

/**
 * La prueba que justifica todo el rediseño. Los datos son de un teléfono de verdad sostenido
 * quieto con la mano (`lecturas-de-telefono-quieto.ts`), y se reproducen tal como los recibiría el
 * asistente: lectura a lectura, suavizando y evaluando.
 *
 * El nivelador anterior, que restaba `beta` y `gamma` contra los de la referencia, bloqueaba el
 * obturador el 69 % de este tramo y cambiaba de estado 63 veces por minuto — con el teléfono
 * quieto. Es el defecto que ninguna prueba con datos inventados podía ver.
 */
describe('un teléfono de verdad, sostenido quieto', () => {
  function reproducir(): { estados: EstadoDeNivel[]; desviaciones: number[] } {
    let suavizada: Gravedad | null = null;
    let referencia: Gravedad | null = null;
    let anterior: EstadoDeNivel | null = null;
    const estados: EstadoDeNivel[] = [];
    const desviaciones: number[] = [];

    for (const [t, beta, gamma] of LECTURAS_DE_TELEFONO_QUIETO) {
      suavizada = suavizar(suavizada, { beta, gamma });
      // La referencia la fija la primera toma, como en el asistente: el primer segundo y medio.
      if (referencia === null && t >= 1500) {
        referencia = suavizada;
      }
      const nivel = evaluarNivel(suavizada, referencia, anterior);
      anterior = nivel.estado;
      if (referencia !== null) {
        estados.push(nivel.estado);
        desviaciones.push(nivel.desviacion);
      }
    }
    return { estados, desviaciones };
  }

  it('la desviación que mide es la del pulso de una mano, no decenas de grados', () => {
    const { desviaciones } = reproducir();
    const media = desviaciones.reduce((s, d) => s + d, 0) / desviaciones.length;

    expect(media).toBeLessThan(TOLERANCIA_GRADOS);
    expect(Math.max(...desviaciones)).toBeLessThan(15);
  });

  it('el obturador se queda habilitado la mayor parte del tiempo', () => {
    // El nivelador por ángulos bloqueaba el 69 % de estos mismos segundos.
    const { estados } = reproducir();
    const enRango = estados.filter((estado) => estado === 'EN_RANGO').length;

    expect(enRango / estados.length).toBeGreaterThan(0.7);
  });

  it('el obturador no parpadea: cambia de estado un puñado de veces, no decenas', () => {
    // Tocar el botón justo en un parpadeo no dispara, y eso se sufre sin lector de pantalla.
    const { estados } = reproducir();
    const cambios = estados.filter((estado, i) => i > 0 && estado !== estados[i - 1]).length;

    expect(cambios).toBeLessThan(10);
  });
});

describe('claveDeNivel', () => {
  it('dice qué hay que hacer, sin los grados', () => {
    expect(claveDeNivel(evaluarNivel(desde(80, 0), OBJETIVO))).toBe('inclina_atras');
    expect(claveDeNivel(evaluarNivel(desde(100, 0), OBJETIVO))).toBe('inclina_adelante');
    expect(claveDeNivel(evaluarNivel(desde(90, 0), OBJETIVO))).toBe('en_rango');
    expect(claveDeNivel(evaluarNivel(null, OBJETIVO))).toBe('sin_sensor');
  });

  it('la toma que fija la referencia lo dice, aunque el nivel esté en rango', () => {
    expect(claveDeNivel(evaluarNivel(desde(90, 0), null), true)).toBe('fijando_referencia');
  });

  it('dos desviaciones distintas del mismo lado son el mismo anuncio', () => {
    // Es el punto entero: los grados no pueden ser lo que dispara un anuncio.
    expect(claveDeNivel(evaluarNivel(desde(80, 0), OBJETIVO))).toBe(
      claveDeNivel(evaluarNivel(desde(70, 0), OBJETIVO)),
    );
  });
});

describe('asentarAnuncio', () => {
  it('el primer anuncio no espera: quien acaba de entrar no puede quedarse en silencio', () => {
    expect(asentarAnuncio(ANUNCIO_EN_BLANCO, 'en_rango', 0, 1000).clave).toBe('en_rango');
  });

  it('una clave que no se sostiene no se anuncia', () => {
    let anuncio = asentarAnuncio(ANUNCIO_EN_BLANCO, 'en_rango', 0, 0);
    anuncio = asentarAnuncio(anuncio, 'inclina_atras', 7, 100);
    anuncio = asentarAnuncio(anuncio, 'en_rango', 0, 200);

    expect(anuncio.clave).toBe('en_rango');
  });

  it('una clave que se sostiene el retardo entero sí se anuncia', () => {
    let anuncio = asentarAnuncio(ANUNCIO_EN_BLANCO, 'en_rango', 0, 0);
    for (let t = 100; t <= RETARDO_DE_ANUNCIO_MS + 100; t += 100) {
      anuncio = asentarAnuncio(anuncio, 'inclina_atras', 7, t);
    }

    expect(anuncio.clave).toBe('inclina_atras');
    expect(anuncio.grados).toBe(7);
  });

  it('los grados quedan congelados en el anuncio, no siguen al sensor', () => {
    // Si se refrescaran, el contenido de la región viva cambiaría y volvería a hablar.
    let anuncio = asentarAnuncio(ANUNCIO_EN_BLANCO, 'inclina_atras', 7, 0);
    anuncio = asentarAnuncio(anuncio, 'inclina_atras', 9, 100);

    expect(anuncio.grados).toBe(7);
  });

  it('el retardo se cuenta desde que la clave apareció, no desde el último anuncio', () => {
    let anuncio = asentarAnuncio(ANUNCIO_EN_BLANCO, 'en_rango', 0, 0);
    anuncio = asentarAnuncio(anuncio, 'inclina_atras', 7, 1000);
    anuncio = asentarAnuncio(anuncio, 'inclina_atras', 7, 1000 + RETARDO_DE_ANUNCIO_MS);

    expect(anuncio.clave).toBe('inclina_atras');
  });
});

/**
 * La otra mitad de la deuda 32, medida sobre las mismas lecturas reales: cuántas veces hablaría un
 * lector de pantalla en esos 8,6 segundos con el teléfono quieto.
 *
 * Sin esto, el indicador anunciaba cada grado entero — sobre la vuelta completa a un producto, 84,5
 * veces por minuto.
 */
describe('cuántas veces habla el nivel con un teléfono de verdad', () => {
  it('unos pocos anuncios, no uno por lectura', () => {
    let suavizada: Gravedad | null = null;
    let referencia: Gravedad | null = null;
    let estadoAnterior: EstadoDeNivel | null = null;
    let anuncio = ANUNCIO_EN_BLANCO;
    let dichos = 0;

    for (const [t, beta, gamma] of LECTURAS_DE_TELEFONO_QUIETO) {
      suavizada = suavizar(suavizada, { beta, gamma });
      if (referencia === null && t >= 1500) {
        referencia = suavizada;
      }
      const nivel = evaluarNivel(suavizada, referencia, estadoAnterior);
      estadoAnterior = nivel.estado;

      const antes = anuncio.clave;
      anuncio = asentarAnuncio(anuncio, claveDeNivel(nivel, referencia === null), 0, t);
      if (anuncio.clave !== antes) {
        dichos += 1;
      }
    }

    // 508 lecturas en 8,6 segundos. Sin asentar serían decenas.
    expect(dichos).toBeLessThanOrEqual(4);
    expect(dichos).toBeGreaterThan(0);
  });
});
