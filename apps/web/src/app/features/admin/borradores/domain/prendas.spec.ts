import {
  agregarPrendaAparte,
  AsignacionDePrendas,
  elegirTonoDeFoto,
  elegirTonoDePrenda,
  marcarTallaDePrenda,
  moverFotoAPrenda,
  olvidarFotoDePrendas,
  prendasEnUso,
  prendaSinTallas,
  prendasSugeridas,
  problemaDePrendas,
  quitarPrenda,
  SIN_PRENDAS,
  tallasDePrenda,
  tallasSugeridasPorPrenda,
  tonoDeFoto,
  unaSolaPrenda,
  variantesQueSeCrean,
} from './prendas';

/** Aplica los colores en orden, cada uno a su foto: el camino de quien marca foto por foto. */
function conTonos(...pares: [string, string][]): AsignacionDePrendas {
  return pares.reduce((a, [id, tono]) => elegirTonoDeFoto(a, id, tono), SIN_PRENDAS);
}

describe('prendas sugeridas por la lectura de fotos', () => {
  const paleta = ['Negro', 'Café', 'Gris oscuro', 'Verde'];

  it('las fotos del mismo color son una prenda, y cada color la suya', () => {
    const a = prendasSugeridas(
      [
        { mensajeId: 'consolidada', tonoSugerido: null },
        { mensajeId: 'negra-frente', tonoSugerido: 'negro' },
        { mensajeId: 'cafe', tonoSugerido: 'cafe' },
        { mensajeId: 'negra-espalda', tonoSugerido: 'NEGRO' },
      ],
      paleta,
    );

    expect(a.prendaPorFoto).toEqual({ 'negra-frente': 1, cafe: 2, 'negra-espalda': 1 });
    expect(a.tonoPorPrenda).toEqual({ 1: 'Negro', 2: 'Café' });
    expect(variantesQueSeCrean(a, ['consolidada', 'negra-frente', 'cafe']).length).toBe(2);
  });

  it('un color que la paleta no tiene no se inventa: la foto vale para todas', () => {
    const a = prendasSugeridas([{ mensajeId: 'f1', tonoSugerido: 'cocoa' }], paleta);

    expect(a).toEqual(SIN_PRENDAS);
  });
});

