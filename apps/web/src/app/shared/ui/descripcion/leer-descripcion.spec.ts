import { BloqueDeDescripcion, leerDescripcion, segmentar } from './leer-descripcion';

// La del Echo Dot de 5.ª generación tal como la exportó la ingesta el 22/10/2026, recortada.
const ECHO_DOT = `El Echo Dot de 5.ª generación es el parlante inteligente compacto de Amazon con Alexa.

## Características principales

- Música por Amazon Music, Apple Music, Spotify y otros servicios.
- Botón para desactivar los micrófonos.

## Ficha técnica

| Atributo | Detalle |
|---|---|
| Sonido | altavoz frontal de 44 mm |
| Dimensiones y peso | 100 x 100 x 89 mm · 340 g |

## Contenido de la caja

- Echo Dot (5.ª generación)
- Adaptador de corriente de 15 W

---

Ficha técnica: amazon.es (página del fabricante), consultado el 2026-10-22.
Contenido de la caja: amazon.es (página del fabricante), consultado el 2026-10-22.
`;

function textoDe(segmentos: readonly { texto: string }[]): string {
  return segmentos.map((s) => s.texto).join('');
}

function tipos(bloques: BloqueDeDescripcion[]): string[] {
  return bloques.map((b) => b.tipo);
}

