// Lo que hay en `catalogo/` para cada producto de la lista del proveedor, y si alcanza para
// publicarlo. Vive aparte porque lo usan dos scripts —el cruce que informa y el cargador que
// escribe—, y dos definiciones de "publicable" que puedan divergir es exactamente el defecto
// que este proyecto se cansó de encontrar en otras partes.

import { readFileSync, readdirSync, existsSync, openSync, readSync, closeSync } from "node:fs";
import { join } from "node:path";

export const LADO_MINIMO = 1200;

export const RAIZ = new URL("..", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
export const CATALOGO = join(RAIZ, "catalogo");
const ESTUDIO = join(CATALOGO, "fotos", "estudio");

/**
 * El ancho y el alto de un JPEG, leyendo su cabecera. Son ~25 líneas y evita una dependencia de
 * imágenes para responder una sola pregunta; la regla dura #9 de CLAUDE.md pesa más que la
 * comodidad de un `import`.
 */
export function dimensionesJpeg(ruta) {
  const fd = openSync(ruta, "r");
  try {
    const cabecera = Buffer.alloc(2);
    readSync(fd, cabecera, 0, 2, 0);
    if (cabecera[0] !== 0xff || cabecera[1] !== 0xd8) return null; // no es JPEG
    let posicion = 2;
    const marcador = Buffer.alloc(4);
    for (;;) {
      if (readSync(fd, marcador, 0, 4, posicion) < 4) return null;
      if (marcador[0] !== 0xff) return null;
      const tipo = marcador[1];
      const largo = marcador.readUInt16BE(2);
      // SOF0..SOF15 llevan el tamaño; 0xc4 (DHT), 0xc8 (JPG) y 0xcc (DAC) caen en ese rango y no
      // son marcos de imagen.
      if (tipo >= 0xc0 && tipo <= 0xcf && tipo !== 0xc4 && tipo !== 0xc8 && tipo !== 0xcc) {
        const marco = Buffer.alloc(5);
        readSync(fd, marco, 0, 5, posicion + 4);
        return { alto: marco.readUInt16BE(1), ancho: marco.readUInt16BE(3) };
      }
      posicion += 2 + largo;
    }
  } finally {
    closeSync(fd);
  }
}

export function leerJson(ruta) {
  return existsSync(ruta) ? JSON.parse(readFileSync(ruta, "utf8")) : null;
}

function fotos(id) {
  const carpeta = join(ESTUDIO, id, "maestra");
  if (!existsSync(carpeta)) return { archivos: [], ladoMenor: 0 };
  const archivos = readdirSync(carpeta)
    .filter((f) => f.toLowerCase().endsWith(".jpg"))
    .sort()
    .map((f) => ({ ruta: join(carpeta, f), ...dimensionesJpeg(join(carpeta, f)) }))
    .filter((f) => f.ancho);
  const lados = archivos.map((f) => Math.min(f.ancho, f.alto));
  return { archivos, ladoMenor: lados.length ? Math.min(...lados) : 0 };
}

/**
 * Las cuatro cifras del empaque salen de la ficha de Icecat cuando el fabricante las publica. Los
 * de celulares no publican nada del empaque: ahí toca báscula, y eso no lo resuelve ningún script.
 *
 * <p>Se devuelven en gramos y centímetros enteros redondeados hacia arriba, que es lo que el
 * dominio exige, y solo si están las cuatro: tres medidas y un peso ausente no es "a medio medir",
 * es una carga rota (`adr/0021`, `adr/0046`).
 */
function empaque(id) {
  const ficha = leerJson(join(CATALOGO, "icecat", "fichas", `${id}.json`));
  if (!ficha) return null;
  const especificaciones = (ficha.especificaciones ?? []).flatMap((grupo) =>
    Array.isArray(grupo.valores) ? grupo.valores : Array.isArray(grupo) ? grupo : [grupo],
  );
  const texto = (busca) =>
    especificaciones.find((e) => (e?.atributo ?? "").toLowerCase().includes(busca))?.valor ?? null;

  const aMilimetros = (valor) => {
    if (!valor) return null;
    const numero = Number.parseFloat(String(valor).replace(",", "."));
    if (Number.isNaN(numero)) return null;
    if (/\bcm\b/i.test(valor)) return numero * 10;
    if (/\bm\b/i.test(valor) && !/\bmm\b/i.test(valor)) return numero * 1000;
    return numero; // mm
  };
  const aGramos = (valor) => {
    if (!valor) return null;
    const numero = Number.parseFloat(String(valor).replace(",", "."));
    if (Number.isNaN(numero)) return null;
    return /\bkg\b/i.test(valor) ? numero * 1000 : numero;
  };

  const anchoMm = aMilimetros(texto("ancho del paquete"));
  const largoMm = aMilimetros(texto("profundidad del paquete"));
  const altoMm = aMilimetros(texto("altura del paquete"));
  const pesoG = aGramos(texto("peso del paquete"));
  if (!anchoMm || !largoMm || !altoMm || !pesoG) return null;

  return {
    pesoGramos: Math.ceil(pesoG),
    largoCm: Math.ceil(largoMm / 10),
    anchoCm: Math.ceil(anchoMm / 10),
    altoCm: Math.ceil(altoMm / 10),
  };
}

/**
 * Cada producto de la lista con su material y sus faltas. Las faltas son solo las que impiden
 * publicar: foto utilizable, prosa y precio. **Las medidas no son una falta** — sin ellas se
 * vende con recogida en el punto (`adr/0046`).
 */
export function leerMaterial() {
  const catalogo = leerJson(join(CATALOGO, "productos.json"));
  const prosa = leerJson(join(CATALOGO, "prosa.json"));
  if (!catalogo || !prosa) {
    throw new Error(
      "No encuentro catalogo/productos.json o catalogo/prosa.json. Esa carpeta no está versionada:\n" +
        "es material de trabajo y vive solo en la máquina donde se procesó la lista del proveedor.",
    );
  }

  const productos = catalogo.productos.map((producto) => {
    const foto = fotos(producto.id);
    const faltas = [];
    if (foto.archivos.length === 0) faltas.push("sin foto");
    else if (foto.ladoMenor < LADO_MINIMO) faltas.push(`foto ${foto.ladoMenor}px`);
    if (!prosa[producto.id]?.apertura) faltas.push("sin prosa");
    if (!producto.precio_mercado_cop) faltas.push("sin precio");
    return { ...producto, foto, empaque: empaque(producto.id), prosa: prosa[producto.id], faltas };
  });

  return { fecha: catalogo.fecha_lista, productos };
}
