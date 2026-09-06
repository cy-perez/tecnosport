import { FotogramaCrudo } from '../domain/camara.puerto';
import { ProcesadorDeFotogramas } from '../domain/procesador-fotogramas.puerto';
import { ColorRgb, DeteccionDeRecorte, EncuadreDelSet, Rectangulo } from '../domain/recorte-360';
import { FotogramaCapturado } from '../domain/sesion-captura.model';
import { procesarSet } from './procesar-set';

const FONDO: ColorRgb = { r: 240, g: 240, b: 240 };

/** Doble escrito a mano: apunta con qué encuadre le pidieron renderizar cada fotograma. */
class ProcesadorEspia implements ProcesadorDeFotogramas {
  medidos: Blob[] = [];
  encuadres: EncuadreDelSet[] = [];
  fondos: ColorRgb[] = [];
  falloEn: number | null = null;

  constructor(private readonly rectangulos: Rectangulo[]) {}

  async medir(toma: Blob): Promise<DeteccionDeRecorte> {
    const indice = this.medidos.length;
    this.medidos.push(toma);
    if (this.falloEn === indice) {
      return { ok: false, motivo: 'PRODUCTO_CORTADO' };
    }
    return { ok: true, rectangulo: this.rectangulos[indice], fondo: FONDO };
  }

  async renderizar(
    _toma: Blob,
    _rectangulo: Rectangulo,
    encuadre: EncuadreDelSet,
    fondo: ColorRgb,
  ): Promise<Blob> {
    this.fondos.push(fondo);
    this.encuadres.push(encuadre);
    return new Blob(['procesado']);
  }
}

function capturado(orden: number): FotogramaCapturado {
  const imagen: FotogramaCrudo = {
    url: `blob:${orden}`,
    blob: new Blob([`toma-${orden}`]),
    ancho: 1920,
    alto: 1920,
  };
  return { orden, imagen, inclinacion: null };
}

const CUATRO = [capturado(0), capturado(1), capturado(2), capturado(3)];

/** Productos de distinto tamaño en cada toma, que es lo normal al girar. */
const RECTANGULOS: Rectangulo[] = [
  { x: 500, y: 400, ancho: 800, alto: 700 },
  { x: 600, y: 350, ancho: 500, alto: 900 },
  { x: 520, y: 420, ancho: 780, alto: 680 },
  { x: 610, y: 360, ancho: 520, alto: 880 },
];

describe('procesarSet', () => {
  it('renderiza los cuatro fotogramas con el mismo encuadre', async () => {
    const procesador = new ProcesadorEspia(RECTANGULOS);

    const resultado = await procesarSet(CUATRO, procesador, () => undefined);

    expect(resultado.ok).toBe(true);
    expect(procesador.encuadres).toHaveLength(4);
    // Es el invariante del set entero: una sola escala, o el producto late al girar.
    const [primero, ...resto] = procesador.encuadres;
    for (const encuadre of resto) {
      expect(encuadre.escala).toBe(primero.escala);
      expect(encuadre.ladoFuentePx).toBe(primero.ladoFuentePx);
    }
  });

  it('mide todo el set antes de renderizar nada', async () => {
    const procesador = new ProcesadorEspia(RECTANGULOS);
    const orden: string[] = [];
    const medir = procesador.medir.bind(procesador);
    const renderizar = procesador.renderizar.bind(procesador);
    procesador.medir = async (toma: Blob) => {
      orden.push('medir');
      return medir(toma);
    };
    procesador.renderizar = async (
      toma: Blob,
      rectangulo: Rectangulo,
      encuadre: EncuadreDelSet,
      fondo: ColorRgb,
    ) => {
      orden.push('renderizar');
      return renderizar(toma, rectangulo, encuadre, fondo);
    };

    await procesarSet(CUATRO, procesador, () => undefined);

    // Hasta que no está medida la última toma no se conoce la escala de ninguna: por eso no se
    // puede procesar ni subir sobre la marcha.
    expect(orden).toEqual([
      'medir',
      'medir',
      'medir',
      'medir',
      'renderizar',
      'renderizar',
      'renderizar',
      'renderizar',
    ]);
  });

  it('el encuadre lo manda el fotograma mas grande del set, no el primero', async () => {
    const procesador = new ProcesadorEspia(RECTANGULOS);

    await procesarSet(CUATRO, procesador, () => undefined);

    // El lado mayor de todo el set es 900 (el alto del segundo), mas el 8% de margen a cada lado.
    expect(procesador.encuadres[0].ladoFuentePx).toBeCloseTo(900 * 1.16);
  });

  it('una toma que no se puede medir detiene el set y dice cual', async () => {
    const procesador = new ProcesadorEspia(RECTANGULOS);
    procesador.falloEn = 2;

    const resultado = await procesarSet(CUATRO, procesador, () => undefined);

    expect(resultado).toEqual({ ok: false, orden: 2, motivo: 'PRODUCTO_CORTADO' });
    // Y no se renderiza nada: medio set procesado no le sirve a nadie.
    expect(procesador.encuadres).toEqual([]);
  });

  it('informa el avance de las dos pasadas', async () => {
    const procesador = new ProcesadorEspia(RECTANGULOS);
    const avances: number[] = [];

    await procesarSet(CUATRO, procesador, (hechos, total) => {
      avances.push(hechos);
      expect(total).toBe(8);
    });

    expect(avances).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
  });
});
