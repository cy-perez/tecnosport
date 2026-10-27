import {
  ahorroDe,
  ahorroDesde,
  descriptoresDe,
  Imagen,
  Producto,
  Variante,
} from './producto.model';

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

function productoCon(variantes: Variante[]): Producto {
  return {
    slug: 'p',
    nombre: 'P',
    descripcion: '',
    marca: { id: 'm', nombre: 'M' },
    categoria: {
      id: 'c',
      nombre: 'Bolsos de mano',
      slug: 'bolsos-dama-bolsos-de-mano',
      linea: 'BOLSOS',
      padreId: null,
      hashtags: [],
      escalaTallas: [],
    },
    imagenPrincipal: null,
    galeria: [],
    variantes,
    escalaTallas: [],
    tallaSirveHasta: null,
    paleta: [],
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

describe('ahorroDesde', () => {
  it('con un mismo ahorro en todas las variantes no dice "o más"', () => {
    expect(
      ahorroDesde(productoCon([variante('a', 105_000, 100_000), variante('b', 105_000, 100_000)])),
    ).toEqual({ valor: 5_000, moneda: 'COP', varia: false });
  });

  it('con ahorros distintos anuncia el menor y avisa que varía', () => {
    expect(
      ahorroDesde(
        productoCon([variante('a', 1_034_000, 1_000_000), variante('b', 53_000, 50_000)]),
      ),
    ).toEqual({ valor: 3_000, moneda: 'COP', varia: true });
  });

  it('no anuncia nada si alguna variante no ahorra, ni sin variantes', () => {
    expect(
      ahorroDesde(productoCon([variante('a', 105_000, 100_000), variante('b', 50_000, 50_000)])),
    ).toBeNull();
    expect(ahorroDesde(productoCon([]))).toBeNull();
  });
});
