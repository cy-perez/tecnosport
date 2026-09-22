import { descriptoresDe, Imagen } from './producto.model';

function imagen(anchos: number[]): Imagen {
  return {
    url: `https://x/${anchos[anchos.length - 1]}.avif`,
    variantes: anchos.map((ancho) => ({ ancho, url: `https://x/${ancho}.avif` })),
    urlVistaPrevia: null,
    ancho: anchos[anchos.length - 1],
    alto: 900,
    altEs: 'alt es',
    altEn: 'alt en',
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
