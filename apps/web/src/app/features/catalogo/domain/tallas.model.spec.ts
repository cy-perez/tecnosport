import { lineasDeTallaPosibles, OpcionDeTalla, ordenarTallas } from './tallas.model';

const ESCALA_ROPA = ['XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL'];
const ESCALA_JEANS = ['26', '28', '30', '32', '34', '36', '38', '40', '42'];

function opciones(...valores: string[]): OpcionDeTalla[] {
  return valores.map((valor) => ({ valor, etiqueta: valor }));
}

function valores(lista: readonly OpcionDeTalla[]): string[] {
  return lista.map((opcion) => opcion.valor);
}

describe('ordenarTallas', () => {
  it('pone las de letra en el orden de la escala, no en el alfabético', () => {
    const ordenadas = ordenarTallas(opciones('XL', 'L', 'M', 'XS', 'S'), [ESCALA_ROPA]);

    expect(valores(ordenadas)).toEqual(['XS', 'S', 'M', 'L', 'XL']);
  });

  /** «100» antes que «28» es el orden alfabético; quien compra lee números. */
  it('pone las de número después de las de letra y de menor a mayor', () => {
    const ordenadas = ordenarTallas(opciones('30', 'M', '28', '100', 'S'), [
      ESCALA_JEANS,
      ESCALA_ROPA,
    ]);

    expect(valores(ordenadas)).toEqual(['S', 'M', '28', '30', '100']);
  });

  it('deja al final lo que ninguna escala nombra y, al último, la talla única', () => {
    const ordenadas = ordenarTallas(opciones('UNICA', 'S-M', 'M', 'L-XL', '38'), [ESCALA_ROPA]);

    expect(valores(ordenadas)).toEqual(['M', '38', 'L-XL', 'S-M', 'UNICA']);
  });

  it('sin escala ordena igual las de número y deja las de letra por orden alfabético', () => {
    expect(valores(ordenarTallas(opciones('M', '40', 'S', '36'), []))).toEqual([
      '36',
      '40',
      'M',
      'S',
    ]);
  });

  it('no cambia la lista que recibe', () => {
    const lista = opciones('L', 'S');
    ordenarTallas(lista, [ESCALA_ROPA]);

    expect(valores(lista)).toEqual(['L', 'S']);
  });
});

describe('lineasDeTallaPosibles', () => {
  const posibles = (seleccion: Parameters<typeof lineasDeTallaPosibles>[0]) => [
    ...lineasDeTallaPosibles(seleccion),
  ];

  it('sin nada elegido se pueden las dos', () => {
    expect(posibles({})).toEqual(['ROPA', 'CALZADO']);
  });

  it('una línea deja solo su talla, y una sin talla ninguna', () => {
    expect(posibles({ linea: 'ROPA' })).toEqual(['ROPA']);
    expect(posibles({ linea: 'CALZADO' })).toEqual(['CALZADO']);
    expect(posibles({ linea: 'TECNOLOGIA' })).toEqual([]);
  });

  it('una categoría acota igual que su línea', () => {
    expect(posibles({ lineaDeCategoria: 'ROPA' })).toEqual(['ROPA']);
    expect(posibles({ lineaDeCategoria: 'BOLSOS' })).toEqual([]);
  });

  it('una marca deja las líneas en que vende', () => {
    expect(posibles({ lineasDeMarca: ['CALZADO', 'TECNOLOGIA'] })).toEqual(['CALZADO']);
    expect(posibles({ lineasDeMarca: ['ROPA', 'CALZADO'] })).toEqual(['ROPA', 'CALZADO']);
    expect(posibles({ lineasDeMarca: ['TECNOLOGIA'] })).toEqual([]);
  });

  it('las elecciones se suman: una marca de las dos con una categoría de ropa deja ropa', () => {
    expect(posibles({ lineaDeCategoria: 'ROPA', lineasDeMarca: ['ROPA', 'CALZADO'] })).toEqual([
      'ROPA',
    ]);
  });
});
