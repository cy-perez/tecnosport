import { Imagen, muestraDe, ParteDeMuestra, Producto, Variante } from './producto.model';

export interface OpcionEje {
  readonly valor: string;
  readonly colorHex: string | null;
  /** Las porciones del círculo de un color; ausente, la de `colorHex` solo. */
  readonly muestra?: readonly ParteDeMuestra[];
  /**
   * Si alguna variante del producto la tiene. Falso en las tallas que la escala de la categoría
   * trae y este producto no; la ficha las quita con `soloTallasElegibles`, junto con las agotadas.
   */
  readonly existe: boolean;
}

export interface EjeAtributo {
  readonly nombre: string;
  /** Del eje y no de cada opción: todos los valores de un atributo se miden igual. */
  readonly unidad: string | null;
  readonly opciones: readonly OpcionEje[];
}

/** Lo que se pinta en el botón: "12 meses", o "M" cuando el eje no tiene unidad. */
export function etiquetaDeOpcion(eje: EjeAtributo, opcion: OpcionEje): string {
  return eje.unidad ? `${opcion.valor} ${eje.unidad}` : opcion.valor;
}

/** Nombre de atributo -> valor elegido. */
export type Seleccion = Readonly<Record<string, string>>;

/** El valor de talla con que la ingesta publica una prenda de talla única. */
export const TALLA_UNICA = 'Única';

