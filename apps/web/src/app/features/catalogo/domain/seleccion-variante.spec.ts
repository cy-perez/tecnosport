import { muestraDe, Producto, Variante } from './producto.model';
import {
  colorDeImagen,
  detalleDeVariante,
  indiceDeLaPrimeraDelColor,
  nombreDeColor,
  seleccionAlElegir,
  tallaNormalizada,
  coloresDe,
  ejesDeAtributos,
  opcionDisponible,
  tallaUnicaDe,
  seleccionDeVariante,
  soloTallasElegibles,
  variantePorDefecto,
  varianteSeleccionada,
  etiquetaDeOpcion,
} from './seleccion-variante';

function variante(sku: string, disponible: boolean, atributos: Variante['atributos']): Variante {
  return { id: `id-${sku}`, sku, precio: { valor: 100_000, moneda: 'COP' }, disponible, atributos };
}

function productoDePrueba(variantes: Variante[]): Producto {
  return {
    slug: 'camiseta',
    nombre: 'Camiseta',
    descripcion: '',
    marca: { id: '1', nombre: 'TecnoSport' },
    categoria: {
      id: 'c1',
      nombre: 'Ropa',
      slug: 'ropa',
      linea: 'ROPA',
      padreId: null,
      hashtags: [],
      escalaTallas: [],
    },
    imagenPrincipal: null,
    galeria: [],
    rotacion: null,
    variantes,
    escalaTallas: [],
    tallaSirveHasta: null,
  };
}

const azulM = variante('SKU-AZ-M', true, [
  { nombre: 'Color', valor: 'Azul marino', colorHex: '#1E3A8A', unidad: null },
  { nombre: 'Talla', valor: 'M', colorHex: null, unidad: null },
]);
const negroL = variante('SKU-NG-L', false, [
  { nombre: 'Color', valor: 'Negro', colorHex: '#111111', unidad: null },
  { nombre: 'Talla', valor: 'L', colorHex: null, unidad: null },
]);

describe('ejesDeAtributos', () => {
  it('agrupa un eje por nombre de atributo con sus valores distintos', () => {
    const ejes = ejesDeAtributos(productoDePrueba([azulM, negroL]));

    expect(ejes).toEqual([
      {
        nombre: 'Color',
        unidad: null,
        opciones: [
          {
            valor: 'Azul marino',
            colorHex: '#1E3A8A',
            muestra: [{ patron: null, colores: ['#1E3A8A'] }],
            existe: true,
          },
          {
            valor: 'Negro',
            colorHex: '#111111',
            muestra: [{ patron: null, colores: ['#111111'] }],
            existe: true,
          },
        ],
      },
      {
        nombre: 'Talla',
        unidad: null,
        opciones: [
          { valor: 'M', colorHex: null, existe: true },
          { valor: 'L', colorHex: null, existe: true },
        ],
      },
    ]);
  });

  // "Garantía: 12" se leía sin decir 12 qué: la unidad viaja con el atributo y sube al eje.
  it('la unidad del atributo sube al eje', () => {
    const conGarantia = variante('SKU-G', true, [
      { nombre: 'Garantía', valor: '12', colorHex: null, unidad: 'meses' },
    ]);

    const ejes = ejesDeAtributos(productoDePrueba([conGarantia]));

    expect(ejes).toEqual([
      {
        nombre: 'Garantía',
        unidad: 'meses',
        opciones: [{ valor: '12', colorHex: null, existe: true }],
      },
    ]);
    expect(etiquetaDeOpcion(ejes[0], ejes[0].opciones[0])).toBe('12 meses');
  });

  it('un producto sin variantes no tiene ejes', () => {
    expect(ejesDeAtributos(productoDePrueba([]))).toEqual([]);
  });

  /** La escala de la categoría manda el orden y enseña las tallas que este producto no trae. */
  it('con escala, el eje de talla la sigue entera y en su orden', () => {
    const negroXl = variante('SKU-NG-XL', true, [
      { nombre: 'Color', valor: 'Negro', colorHex: '#111111', unidad: null },
      { nombre: 'Talla', valor: 'XL', colorHex: null, unidad: null },
    ]);
    const ejes = ejesDeAtributos(productoDePrueba([negroXl, azulM]), ['S', 'M', 'L', 'XL']);

    expect(ejes[1].opciones).toEqual([
      { valor: 'S', colorHex: null, existe: false },
      { valor: 'M', colorHex: null, existe: true },
      { valor: 'L', colorHex: null, existe: false },
      { valor: 'XL', colorHex: null, existe: true },
    ]);
    expect(ejes[0].opciones.map((o) => o.valor)).toEqual(['Negro', 'Azul marino']);
  });

  it('lo que el producto trae fuera de la escala va al final', () => {
    const rara = variante('SKU-R', true, [
      { nombre: 'Talla', valor: '4XL', colorHex: null, unidad: null },
    ]);
    const ejes = ejesDeAtributos(productoDePrueba([rara, azulM]), ['M', 'L']);

    expect(ejes.find((e) => e.nombre === 'Talla')!.opciones.map((o) => o.valor)).toEqual([
      'M',
      'L',
      '4XL',
    ]);
  });

  it('una talla única no se completa con la escala', () => {
    const unica = variante('SKU-U', true, [
      { nombre: 'Talla', valor: 'Única', colorHex: null, unidad: null },
    ]);

    expect(ejesDeAtributos(productoDePrueba([unica]), ['S', 'M'])[0].opciones).toEqual([
      { valor: 'Única', colorHex: null, existe: true },
    ]);
  });
});