describe('prendas del borrador', () => {
  it('un color en una foto sin prenda la vuelve una prenda ella sola', () => {
    const a = conTonos(['f1', 'Negro'], ['f2', 'Negro']);

    expect(a.prendaPorFoto).toEqual({ f1: 1, f2: 2 });
    expect(variantesQueSeCrean(a, ['f1', 'f2']).map((v) => v.valor)).toEqual([
      'Negro 1',
      'Negro 2',
    ]);
  });

  /** El jean del 7 de octubre de 2026: cuatro diseños negros son cuatro variantes. */
  it('cuatro prendas del mismo color se numeran', () => {
    const a = conTonos(['f1', 'Negro'], ['f2', 'Negro'], ['f3', 'Negro'], ['f4', 'Negro']);

    expect(variantesQueSeCrean(a, ['f1', 'f2', 'f3', 'f4']).map((v) => v.valor)).toEqual([
      'Negro 1',
      'Negro 2',
      'Negro 3',
      'Negro 4',
    ]);
  });

  /** Dos ángulos de la misma prenda: una variante con dos fotos, y sin número. */
  it('juntar una foto con la prenda de otra le da su color y una sola variante', () => {
    let a = conTonos(['f1', 'Rojo']);
    a = moverFotoAPrenda(a, 'f2', 1);

    expect(tonoDeFoto(a, 'f2')).toBe('Rojo');
    expect(variantesQueSeCrean(a, ['f1', 'f2'])).toEqual([
      { prenda: 1, valor: 'Rojo', fotos: ['f1', 'f2'] },
    ]);
  });

  it('tres prendas de dos fotos cada una son tres variantes', () => {
    let a = conTonos(['f1', 'Rojo'], ['f3', 'Blanco'], ['f5', 'Negro']);
    a = moverFotoAPrenda(a, 'f2', 1);
    a = moverFotoAPrenda(a, 'f4', 2);
    a = moverFotoAPrenda(a, 'f6', 3);

    expect(variantesQueSeCrean(a, ['f1', 'f2', 'f3', 'f4', 'f5', 'f6'])).toEqual([
      { prenda: 1, valor: 'Rojo', fotos: ['f1', 'f2'] },
      { prenda: 2, valor: 'Blanco', fotos: ['f3', 'f4'] },
      { prenda: 3, valor: 'Negro', fotos: ['f5', 'f6'] },
    ]);
  });

  it('cambiar el color de una foto lo cambia en toda su prenda', () => {
    let a = conTonos(['f1', 'Rojo']);
    a = moverFotoAPrenda(a, 'f2', 1);
    a = elegirTonoDeFoto(a, 'f2', 'Vino');

    expect(tonoDeFoto(a, 'f1')).toBe('Vino');
  });

  it('quitarle el color a la foto sola en su prenda la devuelve a valer para todas', () => {
    let a = conTonos(['f1', 'Rojo'], ['f2', 'Azul']);
    a = elegirTonoDeFoto(a, 'f2', '');

    expect(a.prendaPorFoto).toEqual({ f1: 1 });
    expect(a.tonoPorPrenda).toEqual({ 1: 'Rojo' });
  });

  /** Cambiar «Rojo» por «Vino» en casillas pasa por un momento sin ninguno: no deshace el grupo. */
  it('quitarle el color a una foto de una prenda de varias deja la prenda sin color, entera', () => {
    let a = conTonos(['f1', 'Rojo']);
    a = moverFotoAPrenda(a, 'f2', 1);
    a = elegirTonoDeFoto(a, 'f2', '');

    expect(a.prendaPorFoto).toEqual({ f1: 1, f2: 1 });
    expect(problemaDePrendas(a, ['f1', 'f2'])).toEqual({ tipo: 'SIN_COLOR', prenda: 1 });
    a = elegirTonoDeFoto(a, 'f2', 'Vino');
    expect(tonoDeFoto(a, 'f1')).toBe('Vino');
  });

  it('separar una foto en una prenda nueva se lleva su color', () => {
    let a = conTonos(['f1', 'Negro']);
    a = moverFotoAPrenda(a, 'f2', 1);
    a = moverFotoAPrenda(a, 'f2', 'nueva');

    expect(a.prendaPorFoto).toEqual({ f1: 1, f2: 2 });
    expect(variantesQueSeCrean(a, ['f1', 'f2']).map((v) => v.valor)).toEqual([
      'Negro 1',
      'Negro 2',
    ]);
  });

  /** Si no, el número reutilizado heredaría el color de una prenda que ya no existe. */
  it('una prenda que se queda sin fotos se olvida con su color', () => {
    let a = conTonos(['f1', 'Rojo'], ['f2', 'Azul']);
    a = olvidarFotoDePrendas(a, 'f1');

    expect(prendasEnUso(a)).toEqual([2]);
    expect(a.tonoPorPrenda).toEqual({ 2: 'Azul' });
    a = moverFotoAPrenda(a, 'f3', 'nueva');
    expect(a.prendaPorFoto['f3']).toBe(1);
    expect(tonoDeFoto(a, 'f3')).toBe('');
  });

  it('el atajo junta todas en la prenda 1 con el primer color que hubiera', () => {
    let a = conTonos(['f2', 'Rojo'], ['f3', 'Azul']);
    a = unaSolaPrenda(a, ['f1', 'f2', 'f3']);

    expect(variantesQueSeCrean(a, ['f1', 'f2', 'f3'])).toEqual([
      { prenda: 1, valor: 'Rojo', fotos: ['f1', 'f2', 'f3'] },
    ]);
  });

  it('las variantes salen en el orden de su primera foto y solo con las que entran', () => {
    let a = conTonos(['f1', 'Negro'], ['f2', 'Negro']);
    a = moverFotoAPrenda(a, 'f3', 2);

    expect(variantesQueSeCrean(a, ['f3', 'f1'])).toEqual([
      { prenda: 2, valor: 'Negro 1', fotos: ['f3'] },
      { prenda: 1, valor: 'Negro 2', fotos: ['f1'] },
    ]);
  });

  describe('problemas', () => {
    it('una prenda sin color con fotos que entran', () => {
      const a = moverFotoAPrenda(SIN_PRENDAS, 'f1', 'nueva');

      expect(problemaDePrendas(a, ['f1'])).toEqual({ tipo: 'SIN_COLOR', prenda: 1 });
    });

    it('una prenda con color que se queda toda fuera dice cuál foto', () => {
      const a = conTonos(['f1', 'Rojo'], ['f9', 'Negro']);

      expect(problemaDePrendas(a, ['f1'])).toEqual({ tipo: 'FUERA', mensajeId: 'f9' });
    });

    it('una foto fuera de una prenda que sí tiene otras dentro no estorba', () => {
      let a = conTonos(['f1', 'Rojo']);
      a = moverFotoAPrenda(a, 'f9', 1);

      expect(problemaDePrendas(a, ['f1'])).toBeNull();
    });
  });
});

