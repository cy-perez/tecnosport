/**
 * Funciones puras del procesamiento local del asistente de captura (`docs/10-captura-360.md`,
 * "Procesamiento local"). Sin DOM, sin `canvas` y sin señales: reciben los píxeles ya leídos y
 * devuelven números.
 *
 * Es la parte que decide si la rotación se ve profesional o casera —el producto que crece y
 * encoge al girar es el defecto más visible de un 360 casero— y es lo que más se rompe, así que
 * va primero y va probada. Quien dibuje en el `<canvas>` usa estos resultados; ninguna decisión
 * de recorte ni de escala se toma en una plantilla.
 *
 * Límite honesto, el mismo que declara el documento: la detección supone fondo claro y uniforme.
 * Con fondo desordenado falla, y falla diciéndolo (`DeteccionDeRecorte.ok === false`) para que el
 * asistente ofrezca recorte manual en vez de subir un recorte que sabe que salió mal.
 */

/** Un píxel ocupa cuatro posiciones del arreglo: rojo, verde, azul y alfa. */
const CANALES = 4;

/** Lado de la muestra de cada esquina, relativo al lado menor de la imagen. */
const MUESTRA_RELATIVA = 0.05;

/** Diferencia de luminancia (0-255) que se tolera entre las cuatro esquinas. */
const TOLERANCIA_ENTRE_ESQUINAS = 18;

/** Cuánto tiene que separarse un píxel del fondo, en luminancia (0-255), para ser producto. */
const UMBRAL_CONTRA_FONDO = 24;

/**
 * Píxeles de producto que necesita una fila o una columna para contar como ocupada. Con uno solo,
 * una mota de polvo o un píxel quemado del sensor estiraría el rectángulo hasta el borde. El
 * precio es ignorar un detalle más fino que dos píxeles, que a 1000 px de lado no es nada.
 */
const MINIMO_PIXELES_POR_LINEA = 2;

/** Lado de la imagen final, en píxeles (`docs/10-captura-360.md`: 1000 x 1000). */
export const LADO_SALIDA_PX = 1000;

/**
 * Aire alrededor del producto, relativo a su lado mayor y aplicado a los dos lados
 * (`docs/10-captura-360.md`: 8%). Es el mismo para todo el set, como la escala: un margen que
 * cambia entre fotogramas se ve exactamente igual que un producto que late al girar.
 */
export const MARGEN_RELATIVO_DEL_SET = 0.08;

/**
 * Los píxeles de un fotograma, en RGBA y fila por fila. El `ImageData` de un `canvas` encaja tal
 * cual, pero el tipo es propio a propósito: este módulo no depende del DOM y se prueba sin él.
 */
export interface DatosImagen {
  readonly ancho: number;
  readonly alto: number;
  readonly datos: Uint8ClampedArray;
}

/** Una región, en píxeles de la imagen original. */
export interface Rectangulo {
  readonly x: number;
  readonly y: number;
  readonly ancho: number;
  readonly alto: number;
}

export interface ColorRgb {
  readonly r: number;
  readonly g: number;
  readonly b: number;
}

export type MotivoDeteccionFallida =
  /** Ancho, alto o cantidad de datos que no describen una imagen. */
  | 'IMAGEN_INVALIDA'
  /** Las esquinas no se parecen entre sí: no hay un fondo que estimar. */
  | 'FONDO_NO_UNIFORME'
  /** Nada se separa del fondo lo suficiente. */
  | 'SIN_PRODUCTO'
  /** El producto toca el marco: no se sabe si está completo, y no hay dónde poner el margen. */
  | 'PRODUCTO_CORTADO';

export type DeteccionDeRecorte =
  | { readonly ok: true; readonly rectangulo: Rectangulo; readonly fondo: ColorRgb }
  | { readonly ok: false; readonly motivo: MotivoDeteccionFallida };

export interface OpcionesDeDeteccion {
  /** Lado del cuadro que se muestrea en cada esquina. Por omisión, 5% del lado menor. */
  readonly muestraPx?: number;
  readonly toleranciaEntreEsquinas?: number;
  readonly umbralContraFondo?: number;
  readonly minimoPixelesPorLinea?: number;
}

/**
 * Luminancia relativa de Rec. 709, sobre los valores del canal sin corregir gamma. Aquí solo hace
 * falta comparar un píxel contra el fondo, no medir contraste como en WCAG.
 */