describe('opcionDisponible', () => {
  const azulL = variante('SKU-AZ-L', false, [
    { nombre: 'Color', valor: 'Azul marino', colorHex: '#1E3A8A', unidad: null },
    { nombre: 'Talla', valor: 'L', colorHex: null, unidad: null },
  ]);
  const producto = productoDePrueba([azulM, azulL, negroL]);

  it('una talla con existencia en el color elegido se puede elegir', () => {
    expect(opcionDisponible(producto, { Color: 'Azul marino', Talla: 'L' }, 'Talla', 'M')).toBe(
      true,
    );
  });

  it('una talla agotada en el color elegido no', () => {
    expect(opcionDisponible(producto, { Color: 'Azul marino', Talla: 'M' }, 'Talla', 'L')).toBe(
      false,
    );
  });

  it('una talla que el color elegido no trae, tampoco', () => {
    const conXl = productoDePrueba([
      azulM,
      variante('SKU-NG-XL', true, [
        { nombre: 'Color', valor: 'Negro', colorHex: '#111111', unidad: null },
        { nombre: 'Talla', valor: 'XL', colorHex: null, unidad: null },
      ]),
    ]);

    expect(opcionDisponible(conXl, { Color: 'Azul marino', Talla: 'M' }, 'Talla', 'XL')).toBe(
      false,
    );
    expect(opcionDisponible(conXl, { Color: 'Negro', Talla: 'XL' }, 'Talla', 'XL')).toBe(true);
  });

  it('una talla que no existe en el producto, tampoco', () => {
    expect(opcionDisponible(producto, { Color: 'Azul marino' }, 'Talla', 'XS')).toBe(false);
  });
});

describe('soloTallasElegibles', () => {
  const tallas = (eje: { opciones: readonly { valor: string }[] }) =>
    eje.opciones.map((opcion) => opcion.valor);
  const prenda = (color: string, hex: string, talla: string, disponible = true) =>
    variante(`${color}-${talla}`, disponible, [
      { nombre: 'Color', valor: color, colorHex: hex, unidad: null },
      { nombre: 'Talla', valor: talla, colorHex: null, unidad: null },
    ]);

  /** El caso de la falda short: tallas 8 a 16 en una categoría de escala XS–XXXL. */
  it('quita las tallas de la escala que el producto no trae', () => {
    const falda = productoDePrueba(
      ['8', '10', '12'].map((talla) => prenda('Azul cielo', '#87CEEB', talla)),
    );
    const ejes = ejesDeAtributos(falda, ['XS', 'S', 'M', 'L']);

    const [color, talla] = soloTallasElegibles(falda, ejes, { Color: 'Azul cielo', Talla: '8' });

    expect(tallas(talla)).toEqual(['8', '10', '12']);
    expect(color).toBe(ejes[0]);
  });

  it('quita las agotadas y las que el color elegido no trae, y conserva el orden de la escala', () => {
    const producto = productoDePrueba([
      prenda('Negro', '#111111', 'L'),
      prenda('Negro', '#111111', 'M', false),
      prenda('Negro', '#111111', 'S'),
      prenda('Vino', '#722F37', 'XL'),
    ]);
    const ejes = ejesDeAtributos(producto, ['S', 'M', 'L', 'XL']);

    const [, talla] = soloTallasElegibles(producto, ejes, { Color: 'Negro', Talla: 'S' });

    expect(tallas(talla)).toEqual(['S', 'L']);
  });

  it('nunca esconde la talla elegida', () => {
    const producto = productoDePrueba([
      prenda('Negro', '#111111', 'S'),
      prenda('Negro', '#111111', 'M', false),
    ]);
    const ejes = ejesDeAtributos(producto, ['S', 'M']);

    const [, talla] = soloTallasElegibles(producto, ejes, { Color: 'Negro', Talla: 'M' });

    expect(tallas(talla)).toEqual(['S', 'M']);
  });

  it('con todo agotado dice las tallas que el producto trae, no las de la escala', () => {
    const producto = productoDePrueba([
      prenda('Negro', '#111111', 'S', false),
      prenda('Negro', '#111111', 'M', false),
    ]);
    const ejes = ejesDeAtributos(producto, ['XS', 'S', 'M', 'L']);

    const [, talla] = soloTallasElegibles(producto, ejes, { Color: 'Negro' });

    expect(tallas(talla)).toEqual(['S', 'M']);
  });

  it('no toca los ejes que no son de talla', () => {
    const producto = productoDePrueba([azulM, negroL]);
    const ejes = ejesDeAtributos(producto);

    const [color] = soloTallasElegibles(producto, ejes, { Color: 'Azul marino', Talla: 'M' });

    expect(color.opciones.map((opcion) => opcion.valor)).toEqual(['Azul marino', 'Negro']);
  });
});

