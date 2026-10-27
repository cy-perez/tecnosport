import { ahorroDe, descriptoresDe, Imagen, Variante } from './producto.model';

function imagen(anchos: number[]): Imagen {
  return {
    url: `https://x/${anchos[anchos.length - 1]}.avif`,
    variantes: anchos.map((ancho) => ({ ancho, url: `https://x/${ancho}.avif` })),
    urlVistaPrevia: null,
    ancho: anchos[anchos.length - 1],
    alto: 900,
    altEs: 'alt es',
    altEn: 'alt en',
    varianteId: null,
    enLaFicha: false,
    variantesQueMuestra: [],
  };
}

describe('descriptoresDe', () => {
  it('escribe un descriptor de ancho por variante', () => {
    expect(descriptoresDe(imagen([480, 800, 1200]))).toBe('480w, 800w, 1200w');
  });

  // El procesamiento de estudio no amplía, así que hay tomas que solo llegan a 480. Un `srcset` de
  // una entrada es válido y es exactamente lo que el sitio servía antes de que hubiera variantes.
  it('funciona con una sola variante', () => {
    expect(descriptoresDe(imagen([480]))).toBe('480w');
  });
});

function variante(id: string, publicado: number, contado: number): Variante {
  return {
    id,
    sku: id,
    precio: { valor: publicado, moneda: 'COP' },
    precioContado: { valor: contado, moneda: 'COP' },
    disponible: true,
    disponibilidadPorConfirmar: false,
    atributos: [],
  };
}

/** ADR-0095: el ahorro sin pasarela es publicado menos contado, y nunca negativo. */
describe('ahorroDe', () => {
  it('es el publicado menos el contado', () => {
    expect(ahorroDe(variante('v', 239_000, 230_000))).toBe(9_000);
  });

  it('es cero sin descuento, y no se vuelve negativo con un contado mal cargado', () => {
    expect(ahorroDe(variante('v', 100_000, 100_000))).toBe(0);
    expect(ahorroDe(variante('v', 100_000, 110_000))).toBe(0);
  });
});