export function luminancia(r: number, g: number, b: number): number {
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/**
 * Estima el color del fondo con las cuatro esquinas. Devuelve `null` cuando las esquinas no se
 * parecen entre sí: un degradado, una sombra dura o el producto invadiendo una esquina significan
 * que no hay un fondo uniforme del que fiarse.
 */
export function colorDeFondo(
  imagen: DatosImagen,
  opciones: OpcionesDeDeteccion = {},
): ColorRgb | null {
  if (!esImagenValida(imagen)) {
    return null;
  }

  const ladoMenor = Math.min(imagen.ancho, imagen.alto);
  const pedido = opciones.muestraPx ?? ladoMenor * MUESTRA_RELATIVA;
  // Las cuatro muestras no pueden solaparse: en una imagen diminuta, media imagen por esquina.
  const muestra = Math.max(1, Math.min(Math.round(pedido), Math.floor(ladoMenor / 2)));

  const esquinas = [
    promedioDeRegion(imagen, 0, 0, muestra),
    promedioDeRegion(imagen, imagen.ancho - muestra, 0, muestra),
    promedioDeRegion(imagen, 0, imagen.alto - muestra, muestra),
    promedioDeRegion(imagen, imagen.ancho - muestra, imagen.alto - muestra, muestra),
  ];

  const luminancias = esquinas.map((color) => luminancia(color.r, color.g, color.b));
  const tolerancia = opciones.toleranciaEntreEsquinas ?? TOLERANCIA_ENTRE_ESQUINAS;
  if (Math.max(...luminancias) - Math.min(...luminancias) > tolerancia) {
    return null;
  }

  return {
    r: promedio(esquinas.map((color) => color.r)),
    g: promedio(esquinas.map((color) => color.g)),
    b: promedio(esquinas.map((color) => color.b)),
  };
}

/**
 * El rectángulo que contiene al producto: se estima el fondo con las esquinas, se binariza por
 * umbral de luminancia contra ese fondo, y se toma la envolvente de lo que quedó.
 *
 * Un producto que toca el marco se rechaza en vez de recortarse: puede estar cortado, y sobre
 * todo no queda espacio para el margen que el resto del set sí va a tener.
 */
export function detectarRectanguloDelProducto(
  imagen: DatosImagen,
  opciones: OpcionesDeDeteccion = {},
): DeteccionDeRecorte {
  if (!esImagenValida(imagen)) {
    return { ok: false, motivo: 'IMAGEN_INVALIDA' };
  }

  const fondo = colorDeFondo(imagen, opciones);
  if (fondo === null) {
    return { ok: false, motivo: 'FONDO_NO_UNIFORME' };
  }

  const luzDelFondo = luminancia(fondo.r, fondo.g, fondo.b);
  const umbral = opciones.umbralContraFondo ?? UMBRAL_CONTRA_FONDO;
  const porFila = new Uint32Array(imagen.alto);
  const porColumna = new Uint32Array(imagen.ancho);

  for (let y = 0; y < imagen.alto; y++) {
    for (let x = 0; x < imagen.ancho; x++) {
      const i = (y * imagen.ancho + x) * CANALES;
      const luz = luminancia(imagen.datos[i], imagen.datos[i + 1], imagen.datos[i + 2]);
      if (Math.abs(luz - luzDelFondo) > umbral) {
        porFila[y]++;
        porColumna[x]++;
      }
    }
  }

  const minimo = opciones.minimoPixelesPorLinea ?? MINIMO_PIXELES_POR_LINEA;
  const filas = rangoOcupado(porFila, minimo);
  const columnas = rangoOcupado(porColumna, minimo);
  if (filas === null || columnas === null) {
    return { ok: false, motivo: 'SIN_PRODUCTO' };
  }

  if (
    columnas.inicio === 0 ||
    filas.inicio === 0 ||
    columnas.fin === imagen.ancho - 1 ||
    filas.fin === imagen.alto - 1
  ) {
    return { ok: false, motivo: 'PRODUCTO_CORTADO' };
  }

  return {
    ok: true,
    fondo,
    rectangulo: {
      x: columnas.inicio,
      y: filas.inicio,
      ancho: columnas.fin - columnas.inicio + 1,
      alto: filas.fin - filas.inicio + 1,
    },
  };
}

/** El encuadre común del set: un solo lado de recorte y una sola escala para los N fotogramas. */
export interface EncuadreDelSet {
  /** Lado del cuadrado que se recorta de cada fotograma original. El mismo para todo el set. */
  readonly ladoFuentePx: number;
  /** `ladoSalidaPx / ladoFuentePx`. El mismo para todo el set, que es justamente el punto. */
  readonly escala: number;
}

export interface OpcionesDeEncuadre {
  /**
   * Margen alrededor del producto, relativo a su lado mayor y aplicado a los dos lados. Por
   * omisión, `MARGEN_RELATIVO_DEL_SET`; se recibe por parámetro para poder probar la aritmética
   * con números redondos, no para que cada pantalla elija el suyo.
   */
  readonly margenRelativo?: number;
  readonly ladoSalidaPx?: number;
}

/**
 * El factor de escala se calcula **una sola vez para todo el set**. Si cada fotograma se escalara
 * por su cuenta, el producto crecería y encogería al girar.
 *
 * La referencia es el **lado mayor de todos los rectángulos, en las dos dimensiones**, y no "el
 * fotograma más ancho", que era la letra de `docs/10-captura-360.md` hasta que se construyó esto:
 * un fotograma más alto que el ancho del más ancho —el mismo tenis de perfil frente al tenis de
 * frente— se saldría del cuadro y quedaría cortado. El documento ya dice lo que hace este código.
 *
 * Devuelve `null` si no hay fotogramas o si alguno trae un rectángulo degenerado: un set a medias
 * no tiene escala común, y adivinarla es exactamente lo que este módulo existe para evitar.
 */
export function encuadreDelSet(
  rectangulos: readonly Rectangulo[],
  opciones: OpcionesDeEncuadre = {},
): EncuadreDelSet | null {
  const ladoSalidaPx = opciones.ladoSalidaPx ?? LADO_SALIDA_PX;
  const margenRelativo = opciones.margenRelativo ?? MARGEN_RELATIVO_DEL_SET;
  if (rectangulos.length === 0 || !(margenRelativo >= 0) || !(ladoSalidaPx > 0)) {
    return null;
  }

  let ladoMayor = 0;
  for (const rectangulo of rectangulos) {
    if (!(rectangulo.ancho > 0) || !(rectangulo.alto > 0)) {
      return null;
    }
    ladoMayor = Math.max(ladoMayor, rectangulo.ancho, rectangulo.alto);
  }

  const ladoFuentePx = ladoMayor * (1 + 2 * margenRelativo);
  return { ladoFuentePx, escala: ladoSalidaPx / ladoFuentePx };
}

/**
 * Qué región de un fotograma se dibuja y dónde cae dentro del cuadro de salida. Es lo que
 * necesita `drawImage(imagen, origen…, destino…)`, ya resuelto.
 */
export interface RecorteDeFotograma {
  /** Región de la imagen original que se dibuja, recortada a los límites reales del fotograma. */
  readonly origen: Rectangulo;
  /** Dónde va esa región dentro del cuadro de salida. */
  readonly destino: Rectangulo;
}

const REGION_VACIA: Rectangulo = { x: 0, y: 0, ancho: 0, alto: 0 };

/**
 * Lleva el rectángulo del producto a 1:1 centrado, con el encuadre común del set.
 *
 * Cuando el cuadrado se sale del fotograma —producto cerca de un borde— el origen se recorta a lo
 * que existe y el destino se desplaza en la misma proporción: el producto se queda donde estaba y
 * el hueco lo llena quien dibuja, con el color de fondo estimado. Estirar la parte que sí existe
 * para llenar el cuadro cambiaría la escala de ese fotograma, que es lo que no puede pasar.
 *
 * No se redondea a píxeles enteros: medio píxel de diferencia entre fotogramas es exactamente la
 * inconsistencia de escala que este módulo evita. `drawImage` interpola.
 */
export function recorteDeFotograma(
  rectangulo: Rectangulo,
  encuadre: EncuadreDelSet,
  imagen: { readonly ancho: number; readonly alto: number },
): RecorteDeFotograma {
  const izquierda = rectangulo.x + rectangulo.ancho / 2 - encuadre.ladoFuentePx / 2;
  const arriba = rectangulo.y + rectangulo.alto / 2 - encuadre.ladoFuentePx / 2;

  const x0 = Math.max(0, izquierda);
  const y0 = Math.max(0, arriba);
  const x1 = Math.min(imagen.ancho, izquierda + encuadre.ladoFuentePx);
  const y1 = Math.min(imagen.alto, arriba + encuadre.ladoFuentePx);

  if (!(x1 > x0) || !(y1 > y0)) {
    return { origen: REGION_VACIA, destino: REGION_VACIA };
  }

  return {
    origen: { x: x0, y: y0, ancho: x1 - x0, alto: y1 - y0 },
    destino: {
      x: (x0 - izquierda) * encuadre.escala,
      y: (y0 - arriba) * encuadre.escala,
      ancho: (x1 - x0) * encuadre.escala,
      alto: (y1 - y0) * encuadre.escala,
    },
  };
}

function esImagenValida(imagen: DatosImagen): boolean {
  return (
    Number.isInteger(imagen.ancho) &&
    Number.isInteger(imagen.alto) &&
    imagen.ancho > 0 &&
    imagen.alto > 0 &&
    imagen.datos.length >= imagen.ancho * imagen.alto * CANALES
  );
}

function promedioDeRegion(imagen: DatosImagen, x0: number, y0: number, lado: number): ColorRgb {
  let r = 0;
  let g = 0;
  let b = 0;
  let contados = 0;

  for (let y = y0; y < y0 + lado; y++) {
    for (let x = x0; x < x0 + lado; x++) {
      const i = (y * imagen.ancho + x) * CANALES;
      r += imagen.datos[i];
      g += imagen.datos[i + 1];
      b += imagen.datos[i + 2];
      contados++;
    }
  }

  return { r: r / contados, g: g / contados, b: b / contados };
}

function promedio(valores: readonly number[]): number {
  return valores.reduce((suma, valor) => suma + valor, 0) / valores.length;
}

function rangoOcupado(
  conteos: Uint32Array,
  minimo: number,
): { readonly inicio: number; readonly fin: number } | null {
  let inicio = -1;
  let fin = -1;

  for (let i = 0; i < conteos.length; i++) {
    if (conteos[i] >= minimo) {
      if (inicio === -1) {
        inicio = i;
      }
      fin = i;
    }
  }

  return inicio === -1 ? null : { inicio, fin };
}