describe('prendas sin foto propia', () => {
  const paleta = ['Negro', 'Café', 'Verde', 'Azul', 'Beige'];

  /**
   * La blusa de Violeta (12 de octubre de 2026): una foto con los tres tonos de frente y otra de
   * espalda. Ninguna es de un solo tono, y antes no se proponía ninguno: se aprobaba sin colores.
   */
  it('los tonos de la foto de todos se proponen como prendas sin foto', () => {
    const a = prendasSugeridas(
      [
        { mensajeId: 'frente', tonoSugerido: null, tonosVistos: ['negro', 'cocoa', 'verde'] },
        { mensajeId: 'espalda', tonoSugerido: null, tonosVistos: ['negro', 'cocoa', 'verde'] },
      ],
      paleta,
    );

    expect(a.prendaPorFoto).toEqual({});
    expect(a.aparte).toEqual([1, 2, 3]);
    expect(a.tonoPorPrenda).toEqual({ 1: 'Negro', 2: '', 3: 'Verde' });
    expect(a.pistaPorPrenda[2]).toBe('cocoa');
    expect(problemaDePrendas(a, ['frente', 'espalda'])).toEqual({ tipo: 'SIN_COLOR', prenda: 2 });

    const conCafe = elegirTonoDePrenda(a, 2, 'Café');
    expect(variantesQueSeCrean(conCafe, ['frente', 'espalda'])).toEqual([
      { prenda: 1, valor: 'Negro', fotos: [] },
      { prenda: 2, valor: 'Café', fotos: [] },
      { prenda: 3, valor: 'Verde', fotos: [] },
    ]);
    expect(problemaDePrendas(conCafe, ['frente', 'espalda'])).toBeNull();
  });

  it('un tono que ya tiene su foto no se propone otra vez sin foto', () => {
    const a = prendasSugeridas(
      [
        { mensajeId: 'todos', tonoSugerido: null, tonosVistos: ['azul', 'beige'] },
        { mensajeId: 'azul', tonoSugerido: 'azul' },
      ],
      paleta,
      ['Azul', 'ivory'],
    );

    expect(variantesQueSeCrean(a, ['todos', 'azul']).map((v) => [v.valor, v.fotos])).toEqual([
      ['Azul', ['azul']],
      ['Beige', []],
    ]);
    expect(a.pistaPorPrenda[3]).toBe('ivory');
    expect(a.tonoPorPrenda[3]).toBe('');
  });

  it('las prendas con foto van primero, como en la API', () => {
    let a = agregarPrendaAparte(SIN_PRENDAS, 'Beige');
    a = elegirTonoDeFoto(a, 'f1', 'Azul');

    expect(variantesQueSeCrean(a, ['f1']).map((v) => v.valor)).toEqual(['Azul', 'Beige']);
  });

  it('una prenda sin foto no se llama como otra', () => {
    let a = elegirTonoDeFoto(SIN_PRENDAS, 'f1', 'Negro');
    a = agregarPrendaAparte(a, 'Negro');

    expect(problemaDePrendas(a, ['f1'])).toEqual({ tipo: 'REPETIDA', prenda: 2, tono: 'Negro' });
  });

  /** La agregada aparte existe sin fotos: sacarle la última no la borra. */
  it('una prenda agregada aparte sigue aunque se quede sin fotos', () => {
    let a = agregarPrendaAparte(SIN_PRENDAS, 'Verde');
    a = moverFotoAPrenda(a, 'f1', 1);
    a = moverFotoAPrenda(a, 'f1', null);

    expect(prendasEnUso(a)).toEqual([1]);
    expect(a.tonoPorPrenda).toEqual({ 1: 'Verde' });
  });

  /** El anuncio dice «verde» y quien revisa marca la foto verde: es la foto de esa prenda. */
  it('marcarle a una foto el color de una prenda sin foto la mete en ella', () => {
    let a = agregarPrendaAparte(SIN_PRENDAS, 'Verde');
    a = elegirTonoDeFoto(a, 'f1', 'verde');

    expect(a.prendaPorFoto).toEqual({ f1: 1 });
    expect(variantesQueSeCrean(a, ['f1'])).toEqual([{ prenda: 1, valor: 'Verde', fotos: ['f1'] }]);
    expect(problemaDePrendas(a, ['f1'])).toBeNull();
  });

  it('quitarle el color a su única foto la saca, y la prenda se queda sin foto y con su color', () => {
    let a = agregarPrendaAparte(SIN_PRENDAS, 'Verde');
    a = elegirTonoDeFoto(a, 'f1', 'Verde');
    a = elegirTonoDeFoto(a, 'f1', '');

    expect(a.prendaPorFoto).toEqual({});
    expect(a.tonoPorPrenda).toEqual({ 1: 'Verde' });
  });

  it('una prenda agregada aparte con fotos que no entran sigue creándose, sin foto', () => {
    let a = agregarPrendaAparte(SIN_PRENDAS, 'Verde');
    a = moverFotoAPrenda(a, 'f9', 1);

    expect(problemaDePrendas(a, ['f1'])).toBeNull();
    expect(variantesQueSeCrean(a, ['f1'])).toEqual([{ prenda: 1, valor: 'Verde', fotos: [] }]);
  });

  it('quitar una prenda devuelve sus fotos a valer para todas y olvida sus tallas', () => {
    let a = agregarPrendaAparte(SIN_PRENDAS, 'Verde');
    a = moverFotoAPrenda(a, 'f1', 1);
    a = marcarTallaDePrenda(a, 1, 'S', false, ['S', 'M']);
    a = quitarPrenda(a, 1);

    expect(a).toEqual(SIN_PRENDAS);
  });

  it('separar una foto en una prenda nueva se lleva la pista y las tallas de la suya', () => {
    let a = agregarPrendaAparte(SIN_PRENDAS, 'Café', 'cocoa');
    a = marcarTallaDePrenda(a, 1, 'S', false, ['S', 'M']);
    a = moverFotoAPrenda(a, 'f1', 1);
    a = moverFotoAPrenda(a, 'f1', 'nueva');

    expect(a.prendaPorFoto).toEqual({ f1: 2 });
    expect(a.pistaPorPrenda[2]).toBe('cocoa');
    expect(tallasDePrenda(a, 2, ['S', 'M'])).toEqual(['M']);
  });

  it('el atajo de una sola prenda se lleva las agregadas aparte', () => {
    const a = unaSolaPrenda(agregarPrendaAparte(SIN_PRENDAS, 'Verde'), ['f1', 'f2']);

    expect(a.aparte).toEqual([]);
  });
});

