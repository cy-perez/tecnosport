import { ordenarPorEtiqueta } from './ordenar-opciones';

const opcion = (etiqueta: string) => ({ valor: etiqueta, etiqueta });

describe('ordenarPorEtiqueta', () => {
  it('ordena sin distinguir tildes ni mayúsculas', () => {
    const ordenadas = ordenarPorEtiqueta(
      ['Verde', 'Índigo', 'azul', 'Coral', 'Coñac', 'Beige'].map(opcion),
      'es',
    );

    expect(ordenadas.map((o) => o.etiqueta)).toEqual([
      'azul',
      'Beige',
      'Coñac',
      'Coral',
      'Índigo',
      'Verde',
    ]);
  });

  it('no toca la lista que recibe', () => {
    const originales = ['B', 'A'].map(opcion);

    ordenarPorEtiqueta(originales, 'es');

    expect(originales.map((o) => o.etiqueta)).toEqual(['B', 'A']);
  });
});
