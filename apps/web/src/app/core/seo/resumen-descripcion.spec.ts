import { resumirDescripcion } from './resumen-descripcion';

describe('resumirDescripcion', () => {
  it('deja como está una descripción corta', () => {
    expect(resumirDescripcion('Camiseta de running en tejido técnico.')).toBe(
      'Camiseta de running en tejido técnico.',
    );
  });

  it('colapsa saltos de línea y espacios repetidos', () => {
    expect(resumirDescripcion('Camiseta  de running.\n\nTejido técnico.  ')).toBe(
      'Camiseta de running. Tejido técnico.',
    );
  });

  it('devuelve cadena vacía cuando no hay descripción', () => {
    expect(resumirDescripcion(null)).toBe('');
    expect(resumirDescripcion(undefined)).toBe('');
    expect(resumirDescripcion('   \n  ')).toBe('');
  });

  it('recorta por palabra y no a mitad de una', () => {
    const largo = `${'palabra '.repeat(30)}final`;
    const resumen = resumirDescripcion(largo);

    expect(resumen.length).toBeLessThanOrEqual(161); // 160 más los puntos suspensivos
    expect(resumen.endsWith('…')).toBe(true);
    expect(resumen.slice(0, -1).endsWith('palabra')).toBe(true);
  });

  it('no deja el signo de puntuación pegado a los puntos suspensivos', () => {
    const largo = `${'palabra, '.repeat(30)}final`;
    expect(resumirDescripcion(largo)).not.toContain(',…');
  });

  // Sin espacios no hay palabra por la cual cortar, y devolver el texto entero
  // sería peor que un corte feo: la descripción se envía tal cual al buscador.
  it('corta igual un texto sin espacios', () => {
    const resumen = resumirDescripcion('a'.repeat(400));
    expect(resumen.length).toBe(161);
    expect(resumen.endsWith('…')).toBe(true);
  });
});