describe('tallas por color', () => {
  const aprobadas = ['S', 'M', 'L', 'XL'];

  /** El enterizo de Violeta: «beige rosa S L XL / azul M L XL». */
  it('se llenan con lo que el anuncio dice de cada tono, por el color o por la pista', () => {
    let a = agregarPrendaAparte(SIN_PRENDAS, 'Beige', 'beige rosa');
    a = agregarPrendaAparte(a, 'Azul', 'azul');
    a = agregarPrendaAparte(a, 'Blanco', 'ivory');
    a = tallasSugeridasPorPrenda(
      a,
      [
        { tono: 'beige rosa', tallas: ['S', 'L', 'XL'] },
        { tono: 'AZUL', tallas: ['m', 'L', 'XL'] },
      ],
      aprobadas,
    );

    expect(tallasDePrenda(a, 1, aprobadas)).toEqual(['S', 'L', 'XL']);
    expect(tallasDePrenda(a, 2, aprobadas)).toEqual(['M', 'L', 'XL']);
    expect(tallasDePrenda(a, 3, aprobadas)).toEqual(aprobadas);
  });

  it('marcar y desmarcar una casilla cambia solo esa prenda, en el orden del producto', () => {
    let a = agregarPrendaAparte(SIN_PRENDAS, 'Azul');
    a = agregarPrendaAparte(a, 'Beige');
    a = marcarTallaDePrenda(a, 1, 'M', false, aprobadas);
    a = marcarTallaDePrenda(a, 1, 'M', true, aprobadas);
    a = marcarTallaDePrenda(a, 1, 'S', false, aprobadas);

    expect(tallasDePrenda(a, 1, aprobadas)).toEqual(['M', 'L', 'XL']);
    expect(tallasDePrenda(a, 2, aprobadas)).toEqual(aprobadas);
  });

  it('las tallas de una prenda se quedan entre las del producto', () => {
    const a = marcarTallaDePrenda(agregarPrendaAparte(SIN_PRENDAS, 'Azul'), 1, 'S', true, ['S']);

    expect(tallasDePrenda(a, 1, ['M', 'L'])).toEqual([]);
    expect(prendaSinTallas(a, [], ['M', 'L'])).toEqual({
      tipo: 'SIN_TALLAS',
      prenda: 1,
      tono: 'Azul',
    });
  });
});