describe('leerDescripcion', () => {
  it('separa la descripción de la ingesta en sus bloques y en su orden', () => {
    expect(tipos(leerDescripcion(ECHO_DOT))).toEqual([
      'parrafo',
      'subtitulo',
      'lista',
      'subtitulo',
      'tabla',
      'subtitulo',
      'lista',
      'fuentes',
    ]);
  });

  it('los subtítulos llegan sin las almohadillas', () => {
    const subtitulos = leerDescripcion(ECHO_DOT).flatMap((b) =>
      b.tipo === 'subtitulo' ? [b.texto] : [],
    );

    expect(subtitulos).toEqual([
      'Características principales',
      'Ficha técnica',
      'Contenido de la caja',
    ]);
  });

  it('cada viñeta es un elemento, sin el guion', () => {
    const lista = leerDescripcion(ECHO_DOT).find((b) => b.tipo === 'lista');

    expect(lista?.tipo === 'lista' && lista.elementos.map(textoDe)).toEqual([
      'Música por Amazon Music, Apple Music, Spotify y otros servicios.',
      'Botón para desactivar los micrófonos.',
    ]);
  });

  it('la tabla separa el encabezado de las filas y quita el separador', () => {
    const tabla = leerDescripcion(ECHO_DOT).find((b) => b.tipo === 'tabla');

    expect(tabla?.tipo).toBe('tabla');
    if (tabla?.tipo !== 'tabla') return;
    expect(tabla.encabezados).toEqual(['Atributo', 'Detalle']);
    expect(tabla.filas.map((f) => f.map(textoDe))).toEqual([
      ['Sonido', 'altavoz frontal de 44 mm'],
      ['Dimensiones y peso', '100 x 100 x 89 mm · 340 g'],
    ]);
  });

  it('lo que va tras la línea horizontal es el pie de fuentes, una línea por fuente', () => {
    const pie = leerDescripcion(ECHO_DOT).at(-1);

    expect(pie?.tipo === 'fuentes' && pie.lineas.map(textoDe)).toEqual([
      'Ficha técnica: amazon.es (página del fabricante), consultado el 2026-10-22.',
      'Contenido de la caja: amazon.es (página del fabricante), consultado el 2026-10-22.',
    ]);
  });

  // Icecat escribe «GPS | Galileo | Glonass» dentro del valor; partida por cada barra, la fila
  // del reloj salía con cinco columnas bajo un encabezado de dos.
  it('una barra de más dentro del valor se queda en la última columna', () => {
    const bloques = leerDescripcion(
      '| Atributo | Detalle |\n|---|---|\n| Conectividad | Bluetooth 5.3 · GPS | Galileo | Glonass |',
    );
    const tabla = bloques[0];

    expect(tabla.tipo === 'tabla' && tabla.filas.map((f) => f.map(textoDe))).toEqual([
      ['Conectividad', 'Bluetooth 5.3 · GPS | Galileo | Glonass'],
    ]);
  });

  it('una barra escapada es texto de la celda', () => {
    const tabla = leerDescripcion('| Puertos | USB-C \\| HDMI |')[0];

    expect(tabla.tipo === 'tabla' && tabla.filas[0].map(textoDe)).toEqual([
      'Puertos',
      'USB-C | HDMI',
    ]);
  });

  it('una tabla sin separador no tiene encabezado: todas sus filas son datos', () => {
    const tabla = leerDescripcion('| Pantalla | 6,7" |\n| Batería | 5000 mAh |')[0];

    expect(tabla.tipo === 'tabla' && tabla.encabezados).toBeNull();
    expect(tabla.tipo === 'tabla' && tabla.filas).toHaveLength(2);
  });

  it('un texto escrito a mano, sin estructura, es un párrafo y conserva sus saltos de línea', () => {
    const bloques = leerDescripcion(
      'Camiseta de algodón.\nLavar en frío.\r\n\r\nHecha en Colombia.',
    );

    expect(tipos(bloques)).toEqual(['parrafo', 'parrafo']);
    expect(bloques[0].tipo === 'parrafo' && bloques[0].lineas.map(textoDe)).toEqual([
      'Camiseta de algodón.',
      'Lavar en frío.',
    ]);
  });

  it('una línea sin guion dentro de una lista continúa la viñeta anterior', () => {
    const lista = leerDescripcion('- Batería de 5000 mAh\n  con carga de 33 W\n- NFC')[0];

    expect(lista.tipo === 'lista' && lista.elementos.map(textoDe)).toEqual([
      'Batería de 5000 mAh con carga de 33 W',
      'NFC',
    ]);
  });

  it('un párrafo pegado a una lista o a un subtítulo no se los traga', () => {
    expect(tipos(leerDescripcion('Apertura.\n## Notas\nTexto.\n- uno'))).toEqual([
      'parrafo',
      'subtitulo',
      'parrafo',
      'lista',
    ]);
  });

  it('una línea horizontal sin nada después no deja un pie vacío', () => {
    expect(tipos(leerDescripcion('Texto.\n\n---\n'))).toEqual(['parrafo']);
  });

  it('vacía o nula no tiene bloques', () => {
    expect(leerDescripcion('')).toEqual([]);
    expect(leerDescripcion('  \n ')).toEqual([]);
    expect(leerDescripcion(null)).toEqual([]);
  });
});

describe('segmentar', () => {
  // El pie de Icecat: la licencia exige el enlace, y lo escribe entre paréntesis.
  it('convierte las direcciones en enlaces, sin el paréntesis ni el punto de la frase', () => {
    const segmentos = segmentar(
      'Datos de Open Icecat (https://icecat.biz) bajo la licencia https://iceclog.com/open-content-license-opl/.',
    );

    expect(segmentos).toEqual([
      { texto: 'Datos de Open Icecat (' },
      { texto: 'https://icecat.biz', enlace: 'https://icecat.biz' },
      { texto: ') bajo la licencia ' },
      {
        texto: 'https://iceclog.com/open-content-license-opl/',
        enlace: 'https://iceclog.com/open-content-license-opl/',
      },
      { texto: '.' },
    ]);
  });

  it('un texto sin direcciones es un solo trozo', () => {
    expect(segmentar('altavoz frontal de 44 mm')).toEqual([{ texto: 'altavoz frontal de 44 mm' }]);
  });

  it('solo http y https: otro esquema se queda como texto', () => {
    expect(segmentar('javascript:alert(1)')).toEqual([{ texto: 'javascript:alert(1)' }]);
  });
});