describe('tallaUnicaDe', () => {
  const unica = variante('SKU-U', true, [
    { nombre: 'Color', valor: 'Negro', colorHex: '#111111', unidad: null },
    { nombre: 'Talla', valor: 'Única', colorHex: null, unidad: null },
  ]);

  it('dice hasta qué talla sirve una prenda de talla única', () => {
    const producto = { ...productoDePrueba([unica]), tallaSirveHasta: 'L' };

    expect(tallaUnicaDe(producto)).toEqual({ sirveHasta: 'L' });
  });

  it('sin «sirve hasta» la talla única se dice igual', () => {
    expect(tallaUnicaDe(productoDePrueba([unica]))).toEqual({ sirveHasta: null });
  });

  it('una prenda con tallas o un producto que no talla no es de talla única', () => {
    expect(tallaUnicaDe(productoDePrueba([azulM, negroL]))).toBeNull();
    expect(tallaUnicaDe(productoDePrueba([]))).toBeNull();
  });
});

describe('seleccionDeVariante', () => {
  it('convierte los atributos de una variante en un mapa nombre -> valor', () => {
    expect(seleccionDeVariante(azulM)).toEqual({ Color: 'Azul marino', Talla: 'M' });
  });
});

describe('varianteSeleccionada', () => {
  it('encuentra la variante que coincide con la selección completa', () => {
    const producto = productoDePrueba([azulM, negroL]);

    expect(varianteSeleccionada(producto, { Color: 'Negro', Talla: 'L' })).toBe(negroL);
  });

  it('devuelve null si la combinación no corresponde a ningún SKU real', () => {
    const producto = productoDePrueba([azulM, negroL]);

    expect(varianteSeleccionada(producto, { Color: 'Azul marino', Talla: 'L' })).toBeNull();
  });
});

describe('variantePorDefecto', () => {
  it('prefiere la primera variante disponible', () => {
    expect(variantePorDefecto(productoDePrueba([negroL, azulM]))).toBe(azulM);
  });

  it('si ninguna está disponible, devuelve la primera', () => {
    const agotada = variante('SKU-AGOTADA', false, []);
    expect(variantePorDefecto(productoDePrueba([negroL, agotada]))).toBe(negroL);
  });

  it('un producto sin variantes devuelve null', () => {
    expect(variantePorDefecto(productoDePrueba([]))).toBeNull();
  });
});

describe('detalleDeVariante', () => {
  it('dice lo que la variante es, con su unidad', () => {
    expect(detalleDeVariante(azulM)).toBe('Azul marino · M');
    expect(
      detalleDeVariante(
        variante('SKU-G', true, [
          { nombre: 'Garantía', valor: '12', colorHex: null, unidad: 'meses' },
        ]),
      ),
    ).toBe('12 meses');
  });
});