function plano(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

/** «Talla», «Talla calzado»: el eje que se ordena y se completa con la escala de la categoría. */
export function esEjeDeTalla(nombre: string): boolean {
  return plano(nombre).startsWith('talla');
}

/**
 * Un eje por cada nombre de atributo distinto entre las variantes del producto (Color, Talla...).
 *
 * Con la escala de tallas de la categoría, el eje de talla la sigue entera y en su orden —XS antes
 * que S, 34 antes que 35—, con las tallas que este producto no trae marcadas como inexistentes; lo
 * que el producto traiga fuera de la escala va al final, en el orden en que llegó.
 */
export function ejesDeAtributos(
  producto: Producto,
  escalaTallas: readonly string[] = [],
): EjeAtributo[] {
  const valoresPorEje = new Map<
    string,
    Map<string, { colorHex: string | null; muestra: readonly ParteDeMuestra[] }>
  >();
  const unidadPorEje = new Map<string, string | null>();

  for (const variante of producto.variantes) {
    for (const valorAtributo of variante.atributos) {
      if (!valoresPorEje.has(valorAtributo.nombre)) {
        valoresPorEje.set(valorAtributo.nombre, new Map());
        unidadPorEje.set(valorAtributo.nombre, valorAtributo.unidad);
      }
      valoresPorEje.get(valorAtributo.nombre)!.set(valorAtributo.valor, {
        colorHex: valorAtributo.colorHex,
        muestra: muestraDe(valorAtributo),
      });
    }
  }

  return [...valoresPorEje.entries()].map(([nombre, valores]) => {
    const presentes: OpcionEje[] = [...valores.entries()].map(([valor, color]) => ({
      valor,
      colorHex: color.colorHex,
      // Solo en un color: una talla no tiene muestra que pintar.
      ...(color.muestra.length > 0 ? { muestra: color.muestra } : {}),
      existe: true,
    }));
    const unicaSola = presentes.length === 1 && esTallaUnica(presentes[0].valor);
    return {
      nombre,
      unidad: unidadPorEje.get(nombre) ?? null,
      opciones:
        esEjeDeTalla(nombre) && escalaTallas.length > 0 && !unicaSola
          ? segunLaEscala(presentes, escalaTallas)
          : presentes,
    };
  });
}

/**
 * Una talla como se compara: sin tildes, en mayúsculas y con «2XL» escrito «XXL». El proveedor y la
 * extracción no la escriben siempre como la escala, y sin esto «m» y «M» eran dos tallas.
 *
 * <p>Una talla agrupada se compara parte por parte y con guion: «s/m», «S - M» y «S-M» son la
 * casilla «S-M», y «2XL-3XL» la de «XXL-XXXL».
 */
export function tallaNormalizada(talla: string): string {
  const plana = plano(talla).toUpperCase().replace(/\s+/g, '');
  return plana.split(/[-/]/).map(unaTalla).join('-');
}

/**
 * «S, M, L» o «S M L»: una lista de tallas tecleada, separada por coma o espacio. Los espacios
 * alrededor de un guion o una barra no separan: «S - M, L / XL» son dos tallas agrupadas y no seis.
 */
export function separarTallas(texto: string): string[] {
  return texto
    .replace(/\s*([-/])\s*/g, '$1')
    .split(/[,\s]+/)
    .filter((parte) => parte.length > 0);
}

function unaTalla(parte: string): string {
  const conNumero = /^([2-5])XL$/.exec(parte);
  return conNumero ? 'X'.repeat(Number(conNumero[1])) + 'L' : parte;
}

function segunLaEscala(presentes: OpcionEje[], escala: readonly string[]): OpcionEje[] {
  const porValor = new Map(presentes.map((opcion) => [tallaNormalizada(opcion.valor), opcion]));
  const enEscala = escala.map(
    (talla) =>
      porValor.get(tallaNormalizada(talla)) ?? { valor: talla, colorHex: null, existe: false },
  );
  const deLaEscala = new Set(escala.map(tallaNormalizada));
  const fuera = presentes.filter((opcion) => !deLaEscala.has(tallaNormalizada(opcion.valor)));
  return [...enEscala, ...fuera];
}

export function esTallaUnica(valor: string): boolean {
  return plano(valor) === plano(TALLA_UNICA);
}

export function seleccionDeVariante(variante: Variante): Seleccion {
  return Object.fromEntries(variante.atributos.map((va) => [va.nombre, va.valor]));
}

/**
 * Lo que queda elegido al pulsar una opción. Si con lo demás elegido esa combinación existe, es
 * esa; si no —se pulsó una talla que en este color no hay—, la variante que tenga la opción
 * pulsada y se parezca más a lo que estaba elegido, primero entre las disponibles.
 */
export function seleccionAlElegir(
  producto: Producto,
  actual: Seleccion,
  nombreEje: string,
  valor: string,
): Seleccion {
  const deseada = { ...actual, [nombreEje]: valor };
  if (varianteSeleccionada(producto, deseada)) {
    return deseada;
  }
  const candidatas = producto.variantes.filter(
    (variante) => seleccionDeVariante(variante)[nombreEje] === valor,
  );
  const coincidencias = (variante: Variante) => {
    const valores = seleccionDeVariante(variante);
    return Object.entries(actual).filter(([nombre, elegido]) => valores[nombre] === elegido).length;
  };
  const mejor = [...candidatas].sort(
    (una, otra) =>
      Number(otra.disponible) - Number(una.disponible) || coincidencias(otra) - coincidencias(una),
  )[0];
  return mejor ? seleccionDeVariante(mejor) : deseada;
}

/** `null` si la combinación elegida no corresponde a ningún SKU real. */
export function varianteSeleccionada(producto: Producto, seleccion: Seleccion): Variante | null {
  const nombresEjes = Object.keys(seleccion);
  return (
    producto.variantes.find((variante) => {
      const valoresVariante = seleccionDeVariante(variante);
      return nombresEjes.every((nombre) => valoresVariante[nombre] === seleccion[nombre]);
    }) ?? null
  );
}

/**
 * Si elegir esta opción, con lo demás que ya está elegido, lleva a algo que se puede comprar: una
 * talla agotada en el color elegido, o que no existe en él, no lo hace. Es lo que la ficha tacha.
 */
export function opcionDisponible(
  producto: Producto,
  seleccion: Seleccion,
  nombreEje: string,
  valor: string,
): boolean {
  return producto.variantes.some((variante) => {
    if (!variante.disponible) {
      return false;
    }
    const valores = seleccionDeVariante(variante);
    if (valores[nombreEje] !== valor) {
      return false;
    }
    return Object.entries(seleccion).every(
      ([nombre, elegido]) => nombre === nombreEje || valores[nombre] === elegido,
    );
  });
}

/**
 * Los ejes con solo las tallas que se pueden comprar con lo demás elegido: ni las de la escala que
 * el producto no trae ni las agotadas en el color elegido. La falda short, de tallas 8 a 16 en una
 * categoría de escala XS–XXXL, enseñaba doce botones y siete tachados; quien compra solo necesita
 * ver lo que puede elegir. La elegida se queda aunque no esté, para que la selección nunca quede
 * fuera de la vista; y si no queda ninguna —todo agotado—, se dicen las que el producto trae, que
 * siguen tachadas.
 */
export function soloTallasElegibles(
  producto: Producto,
  ejes: readonly EjeAtributo[],
  seleccion: Seleccion,
): EjeAtributo[] {
  return ejes.map((eje) => {
    if (!esEjeDeTalla(eje.nombre)) {
      return eje;
    }
    const elegibles = eje.opciones.filter(
      (opcion) =>
        opcion.valor === seleccion[eje.nombre] ||
        (opcion.existe && opcionDisponible(producto, seleccion, eje.nombre, opcion.valor)),
    );
    return {
      ...eje,
      opciones: elegibles.length > 0 ? elegibles : eje.opciones.filter((opcion) => opcion.existe),
    };
  });
}

/**
 * Lo que la variante es, para decirlo en el carrito: «Negro · M», «12 meses». Ordenado por el
 * nombre del atributo, igual que lo congela el pedido en la API: la misma variante se lee igual en
 * los dos sitios.
 */
export function detalleDeVariante(variante: Variante): string {
  return [...variante.atributos]
    .sort((uno, otro) => uno.nombre.localeCompare(otro.nombre))
    .map((valor) => (valor.unidad ? `${valor.valor} ${valor.unidad}` : valor.valor))
    .join(' · ');
}

/** La primera variante disponible; si ninguna lo está, la primera de todas. */
export function variantePorDefecto(producto: Producto): Variante | null {
  return (
    producto.variantes.find((variante) => variante.disponible) ?? producto.variantes[0] ?? null
  );
}

/**
 * La talla de una prenda de talla única, para decirla en la tarjeta y en la ficha: quien compra no
 * sabe de qué talla es un bodi si solo ve la marca, el nombre y el precio. `null` si el producto
 * talla de otra forma o no talla.
 */
export function tallaUnicaDe(producto: Producto): { readonly sirveHasta: string | null } | null {
  const tallas = new Set<string>();
  for (const variante of producto.variantes) {
    for (const valor of variante.atributos) {
      if (esEjeDeTalla(valor.nombre)) {
        tallas.add(plano(valor.valor));
      }
    }
  }
  if (tallas.size !== 1 || !tallas.has(plano(TALLA_UNICA))) {
    return null;
  }
  return { sirveHasta: producto.tallaSirveHasta };
}

/** Un color del producto con su muestra: lo que pinta la tarjeta debajo de la foto. */
export interface ColorDeProducto {
  readonly valor: string;
  readonly colorHex: string;
  /** Las porciones del círculo, en el orden en que se eligieron: «Negro / Rojo» son dos. */
  readonly muestra: readonly ParteDeMuestra[];
}

/** Los colores del producto, sin repetir y en el orden de sus variantes; solo los que tienen HEX. */
export function coloresDe(producto: Producto): ColorDeProducto[] {
  const vistos = new Map<string, ColorDeProducto>();
  for (const variante of producto.variantes) {
    for (const valor of variante.atributos) {
      if (valor.colorHex && !vistos.has(valor.valor)) {
        vistos.set(valor.valor, {
          valor: valor.valor,
          colorHex: valor.colorHex,
          muestra: muestraDe(valor),
        });
      }
    }
  }
  return [...vistos.values()];
}

function variantesDelColor(producto: Producto, color: string): Set<string> {
  return new Set(
    producto.variantes
      .filter((variante) =>
        variante.atributos.some((valor) => valor.colorHex !== null && valor.valor === color),
      )
      .map((variante) => variante.id),
  );
}

/**
 * Dónde se para la galería al elegir un color: en la primera foto de ese tono, o en la primera
 * de todas si ese tono no tiene ninguna propia.
 *
 * <b>Esto era un filtro y ahora es un índice, y el cambio es el arreglo.</b> `imagenesDelColor`
 * devolvía <i>solo</i> las fotos del color elegido —y, con la casilla de las fotos generales en
 * falso, ni siquiera la principal—. En un pantalón de siete colores con una foto por color eso dejaba la
 * ficha enseñando <b>una</b> foto de las nueve que el producto tiene, sin tira de miniaturas que
 * insinuara que hay más: para llegar a las otras ocho había que ir tocando círculos de color a
 * ciegas, y a la principal no se llegaba nunca.
 *
 * Ahora la galería las enseña todas siempre —la principal primero, que es la que la tarjeta ya
 * usa de previsualización, y detrás la galería en su orden— y elegir un color mueve la foto
 * activa en vez de recortar la lista. Lo que el color decide es dónde mirar, no cuánto se ve.
 */
export function indiceDeLaPrimeraDelColor(
  producto: Producto,
  imagenes: readonly Imagen[],
  color: string | null,
): number {
  if (!color) {
    return 0;
  }
  const delColor = variantesDelColor(producto, color);
  const indice = imagenes.findIndex(
    (imagen) => imagen.varianteId !== null && delColor.has(imagen.varianteId),
  );
  return indice === -1 ? 0 : indice;
}

/**
 * El color que retrata una foto: el de la variante de la que cuelga, o `null` si es una foto
 * general —la principal, o una que no se asoció a ninguna prenda— o su variante no tiene color.
 *
 * Es el camino de vuelta de `indiceDeLaPrimeraDelColor`. Sin él, tocar la miniatura del azul
 * cielo enseñaba el azul cielo y dejaba elegido el beige de la variante por defecto: lo que se
 * agregaba al carrito no era lo que se estaba mirando.
 */
export function colorDeImagen(producto: Producto, imagen: Imagen): string | null {
  if (imagen.varianteId === null) {
    return null;
  }
  const variante = producto.variantes.find((candidata) => candidata.id === imagen.varianteId);
  return variante?.atributos.find((valor) => valor.colorHex !== null)?.valor ?? null;
}

/**
 * La foto de la tarjeta para un color: la primera de ese tono, o la principal. La principal no
 * cuelga de ninguna variante —el panel la reemplaza como la del producto—, así que el color que
 * muestra es el que no tiene otra foto propia.
 */
export function imagenDelColor(producto: Producto, color: string | null): Imagen | null {
  if (!color) {
    return producto.imagenPrincipal;
  }
  const delColor = variantesDelColor(producto, color);
  return (
    producto.galeria.find((imagen) => imagen.varianteId && delColor.has(imagen.varianteId)) ??
    producto.imagenPrincipal
  );
}

/**
 * El nombre de un color en el idioma de quien mira: el valor del atributo es el nombre en español,
 * y la paleta trae el inglés. Una combinación se traduce parte por parte —«Negro / Rojo» es
 * «Black / Red»—, y un color que no está en la paleta se dice como está guardado.
 */
export function nombreDeColor(
  valor: string,
  paleta: readonly { readonly nombre: string; readonly nombreEn: string }[],
  idioma: string,
): string {
  if (idioma !== 'en') {
    return valor;
  }
  const { nombre, sufijo } = sinNumeroDeRepetido(valor);
  const traducido = nombre
    .split('/')
    .map((parte) => parte.trim())
    .map((parte) => paleta.find((color) => plano(color.nombre) === plano(parte))?.nombreEn ?? parte)
    .join(' / ');
  return traducido + sufijo;
}

/**
 * Aparta el número con que la aprobación distingue dos fotos del mismo color —«Azul oscuro 2»—
 * para poder buscar el nombre en la paleta, que lo guarda sin número.
 *
 * Sin esto, un color numerado no se encontraba y en inglés se leía «Azul oscuro 2» en medio de
 * «Black» y «Red». El número se devuelve detrás de lo traducido —«Dark blue 2»— porque es lo
 * que distingue una variante de la otra y quitarlo las haría indistinguibles también en inglés.
 *
 * Solo cuenta un número al final tras un espacio. Un color de la paleta que acabe en dígito no
 * existe, y si algún día existe, el peor caso es que su nombre se busque sin el dígito y no se
 * encuentre — que es lo que ya pasa hoy con cualquier color que no esté en la paleta.
 */
function sinNumeroDeRepetido(valor: string): { nombre: string; sufijo: string } {
  const partido = /^(.*\S)\s+(\d+)$/.exec(valor);
  return partido ? { nombre: partido[1], sufijo: ' ' + partido[2] } : { nombre: valor, sufijo: '' };
}

/** El nombre del eje de color del producto, si lo tiene: «Color». */
export function ejeDeColor(producto: Producto): string | null {
  for (const variante of producto.variantes) {
    const color = variante.atributos.find((valor) => valor.colorHex !== null);
    if (color) {
      return color.nombre;
    }
  }
  return null;
}
