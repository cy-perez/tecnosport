/**
 * La descripción de un producto llega como texto con la estructura que escribe la ingesta de
 * tecnología (`listas-de-proveedor`, `referencias/descripciones.md`): un párrafo de apertura,
 * subtítulos `## `, viñetas `- `, la ficha técnica en una tabla de dos columnas y, tras una línea
 * `---`, las fuentes de los datos.
 *
 * La ficha la pintaba en un solo `<p>`, y el navegador junta los saltos de línea: el cliente leía
 * «## Ficha técnica | Atributo | Detalle |---|---|» en un bloque corrido. Esto lo convierte en
 * bloques que el componente pinta con Angular, sin `innerHTML` y sin una librería de Markdown:
 * reconoce solo lo que la ingesta escribe, y todo lo demás es párrafo. Una descripción escrita a
 * mano en el panel, sin nada de eso, se sigue viendo como se escribió.
 */

/** Un trozo de texto; con `enlace`, se pinta como enlace a esa dirección. */
export interface Segmento {
  readonly texto: string;
  readonly enlace?: string;
}

export type BloqueDeDescripcion =
  | { readonly tipo: 'parrafo'; readonly lineas: readonly (readonly Segmento[])[] }
  | { readonly tipo: 'subtitulo'; readonly texto: string }
  | { readonly tipo: 'lista'; readonly elementos: readonly (readonly Segmento[])[] }
  | {
      readonly tipo: 'tabla';
      /** La fila de encabezado («Atributo | Detalle»), o `null` si la tabla no la trae. */
      readonly encabezados: readonly string[] | null;
      readonly filas: readonly (readonly (readonly Segmento[])[])[];
    }
  | { readonly tipo: 'fuentes'; readonly lineas: readonly (readonly Segmento[])[] };

const RE_SUBTITULO = /^#{1,6}\s+(.+?)\s*#*$/;
const RE_VINETA = /^[-*]\s+(.*)$/;
const RE_FILA = /^\|.*\|$/;
const RE_SEPARADOR_DE_TABLA = /^\|(\s*:?-+:?\s*\|)+$/;
const RE_LINEA_HORIZONTAL = /^(-{3,}|\*{3,}|_{3,})$/;
// La dirección termina en el primer espacio o paréntesis de cierre: el pie de Icecat la escribe
// entre paréntesis, «(https://icecat.biz)». Un punto o una coma al final son de la frase.
const RE_URL = /https?:\/\/[^\s)]+/g;
const PUNTUACION_FINAL = /[.,;:]+$/;

/** El texto partido en trozos, con las direcciones `http(s)` como enlaces. */
export function segmentar(texto: string): Segmento[] {
  const segmentos: Segmento[] = [];
  let desde = 0;
  for (const m of texto.matchAll(RE_URL)) {
    const url = m[0].replace(PUNTUACION_FINAL, '');
    const inicio = m.index;
    if (inicio > desde) segmentos.push({ texto: texto.slice(desde, inicio) });
    segmentos.push({ texto: url, enlace: url });
    desde = inicio + url.length;
  }
  if (desde < texto.length) segmentos.push({ texto: texto.slice(desde) });
  return segmentos;
}

/**
 * Las celdas de una fila `| a | b |`. Una barra escapada (`\|`) es del texto, no un borde.
 *
 * Con `columnas`, lo que sobra se junta en la última celda: Icecat escribe «GPS | Galileo |
 * Glonass» dentro del valor de Conectividad, y partido así el reloj publicaba cinco columnas.
 */
function celdas(fila: string, columnas?: number): string[] {
  const partes = fila
    .slice(1, -1)
    .split(/(?<!\\)\|/)
    .map((c) => c.trim().replaceAll('\\|', '|'));
  if (columnas && columnas >= 2 && partes.length > columnas) {
    return [...partes.slice(0, columnas - 1), partes.slice(columnas - 1).join(' | ')];
  }
  return partes;
}

function leerTabla(lineas: readonly string[]): BloqueDeDescripcion {
  const separador = lineas.findIndex((l) => RE_SEPARADOR_DE_TABLA.test(l));
  // El encabezado es la fila de antes del separador; sin separador, todas son datos.
  const encabezados = separador === 1 ? celdas(lineas[0]) : null;
  const columnas = encabezados?.length;
  const datos = lineas.filter((l, i) => i !== separador && !(separador === 1 && i === 0));
  return {
    tipo: 'tabla',
    encabezados,
    filas: datos.map((l) => celdas(l, columnas).map(segmentar)),
  };
}

/** Los bloques de una descripción, en el orden en que se escribieron. */
export function leerDescripcion(texto: string | null | undefined): BloqueDeDescripcion[] {
  if (!texto?.trim()) return [];
  const lineas = texto.split(/\r?\n/).map((l) => l.trim());
  const bloques: BloqueDeDescripcion[] = [];
  let i = 0;

  while (i < lineas.length) {
    const linea = lineas[i];

    if (!linea) {
      i++;
      continue;
    }

    // Todo lo que sigue a la línea horizontal es el pie de fuentes, línea por línea.
    if (RE_LINEA_HORIZONTAL.test(linea)) {
      const pie = lineas.slice(i + 1).filter(Boolean);
      if (pie.length > 0) bloques.push({ tipo: 'fuentes', lineas: pie.map(segmentar) });
      break;
    }

    const subtitulo = RE_SUBTITULO.exec(linea);
    if (subtitulo) {
      bloques.push({ tipo: 'subtitulo', texto: subtitulo[1] });
      i++;
      continue;
    }

    if (RE_FILA.test(linea) && linea.length > 1) {
      const filas: string[] = [];
      while (i < lineas.length && RE_FILA.test(lineas[i]) && lineas[i].length > 1) {
        filas.push(lineas[i++]);
      }
      bloques.push(leerTabla(filas));
      continue;
    }

    if (RE_VINETA.test(linea)) {
      const elementos: string[] = [];
      while (i < lineas.length && lineas[i]) {
        const vineta = RE_VINETA.exec(lineas[i]);
        if (vineta) elementos.push(vineta[1]);
        else if (esInicioDeBloque(lineas[i])) break;
        // Una línea sin guion dentro de la lista continúa la viñeta anterior.
        else elementos[elementos.length - 1] += ` ${lineas[i]}`;
        i++;
      }
      bloques.push({ tipo: 'lista', elementos: elementos.map(segmentar) });
      continue;
    }

    // El párrafo conserva sus saltos de línea: quien escribe a mano en el panel los pone a
    // propósito, y el componente los respeta.
    const parrafo: string[] = [];
    while (i < lineas.length && lineas[i] && !esInicioDeBloque(lineas[i])) {
      parrafo.push(lineas[i++]);
    }
    bloques.push({ tipo: 'parrafo', lineas: parrafo.map(segmentar) });
  }

  return bloques;
}

function esInicioDeBloque(linea: string): boolean {
  return (
    RE_SUBTITULO.test(linea) ||
    RE_VINETA.test(linea) ||
    RE_LINEA_HORIZONTAL.test(linea) ||
    (RE_FILA.test(linea) && linea.length > 1)
  );
}