describe('seleccionAlElegir', () => {
  const celular = (almacenamiento: string, ram: string, disponible = true) =>
    variante(`SKU-${almacenamiento}-${ram}`, disponible, [
      { nombre: 'Almacenamiento', valor: almacenamiento, colorHex: null, unidad: null },
      { nombre: 'RAM', valor: ram, colorHex: null, unidad: null },
    ]);

  it('si la combinación existe, es esa', () => {
    const producto = productoDePrueba([celular('128', '6'), celular('128', '8')]);

    expect(seleccionAlElegir(producto, { Almacenamiento: '128', RAM: '6' }, 'RAM', '8')).toEqual({
      Almacenamiento: '128',
      RAM: '8',
    });
  });

  it('si no existe, salta a la variante que tiene lo elegido', () => {
    const producto = productoDePrueba([celular('128', '6'), celular('256', '8')]);

    expect(
      seleccionAlElegir(producto, { Almacenamiento: '128', RAM: '6' }, 'Almacenamiento', '256'),
    ).toEqual({ Almacenamiento: '256', RAM: '8' });
  });

  it('entre varias, prefiere la disponible', () => {
    const producto = productoDePrueba([
      celular('128', '6'),
      celular('256', '8', false),
      celular('256', '12'),
    ]);

    expect(
      seleccionAlElegir(producto, { Almacenamiento: '128', RAM: '6' }, 'Almacenamiento', '256'),
    ).toEqual({ Almacenamiento: '256', RAM: '12' });
  });
});

describe('indiceDeLaPrimeraDelColor', () => {
  const imagen = (url: string, varianteId: string | null) => ({
    url,
    variantes: [{ ancho: 800, url }],
    urlVistaPrevia: null,
    ancho: 800,
    alto: 800,
    altEs: url,
    altEn: url,
    varianteId,
  });
  const vino = variante('SKU-V', true, [
    { nombre: 'Color', valor: 'Vino', colorHex: '#722F37', unidad: null },
  ]);
  const negro = variante('SKU-N', true, [
    { nombre: 'Color', valor: 'Negro', colorHex: '#111111', unidad: null },
  ]);
  const producto = productoDePrueba([vino, negro]);
  const principal = imagen('principal', null);
  const deVino = imagen('vino', vino.id);

  it('elegir un color se para en su primera foto', () => {
    expect(indiceDeLaPrimeraDelColor(producto, [principal, deVino], 'Vino')).toBe(1);
  });

  /**
   * Antes esto recortaba la lista y un color sin foto propia se quedaba con la principal sola.
   * Ahora la lista no cambia: lo que cambia es dónde se para, y sin foto propia se para en la
   * primera, que es la principal.
   */
  it('un color sin foto propia se queda en la primera', () => {
    expect(indiceDeLaPrimeraDelColor(producto, [principal, deVino], 'Negro')).toBe(0);
  });

  it('sin color elegido, la primera', () => {
    expect(indiceDeLaPrimeraDelColor(producto, [principal, deVino], null)).toBe(0);
  });

  /**
   * Y lo que de verdad arregla el defecto: las fotos de los demás colores <b>siguen en la lista</b>.
   * La función ya no decide cuántas se ven, solo en cuál se empieza — un producto de nueve fotos
   * enseñaba una.
   */
  it('no esconde ninguna foto: solo dice por dónde empezar', () => {
    const todas = [principal, deVino];
    expect(indiceDeLaPrimeraDelColor(producto, todas, 'Negro')).toBeLessThan(todas.length);
    expect(todas).toHaveLength(2);
  });
});

describe('colorDeImagen', () => {
  const imagen = (url: string, varianteId: string | null) => ({
    url,
    variantes: [{ ancho: 800, url }],
    urlVistaPrevia: null,
    ancho: 800,
    alto: 800,
    altEs: url,
    altEn: url,
    varianteId,
  });
  const producto = productoDePrueba([azulM, negroL]);

  it('una foto de una prenda dice el color de esa prenda', () => {
    expect(colorDeImagen(producto, imagen('negra', negroL.id))).toBe('Negro');
  });

  it('una foto general no tiene color', () => {
    expect(colorDeImagen(producto, imagen('principal', null))).toBeNull();
  });

  it('una foto de una variante que ya no existe no tiene color', () => {
    expect(colorDeImagen(producto, imagen('huerfana', 'id-borrada'))).toBeNull();
  });

  it('una foto de una variante sin color no tiene color', () => {
    const memoria = variante('SKU-128', true, [
      { nombre: 'Almacenamiento', valor: '128', colorHex: null, unidad: 'GB' },
    ]);
    expect(colorDeImagen(productoDePrueba([memoria]), imagen('128', memoria.id))).toBeNull();
  });
});

