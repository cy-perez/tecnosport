import { render } from '@testing-library/angular';
import { ParteDeMuestra } from './muestra-color.model';
import { TsMuestraColor } from './ts-muestra-color';

const NEGRO: ParteDeMuestra = { patron: null, colores: ['#111111'] };
const ROJO: ParteDeMuestra = { patron: null, colores: ['#C62828'] };

async function pintar(partes: readonly ParteDeMuestra[]) {
  const { container } = await render(TsMuestraColor, { inputs: { partes } });
  return container;
}

function rellenos(container: Element): (string | null)[] {
  return [...container.querySelectorAll('svg > path')].map((p) => p.getAttribute('fill'));
}

/** La x de donde arranca cada cuña, para saber de qué lado quedó. */
function arranqueX(container: Element, indice: number): number {
  const d = container.querySelectorAll('svg > path')[indice].getAttribute('d') ?? '';
  return Number(/L (\S+) /.exec(d)?.[1]);
}

describe('TsMuestraColor', () => {
  it('un color liso es una sola porción de ese color', async () => {
    const container = await pintar([NEGRO]);

    expect(rellenos(container)).toEqual(['#111111']);
  });

  /**
   * El caso de la camiseta Ferrari: negro y rojo, mitad y mitad, con el primero a la izquierda. La
   * primera cuña arranca abajo al centro y gira por la izquierda; la segunda arranca arriba.
   */
  it('dos colores son dos mitades en el orden elegido, el primero a la izquierda', async () => {
    const container = await pintar([NEGRO, ROJO]);

    expect(rellenos(container)).toEqual(['#111111', '#C62828']);
    const caminoNegro = container.querySelectorAll('svg > path')[0].getAttribute('d') ?? '';
    expect(caminoNegro).toContain('A 75 75 0 0 1 50.000 -25.000');
    expect(arranqueX(container, 0)).toBeCloseTo(50);
  });

  it('el orden manda: rojo y negro pinta el rojo primero', async () => {
    const container = await pintar([ROJO, NEGRO]);

    expect(rellenos(container)).toEqual(['#C62828', '#111111']);
  });

  it('un patrón se dibuja con sus colores y se combina con uno liso', async () => {
    const container = await pintar([
      { patron: 'ANIMAL_PRINT', colores: ['#C19A6B', '#3B2A1A'] },
      NEGRO,
    ]);

    const [patron, liso] = rellenos(container);
    expect(patron).toMatch(/^url\(#ts-muestra-\d+-0\)$/);
    expect(liso).toBe('#111111');
    const definicion = container.querySelector('pattern');
    const colores = [...(definicion?.querySelectorAll('[fill]') ?? [])].map((e) =>
      e.getAttribute('fill'),
    );
    expect(new Set(colores)).toEqual(new Set(['#C19A6B', '#3B2A1A']));
  });

  it('el multicolor pinta una franja por color', async () => {
    const container = await pintar([
      { patron: 'MULTICOLOR', colores: ['#E53935', '#FDD835', '#1E88E5'] },
    ]);

    expect(container.querySelectorAll('pattern rect')).toHaveLength(3);
  });

  /** Dos muestras en la misma página no se pisan los patrones: cada una con sus ids. */
  it('cada muestra usa ids de patrón propios', async () => {
    const { container } = await render(
      `<ts-muestra-color [partes]="partes" /><ts-muestra-color [partes]="partes" />`,
      {
        imports: [TsMuestraColor],
        componentProperties: {
          partes: [{ patron: 'ESTAMPADO', colores: ['#F5F5F5', '#1B2A4A'] }],
        },
      },
    );

    const ids = [...container.querySelectorAll('pattern')].map((p) => p.id);
    expect(ids).toHaveLength(2);
    expect(new Set(ids).size).toBe(2);
  });

  it('es decorativa: el nombre lo dice el botón que la envuelve', async () => {
    const container = await pintar([NEGRO]);

    expect(container.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
  });
});
