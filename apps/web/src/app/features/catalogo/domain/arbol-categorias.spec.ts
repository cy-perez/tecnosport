import { describe, expect, it } from 'vitest';
import { agruparPorLinea, construirArbolDeCategorias, hojasConRuta } from './arbol-categorias';
import { Categoria } from './producto.model';

function categoria(
  id: string,
  nombre: string,
  linea: string,
  padreId: string | null = null,
): Categoria {
  return { id, nombre, slug: id, linea, padreId };
}

describe('construirArbolDeCategorias', () => {
  it('deja en la raíz las que no tienen padre', () => {
    const arbol = construirArbolDeCategorias([
      categoria('celulares', 'Celulares', 'TECNOLOGIA'),
      categoria('tablets', 'Tablets', 'TECNOLOGIA'),
    ]);

    expect(arbol).toHaveLength(2);
    expect(arbol.every((nodo) => nodo.hijas.length === 0)).toBe(true);
  });

  it('cuelga cada hija de su padre', () => {
    const arbol = construirArbolDeCategorias([
      categoria('ropa-dama', 'Dama', 'ROPA'),
      categoria('ropa-dama-faldas', 'Faldas', 'ROPA', 'ropa-dama'),
      categoria('ropa-dama-blusas', 'Blusas', 'ROPA', 'ropa-dama'),
    ]);

    expect(arbol).toHaveLength(1);
    expect(arbol[0].hijas.map((hija) => hija.categoria.nombre)).toEqual(['Faldas', 'Blusas']);
  });

  it('no depende de que el padre venga antes que la hija', () => {
    const arbol = construirArbolDeCategorias([
      categoria('ropa-dama-faldas', 'Faldas', 'ROPA', 'ropa-dama'),
      categoria('ropa-dama', 'Dama', 'ROPA'),
    ]);

    expect(arbol).toHaveLength(1);
    expect(arbol[0].categoria.nombre).toBe('Dama');
    expect(arbol[0].hijas).toHaveLength(1);
  });

  /**
   * El caso que justifica el `!ids.has(padreId)`: si la hija se perdiera en silencio, una categoría
   * desaparecería del menú sin rastro. En la raíz se ve, y por tanto se puede corregir.
   */
  it('sube a la raíz una hija cuyo padre no vino en la lista', () => {
    const arbol = construirArbolDeCategorias([
      categoria('ropa-dama-faldas', 'Faldas', 'ROPA', 'no-esta'),
    ]);

    expect(arbol).toHaveLength(1);
    expect(arbol[0].categoria.nombre).toBe('Faldas');
  });

  it('con la lista vacía devuelve un árbol vacío', () => {
    expect(construirArbolDeCategorias([])).toEqual([]);
  });
});

describe('agruparPorLinea', () => {
  it('reparte por línea en el orden del menú, no en el alfabético', () => {
    const ramas = agruparPorLinea([
      categoria('bolsos-dama', 'Dama', 'BOLSOS'),
      categoria('celulares', 'Celulares', 'TECNOLOGIA'),
      categoria('ropa-dama', 'Dama', 'ROPA'),
      categoria('calzado-unisex', 'Unisex', 'CALZADO'),
    ]);

    expect(ramas.map((rama) => rama.linea)).toEqual(['TECNOLOGIA', 'ROPA', 'CALZADO', 'BOLSOS']);
    expect(ramas.map((rama) => rama.nodos.length)).toEqual([1, 1, 1, 1]);
  });

  it('devuelve la línea aunque no tenga ni una categoría', () => {
    const ramas = agruparPorLinea([categoria('celulares', 'Celulares', 'TECNOLOGIA')]);

    expect(ramas).toHaveLength(4);
    expect(ramas.find((rama) => rama.linea === 'BOLSOS')?.nodos).toEqual([]);
  });

  /** Una hija no aparece en el primer nivel de su línea: cuelga de su rama. */
  it('no pone las subcategorías en el primer nivel de la línea', () => {
    const ramas = agruparPorLinea([
      categoria('ropa-dama', 'Dama', 'ROPA'),
      categoria('ropa-dama-faldas', 'Faldas', 'ROPA', 'ropa-dama'),
    ]);

    const ropa = ramas.find((rama) => rama.linea === 'ROPA');
    expect(ropa?.nodos).toHaveLength(1);
    expect(ropa?.nodos[0].hijas).toHaveLength(1);
  });
});

describe('hojasConRuta', () => {
  const nombreDeLinea = (linea: string) => (linea === 'ROPA' ? 'Ropa' : 'Tecnología');

  it('solo devuelve hojas, con la línea y los ancestros delante', () => {
    const hojas = hojasConRuta(
      [
        categoria('ropa-dama', 'Dama', 'ROPA'),
        categoria('ropa-dama-faldas', 'Faldas', 'ROPA', 'ropa-dama'),
        categoria('celulares', 'Celulares', 'TECNOLOGIA'),
      ],
      nombreDeLinea,
    );

    expect(hojas.map((hoja) => hoja.ruta)).toEqual([
      'Ropa › Dama › Faldas',
      'Tecnología › Celulares',
    ]);
  });

  /** "Dama" sale en tres líneas y "Busos" en dos ramas: sin la ruta, el desplegable no se lee. */
  it('distingue dos hojas con el mismo nombre por su ruta', () => {
    const hojas = hojasConRuta(
      [
        categoria('ropa-dama', 'Dama', 'ROPA'),
        categoria('ropa-caballero', 'Caballero', 'ROPA'),
        categoria('ropa-dama-busos', 'Busos', 'ROPA', 'ropa-dama'),
        categoria('ropa-caballero-busos', 'Busos', 'ROPA', 'ropa-caballero'),
      ],
      nombreDeLinea,
    );

    expect(hojas.map((hoja) => hoja.ruta)).toEqual([
      'Ropa › Dama › Busos',
      'Ropa › Caballero › Busos',
    ]);
  });

  it('una rama con hojas no se ofrece ella misma', () => {
    const hojas = hojasConRuta(
      [
        categoria('ropa-dama', 'Dama', 'ROPA'),
        categoria('ropa-dama-faldas', 'Faldas', 'ROPA', 'ropa-dama'),
      ],
      nombreDeLinea,
    );

    expect(hojas.map((hoja) => hoja.categoria.slug)).toEqual(['ropa-dama-faldas']);
  });
});