describe('tallaNormalizada', () => {
  it('la misma talla escrita distinto es la misma', () => {
    expect(tallaNormalizada('m')).toBe(tallaNormalizada('M'));
    expect(tallaNormalizada('2XL')).toBe('XXL');
    expect(tallaNormalizada('3xl')).toBe('XXXL');
    expect(tallaNormalizada(' 38 ')).toBe('38');
  });

  it('con la escala, «m» y «2XL» caen en su casilla y no se duplican', () => {
    const minuscula = variante('SKU-m', true, [
      { nombre: 'Talla', valor: 'm', colorHex: null, unidad: null },
    ]);
    const dosXl = variante('SKU-2XL', true, [
      { nombre: 'Talla', valor: '2XL', colorHex: null, unidad: null },
    ]);
    const ejes = ejesDeAtributos(productoDePrueba([minuscula, dosXl]), ['M', 'XL', 'XXL']);

    expect(ejes[0].opciones.map((o) => [o.valor, o.existe])).toEqual([
      ['m', true],
      ['XL', false],
      ['2XL', true],
    ]);
  });
});

describe('nombreDeColor', () => {
  const paleta = [{ nombre: 'Negro', nombreEn: 'Black' }];

  it('en inglés dice el nombre en inglés de la paleta', () => {
    expect(nombreDeColor('Negro', paleta, 'en')).toBe('Black');
    expect(nombreDeColor('Negro', paleta, 'es')).toBe('Negro');
    expect(nombreDeColor('Fucsia', paleta, 'en')).toBe('Fucsia');
  });

  /** «Negro / Rojo» se traduce parte por parte; la que no está en la paleta se queda como va. */
  it('una combinación se traduce parte por parte', () => {
    const conRojo = [...paleta, { nombre: 'Rojo', nombreEn: 'Red' }];

    expect(nombreDeColor('Negro / Rojo', conRojo, 'en')).toBe('Black / Red');
    expect(nombreDeColor('Negro / Fucsia', conRojo, 'en')).toBe('Black / Fucsia');
    expect(nombreDeColor('Negro / Rojo', conRojo, 'es')).toBe('Negro / Rojo');
  });

  /**
   * Cuando el proveedor manda dos prendas distintas con el mismo color, la aprobación las numera
   * —«Negro 1», «Negro 2»— para que el pedido pueda distinguirlas. La paleta guarda el nombre
   * sin número, así que hay que apartarlo antes de buscar: sin esto, en inglés se leía
   * «Negro 2» en medio de «Black» y «Red».
   */
  it('un color numerado se traduce y conserva su número', () => {
    expect(nombreDeColor('Negro 2', paleta, 'en')).toBe('Black 2');
    expect(nombreDeColor('Negro 2', paleta, 'es')).toBe('Negro 2');
  });

  /** El número no se puede perder: es lo único que distingue una variante de la otra. */
  it('sin el número las dos variantes se leerían igual', () => {
    expect(nombreDeColor('Negro 1', paleta, 'en')).not.toBe(nombreDeColor('Negro 2', paleta, 'en'));
  });
});

describe('la muestra de los colores', () => {
  const negroRojo = {
    nombre: 'Color',
    valor: 'Negro / Rojo',
    colorHex: '#111111',
    unidad: null,
    muestra: [
      { patron: null, colores: ['#111111'] },
      { patron: null, colores: ['#C62828'] },
    ],
  };
  const blanco = { nombre: 'Color', valor: 'Blanco', colorHex: '#FFFFFF', unidad: null };

  /** La tarjeta y la ficha pintan las porciones en el orden en que se eligieron. */
  it('una combinación llega a la tarjeta y al selector con sus porciones en orden', () => {
    const producto = productoDePrueba([
      variante('SKU-NR', true, [negroRojo]),
      variante('SKU-B', true, [blanco]),
    ]);

    expect(coloresDe(producto).map((c) => c.muestra.map((p) => p.colores[0]))).toEqual([
      ['#111111', '#C62828'],
      ['#FFFFFF'],
    ]);
    expect(ejesDeAtributos(producto)[0].opciones[0].muestra).toEqual(negroRojo.muestra);
  });

  /** Un valor de antes de las combinaciones no trae porciones: su muestra es su `colorHex`. */
  it('sin porciones la muestra es el colorHex solo', () => {
    expect(muestraDe(blanco)).toEqual([{ patron: null, colores: ['#FFFFFF'] }]);
    expect(muestraDe({ colorHex: null })).toEqual([]);
  });
});
